package com.jizhaoyu.chatbi.application.execution;

import java.util.UUID;

public record QueryExecutionResponse(UUID executionId, QueryExecutionStatus status, QueryExecutionResult result) {
}
