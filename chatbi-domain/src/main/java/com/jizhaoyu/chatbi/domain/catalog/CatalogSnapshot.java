package com.jizhaoyu.chatbi.domain.catalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CatalogSnapshot(
        UUID id,
        UUID tenantId,
        UUID dataSourceId,
        long version,
        CatalogSnapshotStatus status,
        List<CatalogTable> tables,
        List<CatalogRelation> relations,
        Instant createdAt,
        Instant activatedAt) {
    public CatalogSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        if (version < 1) {
            throw new IllegalArgumentException("CATALOG_SNAPSHOT_VERSION_INVALID");
        }
        Objects.requireNonNull(status, "status");
        tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        Objects.requireNonNull(createdAt, "createdAt");
        if ((status == CatalogSnapshotStatus.ACTIVE || status == CatalogSnapshotStatus.SUPERSEDED)
                && activatedAt == null) {
            throw new IllegalArgumentException("CATALOG_SNAPSHOT_ACTIVATED_AT_REQUIRED");
        }
        validateScope(id, tenantId, tables, relations);
    }

    public int objectCount() {
        return tables.size() + tables.stream().mapToInt(table -> table.columns().size()).sum() + relations.size();
    }

    private static void validateScope(
            UUID snapshotId, UUID tenantId, List<CatalogTable> tables, List<CatalogRelation> relations) {
        Set<UUID> tableIds = new HashSet<>();
        Set<String> qualifiedNames = new HashSet<>();
        for (CatalogTable table : tables) {
            if (!tenantId.equals(table.tenantId()) || !snapshotId.equals(table.snapshotId())) {
                throw new IllegalArgumentException("CATALOG_TABLE_SCOPE_MISMATCH");
            }
            if (!tableIds.add(table.id()) || !qualifiedNames.add(table.qualifiedName())) {
                throw new IllegalArgumentException("CATALOG_TABLE_DUPLICATE");
            }
        }
        for (CatalogRelation relation : relations) {
            if (!tenantId.equals(relation.tenantId()) || !snapshotId.equals(relation.snapshotId())
                    || !tableIds.contains(relation.sourceTableId()) || !tableIds.contains(relation.targetTableId())) {
                throw new IllegalArgumentException("CATALOG_RELATION_SCOPE_MISMATCH");
            }
        }
    }
}
