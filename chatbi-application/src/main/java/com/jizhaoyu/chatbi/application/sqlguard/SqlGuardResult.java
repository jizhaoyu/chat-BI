package com.jizhaoyu.chatbi.application.sqlguard;

import java.util.List;
import java.util.Objects;

public record SqlGuardResult(String normalizedSql, int effectiveLimit, List<SqlObjectReference> references) {
    public SqlGuardResult {
        if (normalizedSql == null || normalizedSql.isBlank()) {
            throw new IllegalArgumentException("SQL_NORMALIZED_REQUIRED");
        }
        if (effectiveLimit < 0) {
            throw new IllegalArgumentException("SQL_LIMIT_INVALID");
        }
        references = List.copyOf(Objects.requireNonNull(references, "references"));
    }
}
