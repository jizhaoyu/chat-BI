package com.jizhaoyu.chatbi.application.execution;

public final class QueryExecutionFailure extends RuntimeException {
    private final QueryExecutionStatus status;

    public QueryExecutionFailure(QueryExecutionStatus status, String code) {
        super(code);
        if (status != QueryExecutionStatus.TIMEOUT && status != QueryExecutionStatus.CANCELLED
                && status != QueryExecutionStatus.FAILED) {
            throw new IllegalArgumentException("QUERY_EXECUTION_FAILURE_STATUS_INVALID");
        }
        this.status = status;
    }

    public QueryExecutionStatus status() {
        return status;
    }
}
