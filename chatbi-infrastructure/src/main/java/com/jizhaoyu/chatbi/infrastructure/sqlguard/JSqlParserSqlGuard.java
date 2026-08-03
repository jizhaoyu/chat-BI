package com.jizhaoyu.chatbi.infrastructure.sqlguard;

import com.jizhaoyu.chatbi.application.sqlguard.SqlGuardContext;
import com.jizhaoyu.chatbi.application.sqlguard.SqlGuardPort;
import com.jizhaoyu.chatbi.application.sqlguard.SqlGuardResult;
import com.jizhaoyu.chatbi.application.sqlguard.SqlObjectReference;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.expression.VariableAssignment;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.SupportsOldOracleJoinSyntax;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class JSqlParserSqlGuard implements SqlGuardPort {
    private static final int MAX_SQL_LENGTH = 20_000;
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "DATE", "YEAR", "MONTH", "DAY",
            "COALESCE", "IFNULL", "ROUND");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");

    @Override
    public SqlGuardResult validate(String candidateSql, SqlGuardContext context) {
        String sql = validateLexically(candidateSql);
        Statement statement = parseSingle(sql);
        if (!(statement instanceof Select select) || !(select instanceof PlainSelect plain)) {
            throw new IllegalArgumentException(statement instanceof Select
                    ? "SQL_FEATURE_FORBIDDEN" : "SQL_STATEMENT_FORBIDDEN");
        }
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            throw new IllegalArgumentException("SQL_FEATURE_FORBIDDEN");
        }
        QueryScope scope = resolveScope(plain, context);
        validateSelectShape(plain);
        ReferenceCollector collector = new ReferenceCollector(scope);
        Map<String, Expression> projectionAliases = new HashMap<>();
        for (SelectItem<?> item : plain.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns || item.getExpression() instanceof AllTableColumns) {
                throw new IllegalArgumentException("SQL_WILDCARD_FORBIDDEN");
            }
            collector.collect(item.getExpression());
            if (item.getAlias() != null
                    && projectionAliases.putIfAbsent(normalize(item.getAlias().getName()), item.getExpression()) != null) {
                throw new IllegalArgumentException("SQL_IDENTIFIER_AMBIGUOUS");
            }
        }
        accept(plain.getWhere(), collector);
        accept(plain.getHaving(), collector);
        if (plain.getGroupBy() != null) {
            for (Object expression : plain.getGroupBy().getGroupByExpressionList()) {
                if (!(expression instanceof Expression groupExpression)) {
                    throw new IllegalArgumentException("SQL_FEATURE_FORBIDDEN");
                }
                accept(groupExpression, collector);
            }
        }
        if (plain.getOrderByElements() != null) {
            plain.getOrderByElements().forEach(order -> {
                if (order.getExpression() instanceof Column column && column.getTable() == null
                        && projectionAliases.containsKey(normalize(column.getColumnName()))) {
                    return;
                }
                accept(order.getExpression(), collector);
            });
        }
        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                join.getOnExpressions().forEach(expression -> accept(expression, collector));
            }
        }
        int effectiveLimit = tightenLimit(plain, context.maximumRows());
        return new SqlGuardResult(plain.toString(), effectiveLimit, collector.references());
    }

    private static String validateLexically(String candidateSql) {
        if (candidateSql == null || candidateSql.isBlank() || candidateSql.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("SQL_PARSE_FAILED");
        }
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean backtickQuoted = false;
        boolean terminalSemicolon = false;
        for (int index = 0; index < candidateSql.length(); index++) {
            char current = candidateSql.charAt(index);
            char next = index + 1 < candidateSql.length() ? candidateSql.charAt(index + 1) : '\0';
            if (!singleQuoted && !doubleQuoted && !backtickQuoted) {
                if (Character.isISOControl(current) && current != '\r' && current != '\n' && current != '\t') {
                    throw new IllegalArgumentException("SQL_PARSE_FAILED");
                }
                if ((current == '-' && next == '-') || current == '#' || (current == '/' && next == '*')) {
                    throw new IllegalArgumentException("SQL_COMMENT_FORBIDDEN");
                }
                if (current == ';') {
                    if (!candidateSql.substring(index + 1).isBlank()) {
                        throw new IllegalArgumentException("SQL_MULTIPLE_STATEMENTS");
                    }
                    terminalSemicolon = true;
                    continue;
                }
                if (terminalSemicolon && !Character.isWhitespace(current)) {
                    throw new IllegalArgumentException("SQL_MULTIPLE_STATEMENTS");
                }
            }
            if (current == '\'' && !doubleQuoted && !backtickQuoted
                    && (index == 0 || candidateSql.charAt(index - 1) != '\\')) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted && !backtickQuoted
                    && (index == 0 || candidateSql.charAt(index - 1) != '\\')) {
                doubleQuoted = !doubleQuoted;
            } else if (current == '`' && !singleQuoted && !doubleQuoted) {
                backtickQuoted = !backtickQuoted;
            }
        }
        if (singleQuoted || doubleQuoted || backtickQuoted) {
            throw new IllegalArgumentException("SQL_PARSE_FAILED");
        }
        return candidateSql.trim();
    }

    private static Statement parseSingle(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                throw new IllegalArgumentException("SQL_MULTIPLE_STATEMENTS");
            }
            return statements.get(0);
        } catch (JSQLParserException failure) {
            throw new IllegalArgumentException("SQL_PARSE_FAILED");
        }
    }

    private static QueryScope resolveScope(PlainSelect plain, SqlGuardContext context) {
        if (!(plain.getFromItem() instanceof Table from)) {
            throw new IllegalArgumentException("SQL_FEATURE_FORBIDDEN");
        }
        Map<String, CatalogTable> aliases = new LinkedHashMap<>();
        addTable(from, context, aliases);
        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                if (!(join.isInner() || join.isLeft()) || join.isSimple() || join.getOnExpressions().isEmpty()
                        || !(join.getRightItem() instanceof Table table)) {
                    throw new IllegalArgumentException("SQL_FEATURE_FORBIDDEN");
                }
                addTable(table, context, aliases);
            }
        }
        return new QueryScope(aliases);
    }

    private static void addTable(
            Table sqlTable, SqlGuardContext context, Map<String, CatalogTable> aliases) {
        String schema = normalize(sqlTable.getSchemaName());
        if (schema != null && SYSTEM_SCHEMAS.contains(schema)) {
            throw new SecurityException("SQL_OBJECT_FORBIDDEN");
        }
        List<CatalogTable> matches = context.authorizedCatalog().tables().stream()
                .filter(table -> normalize(table.name()).equals(normalize(sqlTable.getName())))
                .filter(table -> schema == null || normalize(table.schemaName()).equals(schema))
                .toList();
        if (matches.size() != 1) {
            throw new SecurityException("SQL_OBJECT_FORBIDDEN");
        }
        CatalogTable table = matches.getFirst();
        String alias = sqlTable.getAlias() == null ? sqlTable.getName() : sqlTable.getAlias().getName();
        if (aliases.putIfAbsent(normalize(alias), table) != null) {
            throw new IllegalArgumentException("SQL_IDENTIFIER_AMBIGUOUS");
        }
        aliases.putIfAbsent(normalize(table.name()), table);
    }

    private static void validateSelectShape(PlainSelect plain) {
        if (plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()
                || plain.getIntoTempTable() != null || plain.getOffset() != null || plain.getFetch() != null
                || plain.getTop() != null || plain.getOracleHint() != null || plain.getQualify() != null
                || plain.getWindowDefinitions() != null && !plain.getWindowDefinitions().isEmpty()
                || plain.getForClause() != null || plain.getForMode() != null || plain.getWait() != null
                || plain.getForUpdateTable() != null || plain.isSkipLocked()
                || plain.getLateralViews() != null && !plain.getLateralViews().isEmpty()) {
            throw new IllegalArgumentException("SQL_FEATURE_FORBIDDEN");
        }
    }

    private static int tightenLimit(PlainSelect plain, int maximumRows) {
        Limit limit = plain.getLimit();
        if (limit == null) {
            plain.setLimit(new Limit().withRowCount(new net.sf.jsqlparser.expression.LongValue(maximumRows)));
            return maximumRows;
        }
        if (limit.getOffset() != null || !(limit.getRowCount() instanceof net.sf.jsqlparser.expression.LongValue value)
                || value.getValue() < 0) {
            throw new IllegalArgumentException("SQL_LIMIT_INVALID");
        }
        int effective = (int) Math.min(value.getValue(), maximumRows);
        limit.setRowCount(new net.sf.jsqlparser.expression.LongValue(effective));
        return effective;
    }

    private static void accept(Expression expression, ReferenceCollector collector) {
        if (expression != null) {
            collector.collect(expression);
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static final class QueryScope {
        private final Map<String, CatalogTable> aliases;

        private QueryScope(Map<String, CatalogTable> aliases) {
            this.aliases = Map.copyOf(aliases);
        }

        SqlObjectReference resolve(Column sqlColumn) {
            String qualifier = sqlColumn.getTable() == null ? null : normalize(sqlColumn.getTable().getName());
            String name = normalize(sqlColumn.getColumnName());
            List<Map.Entry<CatalogTable, CatalogColumn>> matches = new ArrayList<>();
            Set<CatalogTable> distinctTables = new HashSet<>(aliases.values());
            for (CatalogTable table : distinctTables) {
                if (qualifier != null && aliases.get(qualifier) != table) {
                    continue;
                }
                table.columns().stream().filter(column -> normalize(column.name()).equals(name))
                        .forEach(column -> matches.add(Map.entry(table, column)));
            }
            if (matches.size() != 1) {
                throw new SecurityException(matches.isEmpty()
                        ? "SQL_COLUMN_FORBIDDEN" : "SQL_IDENTIFIER_AMBIGUOUS");
            }
            Map.Entry<CatalogTable, CatalogColumn> match = matches.getFirst();
            return new SqlObjectReference(match.getKey().id(), match.getValue().id(),
                    match.getKey().schemaName(), match.getKey().name(), match.getValue().name());
        }
    }

    private static final class ReferenceCollector {
        private final QueryScope scope;
        private final Map<String, SqlObjectReference> references = new LinkedHashMap<>();

        private ReferenceCollector(QueryScope scope) {
            this.scope = scope;
        }

        private void collect(Expression expression) {
            if (expression instanceof Column column) {
                collectColumn(column);
                return;
            }
            if (expression instanceof LongValue || expression instanceof DoubleValue
                    || expression instanceof StringValue || expression instanceof DateValue
                    || expression instanceof TimeValue || expression instanceof TimestampValue
                    || expression instanceof BooleanValue || expression instanceof NullValue) {
                return;
            }
            if (expression instanceof Function function) {
                collectFunction(function);
                return;
            }
            if (expression instanceof SignedExpression signed) {
                if (signed.getSign() != '+' && signed.getSign() != '-') {
                    throw forbidden("SQL_FEATURE_FORBIDDEN");
                }
                collect(signed.getExpression());
                return;
            }
            if (expression instanceof NotExpression not) {
                collect(not.getExpression());
                return;
            }
            if (expression instanceof Between between) {
                collect(between.getLeftExpression());
                collect(between.getBetweenExpressionStart());
                collect(between.getBetweenExpressionEnd());
                return;
            }
            if (expression instanceof InExpression in) {
                rejectOracleSyntax(in);
                if (in.isGlobal()) {
                    throw forbidden("SQL_FEATURE_FORBIDDEN");
                }
                collect(in.getLeftExpression());
                collect(in.getRightExpression());
                return;
            }
            if (expression instanceof IsNullExpression isNull) {
                collect(isNull.getLeftExpression());
                return;
            }
            if (expression instanceof ExpressionList<?> expressions) {
                expressions.forEach(item -> {
                    if (!(item instanceof Expression child)) {
                        throw forbidden("SQL_FEATURE_FORBIDDEN");
                    }
                    collect(child);
                });
                return;
            }
            if (expression instanceof BinaryExpression binary && isAllowedBinary(binary)) {
                if (binary instanceof SupportsOldOracleJoinSyntax oracleSyntax) {
                    rejectOracleSyntax(oracleSyntax);
                }
                if (binary instanceof LikeExpression like) {
                    if (like.isCaseInsensitive() || like.isUseBinary()) {
                        throw forbidden("SQL_FEATURE_FORBIDDEN");
                    }
                    if (like.getEscape() != null) {
                        collect(like.getEscape());
                    }
                }
                collect(binary.getLeftExpression());
                collect(binary.getRightExpression());
                return;
            }
            if (expression instanceof UserVariable || expression instanceof VariableAssignment) {
                throw forbidden("SQL_VARIABLE_FORBIDDEN");
            }
            if (expression instanceof JdbcParameter || expression instanceof JdbcNamedParameter) {
                throw forbidden("SQL_PARAMETER_INVALID");
            }
            throw forbidden("SQL_FEATURE_FORBIDDEN");
        }

        private void collectColumn(Column column) {
            SqlObjectReference reference = scope.resolve(column);
            references.putIfAbsent(reference.tableId() + ":" + reference.columnId(), reference);
        }

        private void collectFunction(Function function) {
            String name = function.getName() == null ? "" : function.getName().toUpperCase(Locale.ROOT);
            if (!ALLOWED_FUNCTIONS.contains(name) || function.getKeep() != null
                    || function.getAttribute() != null
                    || function.getOrderByElements() != null && !function.getOrderByElements().isEmpty()
                    || function.getNamedParameters() != null && !function.getNamedParameters().isEmpty()
                    || function.getLimit() != null || function.getHavingClause() != null
                    || function.getExtraKeyword() != null || function.getOnOverflowTruncate() != null
                    || function.getNullHandling() != null || function.isIgnoreNulls()
                    || function.isIgnoreNullsOutside() || function.isEscaped() || function.isUnique()
                    || function.getMultipartName().size() != 1) {
                throw new IllegalArgumentException("SQL_FUNCTION_FORBIDDEN");
            }
            if (function.isAllColumns() && !"COUNT".equals(name)) {
                throw new IllegalArgumentException("SQL_WILDCARD_FORBIDDEN");
            }
            if (function.getParameters() != null) {
                collect(function.getParameters());
            }
        }

        private static boolean isAllowedBinary(BinaryExpression expression) {
            return expression instanceof Addition || expression instanceof Subtraction
                    || expression instanceof Multiplication || expression instanceof Division
                    || expression instanceof AndExpression || expression instanceof OrExpression
                    || expression instanceof EqualsTo || expression instanceof NotEqualsTo
                    || expression instanceof GreaterThan || expression instanceof GreaterThanEquals
                    || expression instanceof MinorThan || expression instanceof MinorThanEquals
                    || expression instanceof LikeExpression;
        }

        @SuppressWarnings("deprecation")
        private static void rejectOracleSyntax(SupportsOldOracleJoinSyntax expression) {
            if (expression.getOldOracleJoinSyntax() != SupportsOldOracleJoinSyntax.NO_ORACLE_JOIN
                    || expression.getOraclePriorPosition() != SupportsOldOracleJoinSyntax.NO_ORACLE_PRIOR) {
                throw forbidden("SQL_FEATURE_FORBIDDEN");
            }
        }

        List<SqlObjectReference> references() {
            return List.copyOf(references.values());
        }

        private static IllegalArgumentException forbidden(String code) {
            return new IllegalArgumentException(code);
        }
    }
}
