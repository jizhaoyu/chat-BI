package com.jizhaoyu.chatbi.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CatalogRelation(
        UUID id,
        UUID tenantId,
        UUID snapshotId,
        UUID sourceTableId,
        List<String> sourceColumns,
        UUID targetTableId,
        List<String> targetColumns,
        String relationType) {
    public CatalogRelation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(sourceTableId, "sourceTableId");
        Objects.requireNonNull(targetTableId, "targetTableId");
        sourceColumns = requireColumns(sourceColumns);
        targetColumns = requireColumns(targetColumns);
        if (sourceColumns.size() != targetColumns.size()) {
            throw new IllegalArgumentException("CATALOG_RELATION_COLUMN_COUNT_MISMATCH");
        }
        if (relationType == null || relationType.isBlank()) {
            throw new IllegalArgumentException("CATALOG_RELATION_TYPE_REQUIRED");
        }
    }

    private static List<String> requireColumns(List<String> columns) {
        List<String> copy = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (copy.isEmpty() || copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("CATALOG_RELATION_COLUMNS_REQUIRED");
        }
        return copy;
    }
}
