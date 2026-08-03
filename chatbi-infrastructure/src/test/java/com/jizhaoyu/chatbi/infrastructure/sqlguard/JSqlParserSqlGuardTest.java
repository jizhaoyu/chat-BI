package com.jizhaoyu.chatbi.infrastructure.sqlguard;

import com.jizhaoyu.chatbi.application.sqlguard.SqlGuardContext;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotStatus;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JSqlParserSqlGuardTest {
    private final JSqlParserSqlGuard guard = new JSqlParserSqlGuard();
    private final SqlGuardContext context = new SqlGuardContext(catalog(), 200);

    @Test
    void approvesAuthorizedSelectAndInjectsLimit() {
        var result = guard.validate(
                "SELECT id, total_amount FROM sample_sales.fact_order WHERE status = 'PAID'", context);

        assertThat(result.normalizedSql()).endsWith("LIMIT 200");
        assertThat(result.effectiveLimit()).isEqualTo(200);
        assertThat(result.references()).extracting(reference -> reference.columnName())
                .containsExactly("id", "total_amount", "status");
    }

    @Test
    void approvesJoinAggregateGroupingAndAllowedFunctions() {
        var result = guard.validate("""
                SELECT s.store_name, ROUND(SUM(o.total_amount), 2) AS sales
                FROM sample_sales.fact_order o
                INNER JOIN sample_sales.dim_store s ON o.store_id = s.id
                WHERE YEAR(o.order_date) = 2026
                GROUP BY s.store_name
                HAVING SUM(o.total_amount) > 0
                ORDER BY sales DESC
                LIMIT 10
                """, context);

        assertThat(result.effectiveLimit()).isEqualTo(10);
        assertThat(result.references()).extracting(reference -> reference.columnName())
                .contains("store_name", "total_amount", "store_id", "id", "order_date");
    }

    @Test
    void tightensExcessiveLimitWithoutIncreasingSmallerLimit() {
        assertThat(guard.validate("SELECT id FROM fact_order LIMIT 100000", context).effectiveLimit())
                .isEqualTo(200);
        assertThat(guard.validate("SELECT id FROM fact_order LIMIT 5", context).effectiveLimit())
                .isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("rejectedSql")
    void rejectsUnsupportedOrUnsafeSql(String sql, String code) {
        assertThatThrownBy(() -> guard.validate(sql, context))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(code);
    }

    private static Stream<Arguments> rejectedSql() {
        return Stream.of(
                Arguments.of("SELECT id FROM fact_order; DROP TABLE fact_order", "SQL_MULTIPLE_STATEMENTS"),
                Arguments.of("SELECT id FROM fact_order /* hidden */", "SQL_COMMENT_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order /*!50000 UNION SELECT 1 */", "SQL_COMMENT_FORBIDDEN"),
                Arguments.of("DELETE FROM fact_order", "SQL_STATEMENT_FORBIDDEN"),
                Arguments.of("SELECT * FROM fact_order", "SQL_WILDCARD_FORBIDDEN"),
                Arguments.of("SELECT o.* FROM fact_order o", "SQL_WILDCARD_FORBIDDEN"),
                Arguments.of("SELECT password FROM mysql.user", "SQL_OBJECT_FORBIDDEN"),
                Arguments.of("SELECT cost_price FROM dim_product", "SQL_COLUMN_FORBIDDEN"),
                Arguments.of("SELECT missing FROM fact_order", "SQL_COLUMN_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order UNION SELECT id FROM dim_store", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order WHERE store_id IN (SELECT id FROM dim_store)",
                        "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("WITH x AS (SELECT id FROM fact_order) SELECT id FROM x", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT LOAD_FILE('/etc/passwd') FROM fact_order", "SQL_FUNCTION_FORBIDDEN"),
                Arguments.of("SELECT SLEEP(1) FROM fact_order", "SQL_FUNCTION_FORBIDDEN"),
                Arguments.of("SELECT @x := total_amount FROM fact_order", "SQL_VARIABLE_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order LIMIT ?", "SQL_LIMIT_INVALID"),
                Arguments.of("SELECT id FROM fact_order LIMIT 10 OFFSET 1", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order FOR UPDATE", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT id, ROW_NUMBER() OVER (ORDER BY id) FROM fact_order",
                        "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT CASE WHEN status = 'PAID' THEN 1 ELSE 0 END FROM fact_order",
                        "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT CAST(total_amount AS SIGNED) FROM fact_order", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT total_amount % 10 FROM fact_order", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order WHERE status ILIKE 'paid'", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order WHERE id = store_id(+)", "SQL_FEATURE_FORBIDDEN"),
                Arguments.of("SELECT id FROM fact_order o JOIN dim_store s WHERE o.store_id = s.id",
                        "SQL_FEATURE_FORBIDDEN"));
    }

    private static CatalogSnapshot catalog() {
        UUID tenant = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID snapshot = UUID.randomUUID();
        CatalogTable orders = table(tenant, snapshot, "fact_order",
                "id", "total_amount", "status", "store_id", "order_date");
        CatalogTable stores = table(tenant, snapshot, "dim_store", "id", "store_name");
        CatalogTable products = table(tenant, snapshot, "dim_product", "id", "category");
        return new CatalogSnapshot(snapshot, tenant, source, 1, CatalogSnapshotStatus.ACTIVE,
                List.of(orders, stores, products), List.of(), Instant.EPOCH, Instant.EPOCH);
    }

    private static CatalogTable table(UUID tenant, UUID snapshot, String name, String... columns) {
        UUID tableId = UUID.randomUUID();
        List<CatalogColumn> catalogColumns = java.util.stream.IntStream.range(0, columns.length)
                .mapToObj(index -> new CatalogColumn(UUID.randomUUID(), tenant, tableId, columns[index], "VARCHAR",
                        true, index + 1, "", SemanticMetadata.physicalOnly(), true))
                .toList();
        return new CatalogTable(tableId, tenant, snapshot, "sample_sales", name, "",
                SemanticMetadata.physicalOnly(), true, catalogColumns);
    }
}
