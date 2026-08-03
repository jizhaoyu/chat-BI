package com.jizhaoyu.chatbi.application.execution;

import java.time.Instant;
import java.util.UUID;

public interface QueryExecutionRepository {
    void complete(
            UUID tenantId,
            UUID executionId,
            QueryExecutionStatus status,
            Instant completedAt,
            long durationMillis,
            int rowCount,
            boolean truncated,
            String errorCode,
            String resultDigest);
}
