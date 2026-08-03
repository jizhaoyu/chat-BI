package com.jizhaoyu.chatbi.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record CatalogColumn(
        UUID id,
        UUID tenantId,
        UUID tableId,
        String name,
        String dataType,
        boolean nullable,
        int ordinal,
        String comment,
        SemanticMetadata semantic,
        boolean enabled) {
    public CatalogColumn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(tableId, "tableId");
        name = requireText(name, "name");
        dataType = requireText(dataType, "dataType");
        if (ordinal < 1) {
            throw new IllegalArgumentException("CATALOG_COLUMN_ORDINAL_INVALID");
        }
        comment = comment == null ? "" : comment;
        Objects.requireNonNull(semantic, "semantic");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CATALOG_COLUMN_" + field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }
}
