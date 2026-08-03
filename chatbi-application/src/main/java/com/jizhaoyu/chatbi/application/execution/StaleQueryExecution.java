package com.jizhaoyu.chatbi.application.execution;

import java.time.Instant;
import java.util.UUID;

public record StaleQueryExecution(
        UUID executionId, UUID tenantId, UUID executorUserId, UUID dataSourceId, Instant startedAt) {
}
