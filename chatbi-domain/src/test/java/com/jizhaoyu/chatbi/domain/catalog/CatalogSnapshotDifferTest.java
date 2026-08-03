package com.jizhaoyu.chatbi.domain.catalog;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogSnapshotDifferTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID dataSourceId = UUID.randomUUID();

    @Test
    void detectsPhysicalAndSemanticChangesUsingQualifiedNames() {
        CatalogSnapshot before = snapshot(1, "DECIMAL", "", List.of("legacy"), "orders");
        CatalogSnapshot after = snapshot(2, "BIGINT", "销售额", List.of("营收"), "orders");

        CatalogSnapshotDiff diff = new CatalogSnapshotDiffer().diff(before, after);

        assertThat(diff.changedColumns()).containsExactly("sales.orders.amount");
        assertThat(diff.addedTables()).isEmpty();
        assertThat(diff.removedTables()).isEmpty();
    }

    @Test
    void rejectsDiffAcrossTenantBoundary() {
        CatalogSnapshot before = snapshot(1, "DECIMAL", "", List.of(), "orders");
        UUID foreignTenant = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        CatalogSnapshot after = new CatalogSnapshot(snapshotId, foreignTenant, dataSourceId, 2,
                CatalogSnapshotStatus.ACTIVE, List.of(), List.of(), Instant.now(), Instant.now());

        assertThatThrownBy(() -> new CatalogSnapshotDiffer().diff(before, after))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CATALOG_DIFF_SCOPE_MISMATCH");
    }

    private CatalogSnapshot snapshot(long version, String dataType, String businessName, List<String> synonyms, String tableName) {
        UUID snapshotId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        CatalogColumn column = new CatalogColumn(UUID.randomUUID(), tenantId, tableId, "amount", dataType, false, 1,
                "", new SemanticMetadata(businessName, synonyms, SensitivityLevel.PUBLIC), true);
        CatalogTable table = new CatalogTable(tableId, tenantId, snapshotId, "sales", tableName, "",
                SemanticMetadata.physicalOnly(), true, List.of(column));
        return new CatalogSnapshot(snapshotId, tenantId, dataSourceId, version, CatalogSnapshotStatus.ACTIVE,
                List.of(table), List.of(), Instant.now(), Instant.now());
    }
}
