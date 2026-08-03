package com.jizhaoyu.chatbi.application.catalog;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogAuthorization;
import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;
import com.jizhaoyu.chatbi.domain.catalog.CatalogRelation;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotDiff;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotDiffer;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotStatus;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CatalogApplicationService {
    private final CatalogSnapshotRepository snapshots;
    private final CatalogMetadataReader metadataReader;
    private final AuditPort audit;
    private final CatalogPermissionRepository permissions;
    private final Clock clock;
    private final CatalogSnapshotDiffer differ = new CatalogSnapshotDiffer();

    public CatalogApplicationService(
            CatalogSnapshotRepository snapshots,
            CatalogMetadataReader metadataReader,
            CatalogPermissionRepository permissions,
            AuditPort audit,
            Clock clock) {
        this.snapshots = snapshots;
        this.metadataReader = metadataReader;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    public CatalogSyncResult synchronize(UserPrincipal actor, UUID dataSourceId) {
        requireDataAdmin(actor);
        UUID tenantId = actor.tenantId();
        UUID snapshotId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        CatalogSyncAttempt attempt = snapshots.beginSync(tenantId, dataSourceId, snapshotId, startedAt);
        try {
            appendAudit(actor, dataSourceId, "CATALOG_SYNC_STARTED", "ALLOWED", attempt.version());
            CatalogSnapshot previous = snapshots.findActive(tenantId, dataSourceId).orElse(null);
            DiscoveredCatalog discovered = metadataReader.read(tenantId, dataSourceId);
            CatalogSnapshot syncing = assemble(attempt, tenantId, dataSourceId, startedAt, discovered, previous);
            CatalogSnapshot active = snapshots.completeAndActivate(syncing);
            CatalogSnapshotDiff diff = previous == null ? emptyTo(active) : differ.diff(previous, active);
            appendAudit(actor, dataSourceId, "CATALOG_SYNC_ACTIVATED", "ALLOWED", attempt.version());
            return new CatalogSyncResult(active, diff);
        } catch (RuntimeException failure) {
            snapshots.markFailed(tenantId, dataSourceId, snapshotId);
            try {
                appendAudit(actor, dataSourceId, "CATALOG_SYNC_FAILED", "DENIED", attempt.version());
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw failure;
        }
    }

    public CatalogSnapshotDiff diff(UserPrincipal actor, UUID dataSourceId, UUID beforeId, UUID afterId) {
        requireDataAdmin(actor);
        CatalogSnapshot before = snapshots.findById(actor.tenantId(), dataSourceId, beforeId)
                .orElseThrow(() -> new IllegalArgumentException("CATALOG_SNAPSHOT_NOT_FOUND"));
        CatalogSnapshot after = snapshots.findById(actor.tenantId(), dataSourceId, afterId)
                .orElseThrow(() -> new IllegalArgumentException("CATALOG_SNAPSHOT_NOT_FOUND"));
        return differ.diff(before, after);
    }

    public CatalogSnapshot active(UserPrincipal actor, UUID dataSourceId) {
        CatalogSnapshot active = snapshots.findActive(actor.tenantId(), dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("CATALOG_ACTIVE_SNAPSHOT_NOT_FOUND"));
        if (actor.has(Role.DATA_ADMIN)) {
            return active;
        }
        if (!actor.has(Role.ANALYST)) {
            throw new SecurityException("FORBIDDEN");
        }
        CatalogAuthorization authorization = new CatalogAuthorization(
                actor.tenantId(), actor.userId(), dataSourceId,
                permissions.findGranted(actor.tenantId(), actor.userId(), dataSourceId));
        List<CatalogTable> allowedTables = active.tables().stream()
                .filter(authorization::canAccess)
                .map(table -> new CatalogTable(table.id(), table.tenantId(), table.snapshotId(),
                        table.schemaName(), table.name(), table.comment(), table.semantic(), table.enabled(),
                        table.columns().stream()
                                .filter(column -> authorization.canAccess(table, column))
                                .toList()))
                .toList();
        java.util.Set<UUID> allowedTableIds = allowedTables.stream()
                .map(CatalogTable::id).collect(java.util.stream.Collectors.toSet());
        Map<UUID, java.util.Set<String>> allowedColumnNames = allowedTables.stream()
                .collect(Collectors.toMap(CatalogTable::id, table -> table.columns().stream()
                        .map(CatalogColumn::name).collect(Collectors.toSet())));
        List<CatalogRelation> allowedRelations = active.relations().stream()
                .filter(relation -> allowedTableIds.contains(relation.sourceTableId())
                        && allowedTableIds.contains(relation.targetTableId()))
                .filter(relation -> allowedColumnNames.get(relation.sourceTableId())
                                .containsAll(relation.sourceColumns())
                        && allowedColumnNames.get(relation.targetTableId())
                                .containsAll(relation.targetColumns()))
                .toList();
        return new CatalogSnapshot(active.id(), active.tenantId(), active.dataSourceId(), active.version(),
                active.status(), allowedTables, allowedRelations, active.createdAt(), active.activatedAt());
    }

    public void replacePermissions(
            UserPrincipal actor, UUID dataSourceId, UUID subjectId, List<CatalogPermission> grants) {
        requireDataAdmin(actor);
        permissions.replace(actor.tenantId(), subjectId, dataSourceId, List.copyOf(grants));
        appendAudit(actor, dataSourceId, "CATALOG_PERMISSIONS_REPLACED", "ALLOWED", grants.size());
    }

    public void updateColumnSemantic(
            UserPrincipal actor,
            UUID dataSourceId,
            UUID columnId,
            SemanticMetadata semantic,
            boolean enabled) {
        requireDataAdmin(actor);
        snapshots.updateColumnSemantic(actor.tenantId(), dataSourceId, columnId, semantic, enabled);
        appendAudit(actor, dataSourceId, "CATALOG_COLUMN_SEMANTIC_UPDATED", "ALLOWED", 0);
    }

    private CatalogSnapshot assemble(
            CatalogSyncAttempt attempt,
            UUID tenantId,
            UUID dataSourceId,
            Instant createdAt,
            DiscoveredCatalog discovered,
            CatalogSnapshot previous) {
        Map<String, CatalogTable> previousTables = previous == null ? Map.of() : previous.tables().stream()
                .collect(Collectors.toMap(CatalogTable::qualifiedName, Function.identity()));
        Map<String, UUID> tableIds = discovered.tables().stream()
                .collect(Collectors.toMap(DiscoveredTable::qualifiedName, ignored -> UUID.randomUUID()));
        List<CatalogTable> tables = new ArrayList<>();
        for (DiscoveredTable table : discovered.tables()) {
            UUID tableId = tableIds.get(table.qualifiedName());
            CatalogTable previousTable = previousTables.get(table.qualifiedName());
            Map<String, CatalogColumn> previousColumns = previousTable == null ? Map.of() : previousTable.columns().stream()
                    .collect(Collectors.toMap(CatalogColumn::name, Function.identity()));
            List<CatalogColumn> columns = table.columns().stream().map(column -> {
                CatalogColumn previousColumn = previousColumns.get(column.name());
                SemanticMetadata semantic = previousColumn == null
                        ? SemanticMetadata.physicalOnly() : previousColumn.semantic();
                boolean enabled = previousColumn == null || previousColumn.enabled();
                return new CatalogColumn(UUID.randomUUID(), tenantId, tableId, column.name(), column.dataType(),
                        column.nullable(), column.ordinal(), column.comment(), semantic, enabled);
            }).toList();
            SemanticMetadata semantic = previousTable == null ? SemanticMetadata.physicalOnly() : previousTable.semantic();
            boolean enabled = previousTable == null || previousTable.enabled();
            tables.add(new CatalogTable(tableId, tenantId, attempt.snapshotId(), table.schemaName(), table.name(),
                    table.comment(), semantic, enabled, columns));
        }
        List<CatalogRelation> relations = discovered.relations().stream().map(relation -> new CatalogRelation(
                UUID.randomUUID(), tenantId, attempt.snapshotId(), requireTable(tableIds, relation.sourceTable()),
                relation.sourceColumns(), requireTable(tableIds, relation.targetTable()), relation.targetColumns(),
                relation.relationType())).toList();
        return new CatalogSnapshot(attempt.snapshotId(), tenantId, dataSourceId, attempt.version(),
                CatalogSnapshotStatus.SYNCING, tables, relations, createdAt, null);
    }

    private CatalogSnapshotDiff emptyTo(CatalogSnapshot snapshot) {
        CatalogSnapshot empty = new CatalogSnapshot(UUID.randomUUID(), snapshot.tenantId(), snapshot.dataSourceId(), 1,
                CatalogSnapshotStatus.SYNCING, List.of(), List.of(), snapshot.createdAt(), null);
        return differ.diff(empty, snapshot);
    }

    private static UUID requireTable(Map<String, UUID> tableIds, String qualifiedName) {
        UUID tableId = tableIds.get(qualifiedName);
        if (tableId == null) {
            throw new IllegalArgumentException("CATALOG_RELATION_TABLE_NOT_FOUND");
        }
        return tableId;
    }

    private static void requireDataAdmin(UserPrincipal actor) {
        if (actor == null || !actor.roles().contains(Role.DATA_ADMIN)) {
            throw new SecurityException("FORBIDDEN");
        }
    }

    private void appendAudit(UserPrincipal actor, UUID dataSourceId, String action, String decision, long version) {
        audit.append(new AuditEvent(actor.tenantId(), actor.userId(), action, "DATA_SOURCE", dataSourceId, decision,
                "{\"catalogVersion\":" + version + "}"));
    }
}
