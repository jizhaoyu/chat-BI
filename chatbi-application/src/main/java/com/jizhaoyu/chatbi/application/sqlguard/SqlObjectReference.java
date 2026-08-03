package com.jizhaoyu.chatbi.application.sqlguard;

import java.util.Objects;
import java.util.UUID;

public record SqlObjectReference(
        UUID tableId, UUID columnId, String schemaName, String tableName, String columnName) {
    public SqlObjectReference {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(columnId, "columnId");
        schemaName = requireText(schemaName);
        tableName = requireText(tableName);
        columnName = requireText(columnName);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SQL_REFERENCE_INVALID");
        }
        return value;
    }
}
