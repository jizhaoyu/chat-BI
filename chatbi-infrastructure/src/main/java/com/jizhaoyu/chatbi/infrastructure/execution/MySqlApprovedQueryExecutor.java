package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.datasource.CredentialVaultPort;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionSpec;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourcePool;
import com.jizhaoyu.chatbi.application.execution.ApprovedQueryExecutor;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionFailure;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionResult;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionStatus;
import com.jizhaoyu.chatbi.application.execution.QueryResultColumn;
import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public final class MySqlApprovedQueryExecutor implements ApprovedQueryExecutor {
    private static final int EXECUTION_POOL_SIZE = 3;
    private static final int FETCH_SIZE = 500;
    private final DataSourceRepository sources;
    private final CredentialVaultPort credentials;
    private final ExternalDataSourcePool pools;

    public MySqlApprovedQueryExecutor(
            DataSourceRepository sources, CredentialVaultPort credentials, ExternalDataSourcePool pools) {
        this.sources = sources;
        this.credentials = credentials;
        this.pools = pools;
    }

    @Override
    public QueryExecutionResult execute(ApprovedQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw failed("QUERY_EXECUTION_TRANSACTION_FORBIDDEN");
        }
        DataSourceView source = sources.findByTenantAndId(query.tenantId(), query.dataSourceId())
                .orElseThrow(() -> failed("DATASOURCE_UNAVAILABLE"));
        if (source.status() != DataSourceStatus.READY || source.dialect() != DataSourceDialect.MYSQL) {
            throw failed("DATASOURCE_UNAVAILABLE");
        }
        if (source.version() != query.dataSourceVersion()
                || source.authorizationVersion() != query.authorizationVersion()) {
            throw failed("APPROVAL_INVALID");
        }
        String password = credentials.resolve(query.tenantId(), query.dataSourceId(), source.credentialRef());
        ExternalDataSourceConnectionSpec spec = new ExternalDataSourceConnectionSpec(
                query.tenantId(), query.dataSourceId(), source.host(), source.port(), source.database(),
                source.username(), password, source.dialect(), EXECUTION_POOL_SIZE, source.timeoutSeconds());
        try (Connection connection = pools.getOrCreate(spec).getConnection()) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(query.timeoutSeconds());
                statement.setMaxRows(Math.addExact(query.maximumRows(), 1));
                statement.setFetchSize(Math.min(FETCH_SIZE, Math.addExact(query.maximumRows(), 1)));
                try (ResultSet result = statement.executeQuery(query.normalizedSql())) {
                    return read(result, query.maximumRows());
                }
            }
        } catch (SQLTimeoutException failure) {
            throw new QueryExecutionFailure(QueryExecutionStatus.TIMEOUT, "QUERY_TIMEOUT");
        } catch (SQLException | ArithmeticException failure) {
            throw failed("QUERY_EXECUTION_FAILED");
        } catch (RuntimeException failure) {
            if (failure instanceof QueryExecutionFailure executionFailure) {
                throw executionFailure;
            }
            throw failed("QUERY_EXECUTION_FAILED");
        }
    }

    private static QueryExecutionResult read(ResultSet result, int maximumRows) throws SQLException {
        ResultSetMetaData metadata = result.getMetaData();
        List<QueryResultColumn> columns = columns(metadata);
        QueryResultSizeBudget sizeBudget = new QueryResultSizeBudget();
        sizeBudget.addColumns(columns);
        MessageDigest digest = sha256();
        columns.forEach(column -> update(digest, column.name() + ":" + column.type()));
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (result.next()) {
            if (rows.size() == maximumRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columns.size());
            for (int index = 1; index <= columns.size(); index++) {
                Object value = readValue(result, metadata.getColumnType(index), index);
                row.add(value);
            }
            if (!sizeBudget.tryAddRow(row)) {
                truncated = true;
                break;
            }
            row.forEach(value -> update(digest, canonical(value)));
            rows.add(List.copyOf(row));
        }
        return new QueryExecutionResult(columns, rows, truncated, HexFormat.of().formatHex(digest.digest()));
    }

    private static List<QueryResultColumn> columns(ResultSetMetaData metadata) throws SQLException {
        List<QueryResultColumn> columns = new ArrayList<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            int type = metadata.getColumnType(index);
            requireSupported(type);
            String label = metadata.getColumnLabel(index);
            columns.add(new QueryResultColumn(label, JDBCType.valueOf(type).getName()));
        }
        return List.copyOf(columns);
    }

    private static Object readValue(ResultSet result, int type, int index) throws SQLException {
        return switch (type) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> result.getObject(index, Long.class);
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> result.getObject(index, Double.class);
            case Types.NUMERIC, Types.DECIMAL -> result.getObject(index, BigDecimal.class);
            case Types.BIT, Types.BOOLEAN -> result.getObject(index, Boolean.class);
            case Types.DATE -> localDate(result.getDate(index));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> localTime(result.getTime(index));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> localDateTime(result.getTimestamp(index));
            case Types.LONGVARCHAR, Types.LONGNVARCHAR -> readBoundedText(result.getCharacterStream(index));
            default -> result.getString(index);
        };
    }

    private static String readBoundedText(Reader reader) throws SQLException {
        if (reader == null) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[4096];
        try (reader) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                value.append(buffer, 0, read);
                if (value.length() > QueryResultSizeBudget.MAX_CELL_BYTES) {
                    throw failed("QUERY_RESULT_TOO_LARGE");
                }
            }
            return value.toString();
        } catch (IOException failure) {
            throw new SQLException("QUERY_RESULT_READ_FAILED", failure);
        }
    }

    private static void requireSupported(int type) {
        if (type != Types.CHAR && type != Types.VARCHAR && type != Types.LONGVARCHAR
                && type != Types.NCHAR && type != Types.NVARCHAR && type != Types.LONGNVARCHAR
                && type != Types.TINYINT && type != Types.SMALLINT && type != Types.INTEGER
                && type != Types.BIGINT && type != Types.FLOAT && type != Types.REAL && type != Types.DOUBLE
                && type != Types.NUMERIC && type != Types.DECIMAL && type != Types.BIT && type != Types.BOOLEAN
                && type != Types.DATE && type != Types.TIME && type != Types.TIME_WITH_TIMEZONE
                && type != Types.TIMESTAMP && type != Types.TIMESTAMP_WITH_TIMEZONE) {
            throw failed("QUERY_RESULT_TYPE_FORBIDDEN");
        }
    }

    private static Object localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static Object localTime(Time value) {
        return value == null ? null : value.toLocalTime();
    }

    private static Object localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof TemporalAccessor
                || value instanceof String) {
            return value.toString();
        }
        throw failed("QUERY_RESULT_TYPE_FORBIDDEN");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static QueryExecutionFailure failed(String code) {
        return new QueryExecutionFailure(QueryExecutionStatus.FAILED, code);
    }
}
