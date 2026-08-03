package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionRepository;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
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

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
