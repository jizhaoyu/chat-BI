package com.jizhaoyu.chatbi.domain.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogAuthorizationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID dataSourceId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final UUID tableId = UUID.randomUUID();
    private final UUID columnId = UUID.randomUUID();
    private final CatalogColumn column = new CatalogColumn(columnId, tenantId, tableId, "amount", "DECIMAL", false,
            1, "", new SemanticMetadata("销售额", List.of("营收"), SensitivityLevel.INTERNAL), true);
    private final CatalogTable table = new CatalogTable(tableId, tenantId, snapshotId, "sales", "orders", "",
            SemanticMetadata.physicalOnly(), true, List.of(column));

    @Test
    void deniesTableAndColumnByDefault() {
        CatalogAuthorization authorization = new CatalogAuthorization(tenantId, subjectId, dataSourceId, List.of());

        assertThat(authorization.canAccess(table)).isFalse();
        assertThat(authorization.canAccess(table, column)).isFalse();
    }

    @Test
    void tableGrantDoesNotImplicitlyGrantColumns() {
        CatalogAuthorization authorization = new CatalogAuthorization(tenantId, subjectId, dataSourceId,
                List.of(permission(CatalogObjectType.TABLE, tableId)));

        assertThat(authorization.canAccess(table)).isTrue();
        assertThat(authorization.canAccess(table, column)).isFalse();
    }

    @Test
    void permitsColumnOnlyWhenTableAndColumnAreExplicitlyGranted() {
        CatalogAuthorization authorization = new CatalogAuthorization(tenantId, subjectId, dataSourceId,
                List.of(permission(CatalogObjectType.TABLE, tableId), permission(CatalogObjectType.COLUMN, columnId)));

        assertThat(authorization.canAccess(table, column)).isTrue();
    }

    @Test
    void rejectsCrossTenantPermissionsAndResources() {
        CatalogPermission foreignPermission = new CatalogPermission(UUID.randomUUID(), UUID.randomUUID(), "USER",
                subjectId, dataSourceId, CatalogObjectType.TABLE, tableId, "");
        assertThatThrownBy(() -> new CatalogAuthorization(tenantId, subjectId, dataSourceId, List.of(foreignPermission)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CATALOG_PERMISSION_SCOPE_MISMATCH");

        CatalogAuthorization authorization = new CatalogAuthorization(tenantId, subjectId, dataSourceId, List.of());
        CatalogTable foreignTable = new CatalogTable(UUID.randomUUID(), UUID.randomUUID(), snapshotId, "sales", "orders",
                "", SemanticMetadata.physicalOnly(), true, List.of());
        assertThatThrownBy(() -> authorization.canAccess(foreignTable))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CATALOG_TENANT_MISMATCH");
    }

    private CatalogPermission permission(CatalogObjectType type, UUID objectId) {
        return new CatalogPermission(UUID.randomUUID(), tenantId, "USER", subjectId, dataSourceId, type, objectId, "");
    }
}
