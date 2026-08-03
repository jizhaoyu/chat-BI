package com.jizhaoyu.chatbi.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record CatalogPermission(
        UUID id,
        UUID tenantId,
        String subjectType,
        UUID subjectId,
        UUID dataSourceId,
        CatalogObjectType objectType,
        UUID objectId,
        String maskPolicy) {
    public CatalogPermission {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (subjectType == null || subjectType.isBlank()) {
            throw new IllegalArgumentException("CATALOG_PERMISSION_SUBJECT_TYPE_REQUIRED");
        }
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(objectId, "objectId");
        maskPolicy = maskPolicy == null ? "" : maskPolicy.trim();
    }
}
