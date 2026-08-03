package com.jizhaoyu.chatbi.application.sqlguard;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record QueryApprovalEnvelope(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID dataSourceId,
        UUID metadataSnapshotId,
        long dataSourceVersion,
        long authorizationVersion,
        String ruleVersion,
        String policyHash,
        String normalizedSql,
        String sqlHash,
        String parameterHash,
        int maximumRows,
        int timeoutSeconds,
        List<SqlObjectReference> references,
        Instant expiresAt) {
    public QueryApprovalEnvelope {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        ruleVersion = requireText(ruleVersion);
        policyHash = requireText(policyHash);
        normalizedSql = requireText(normalizedSql);
        sqlHash = requireText(sqlHash);
        parameterHash = requireText(parameterHash);
        if (maximumRows < 0 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("SQL_APPROVAL_RESOURCE_LIMIT_INVALID");
        }
        references = List.copyOf(Objects.requireNonNull(references, "references"));
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SQL_APPROVAL_FIELD_REQUIRED");
        }
        return value;
    }
}
