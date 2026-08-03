package com.jizhaoyu.chatbi.application.sqlguard;

import java.time.Instant;
import java.util.List;

public record SqlValidationApproval(
        String approvalId,
        String normalizedSql,
        int effectiveLimit,
        List<SqlObjectReference> references,
        Instant expiresAt) {
    public SqlValidationApproval {
        references = List.copyOf(references);
    }
}
