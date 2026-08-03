package com.jizhaoyu.chatbi.application.execution;

import java.util.List;

public record QueryExecutionResult(
        List<QueryResultColumn> columns,
        List<List<Object>> rows,
        boolean truncated,
        String resultDigest) {
    public QueryExecutionResult {
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
        if (resultDigest == null || resultDigest.isBlank()) {
            throw new IllegalArgumentException("QUERY_RESULT_DIGEST_REQUIRED");
        }
    }
}
