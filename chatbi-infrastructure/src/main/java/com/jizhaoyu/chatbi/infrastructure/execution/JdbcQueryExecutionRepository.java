package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionRepository;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionStatus;
import com.jizhaoyu.chatbi.application.execution.StaleQueryExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcQueryExecutionRepository implements QueryExecutionRepository {
    private final JdbcTemplate jdbc;

    public JdbcQueryExecutionRepository(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void complete(
            UUID tenantId,
            UUID executionId,
            QueryExecutionStatus status,
            Instant completedAt,
            long durationMillis,
            int rowCount,
            boolean truncated,
            String errorCode,
            String resultDigest) {
        int changed = jdbc.update("UPDATE query_execution SET status = ?, completed_at = ?, duration_ms = ?, "
                        + "row_count = ?, truncated = ?, error_code = ?, result_digest = ? "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'RUNNING'",
                status.name(), Timestamp.from(completedAt), durationMillis, rowCount, truncated,
                emptyToNull(errorCode), emptyToNull(resultDigest), tenantId.toString(), executionId.toString());
        if (changed != 1) {
            throw new IllegalStateException("QUERY_EXECUTION_STATE_CONFLICT");
        }
    }

    @Override
    public List<StaleQueryExecution> recoverStale(
            Instant completedAt, Instant startedBefore, String errorCode) {
        List<StaleQueryExecution> stale = jdbc.query(
                "SELECT id, tenant_id, executor_user_id, data_source_id, started_at "
                        + "FROM query_execution WHERE status = 'RUNNING' AND started_at < ? FOR UPDATE",
                (row, number) -> new StaleQueryExecution(
                        UUID.fromString(row.getString("id")), UUID.fromString(row.getString("tenant_id")),
                        UUID.fromString(row.getString("executor_user_id")),
                        UUID.fromString(row.getString("data_source_id")), row.getTimestamp("started_at").toInstant()),
                Timestamp.from(startedBefore));
        for (StaleQueryExecution execution : stale) {
            jdbc.update("UPDATE query_execution SET status = 'FAILED', completed_at = ?, duration_ms = ?, "
                            + "row_count = 0, truncated = FALSE, error_code = ?, result_digest = NULL "
                            + "WHERE tenant_id = ? AND id = ? AND status = 'RUNNING'",
                    Timestamp.from(completedAt), Math.max(0, Duration.between(
                            execution.startedAt(), completedAt).toMillis()), errorCode,
                    execution.tenantId().toString(), execution.executionId().toString());
        }
        return List.copyOf(stale);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
