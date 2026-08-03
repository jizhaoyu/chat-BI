package com.jizhaoyu.chatbi.domain.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CatalogTable(
        UUID id,
        UUID tenantId,
        UUID snapshotId,
        String schemaName,
        String name,
        String comment,
        SemanticMetadata semantic,
        boolean enabled,
        List<CatalogColumn> columns) {
    public CatalogTable {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        schemaName = requireText(schemaName, "schemaName");
        name = requireText(name, "name");
        comment = comment == null ? "" : comment;
        Objects.requireNonNull(semantic, "semantic");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        Set<String> names = new HashSet<>();
        for (CatalogColumn column : columns) {
            if (!tenantId.equals(column.tenantId()) || !id.equals(column.tableId())) {
                throw new IllegalArgumentException("CATALOG_COLUMN_SCOPE_MISMATCH");
            }
            if (!names.add(column.name())) {
                throw new IllegalArgumentException("CATALOG_COLUMN_DUPLICATE");
            }
        }
    }

    public String qualifiedName() {
        return schemaName + "." + name;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CATALOG_TABLE_" + field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }
}
