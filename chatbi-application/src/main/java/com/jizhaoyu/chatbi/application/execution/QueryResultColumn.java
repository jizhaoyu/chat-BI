package com.jizhaoyu.chatbi.application.execution;

public record QueryResultColumn(String name, String type) {
    public QueryResultColumn {
        if (name == null || name.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("QUERY_RESULT_SCHEMA_INVALID");
        }
    }
}
