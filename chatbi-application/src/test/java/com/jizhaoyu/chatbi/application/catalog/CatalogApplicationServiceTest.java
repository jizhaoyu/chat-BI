package com.jizhaoyu.chatbi.application.catalog;

import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogObjectType;
import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotStatus;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogApplicationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final UUID tableId = UUID.randomUUID();
    private final UUID columnId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);
    private final MemorySnapshots snapshots = new MemorySnapshots(activeSnapshot());
    private final MemoryPermissions permissions = new MemoryPermissions();

    @Test
    void returnsEmptyCatalogToAnalystWithoutExplicitGrants() {
        CatalogSnapshot visible = service((tenant, source) -> {
            throw new UnsupportedOperationException();
        }).active(analyst(), sourceId);

        assertThat(visible.tables()).isEmpty();
    }

    @Test
    void tableGrantDoesNotImplicitlyGrantColumns() {
        permissions.grants = List.of(permission(CatalogObjectType.TABLE, tableId));

        CatalogSnapshot visible = service((tenant, source) -> {
            throw new UnsupportedOperationException();
        }).active(analyst(), sourceId);

        assertThat(visible.tables()).singleElement().satisfies(table ->
                assertThat(table.columns()).isEmpty());
    }

    @Test
    void returnsOnlyExplicitlyGrantedTableAndColumn() {
        permissions.grants = List.of(
                permission(CatalogObjectType.TABLE, tableId),
                permission(CatalogObjectType.COLUMN, columnId));

        CatalogSnapshot visible = service((tenant, source) -> {
            throw new UnsupportedOperationException();
        }).active(analyst(), sourceId);

        assertThat(visible.tables()).singleElement().satisfies(table ->
                assertThat(table.columns()).extracting(CatalogColumn::id).containsExactly(columnId));
    }

    @Test
    void failedSynchronizationMarksOnlyNewSnapshotFailed() {
        CatalogApplicationService service = service((tenant, source) -> {
            throw new IllegalStateException("CATALOG_METADATA_READ_FAILED");
        });

        assertThatThrownBy(() -> service.synchronize(admin(), sourceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CATALOG_METADATA_READ_FAILED");
        assertThat(snapshots.failed).isNotNull();
        assertThat(snapshots.active.id()).isEqualTo(snapshotId);
    }

    private CatalogApplicationService service(CatalogMetadataReader reader) {
        return new CatalogApplicationService(snapshots, reader, permissions, event -> { }, clock);
    }

    private UserPrincipal analyst() {
        return new UserPrincipal(userId, tenantId, Set.of(Role.ANALYST));
    }

    private UserPrincipal admin() {
        return new UserPrincipal(userId, tenantId, Set.of(Role.DATA_ADMIN));
    }

    private CatalogPermission permission(CatalogObjectType type, UUID objectId) {
        return new CatalogPermission(UUID.randomUUID(), tenantId, "USER", userId, sourceId, type, objectId, "");
    }

    private CatalogSnapshot activeSnapshot() {
        CatalogColumn column = new CatalogColumn(columnId, tenantId, tableId, "amount", "DECIMAL",
                false, 1, "", SemanticMetadata.physicalOnly(), true);
        CatalogTable table = new CatalogTable(tableId, tenantId, snapshotId, "sample_sales", "fact_order",
                "", SemanticMetadata.physicalOnly(), true, List.of(column));
        return new CatalogSnapshot(snapshotId, tenantId, sourceId, 1, CatalogSnapshotStatus.ACTIVE,
                List.of(table), List.of(), clock.instant(), clock.instant());
    }

    private final class MemorySnapshots implements CatalogSnapshotRepository {
        private CatalogSnapshot active;
        private UUID failed;

        private MemorySnapshots(CatalogSnapshot active) {
            this.active = active;
        }

        @Override
        public CatalogSyncAttempt beginSync(UUID tenant, UUID source, UUID snapshot, Instant createdAt) {
            return new CatalogSyncAttempt(snapshot, 2);
        }

        @Override
        public CatalogSnapshot completeAndActivate(CatalogSnapshot snapshot) {
            active = snapshot;
            return snapshot;
        }

        @Override
        public void markFailed(UUID tenant, UUID source, UUID snapshot) {
            failed = snapshot;
        }

        @Override
        public Optional<CatalogSnapshot> findActive(UUID tenant, UUID source) {
            return tenantId.equals(tenant) && sourceId.equals(source) ? Optional.of(active) : Optional.empty();
        }

        @Override
        public Optional<CatalogSnapshot> findById(UUID tenant, UUID source, UUID snapshot) {
            return active.id().equals(snapshot) ? Optional.of(active) : Optional.empty();
        }

        @Override
        public void updateColumnSemantic(
                UUID tenant, UUID source, UUID column, SemanticMetadata semantic, boolean enabled) {
        }
    }

    private static final class MemoryPermissions implements CatalogPermissionRepository {
        private List<CatalogPermission> grants = new ArrayList<>();

        @Override
        public void replace(UUID tenant, UUID subject, UUID source, List<CatalogPermission> permissions) {
            grants = List.copyOf(permissions);
        }

        @Override
        public List<CatalogPermission> findGranted(UUID tenant, UUID subject, UUID source) {
            return grants;
        }
    }
}
