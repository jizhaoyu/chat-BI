package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;

import java.time.Instant;
import java.util.UUID;

public record PreparedQueryExecution(UUID executionId, ApprovedQuery query, Instant startedAt) {
}
