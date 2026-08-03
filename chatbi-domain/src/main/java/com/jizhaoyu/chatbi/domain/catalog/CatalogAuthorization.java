package com.jizhaoyu.chatbi.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CatalogAuthorization {
    private final UUID tenantId;
    private final UUID subjectId;
    private final UUID dataSourceId;
    private final List<CatalogPermission> permissions;

    public CatalogAuthorization(
            UUID tenantId, UUID subjectId, UUID dataSourceId, List<CatalogPermission> permissions) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.dataSourceId = Objects.requireNonNull(dataSourceId, "dataSourceId");
        this.permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions"));
        if (this.permissions.stream().anyMatch(permission -> !tenantId.equals(permission.tenantId())
                || !subjectId.equals(permission.subjectId()) || !dataSourceId.equals(permission.dataSourceId()))) {
            throw new SecurityException("CATALOG_PERMISSION_SCOPE_MISMATCH");
        }
    }

    public boolean canAccess(CatalogTable table) {
        requireTenant(table.tenantId());
        return hasPermission(CatalogObjectType.TABLE, table.id()) && table.enabled();
    }

    public boolean canAccess(CatalogTable table, CatalogColumn column) {
        requireTenant(table.tenantId());
        requireTenant(column.tenantId());
        if (!table.id().equals(column.tableId())) {
            throw new IllegalArgumentException("CATALOG_COLUMN_TABLE_MISMATCH");
        }
        return canAccess(table) && column.enabled() && hasPermission(CatalogObjectType.COLUMN, column.id());
    }

    private boolean hasPermission(CatalogObjectType type, UUID objectId) {
        return permissions.stream().anyMatch(permission -> permission.objectType() == type
                && permission.objectId().equals(objectId));
    }

    private void requireTenant(UUID resourceTenantId) {
        if (!tenantId.equals(resourceTenantId)) {
            throw new SecurityException("CATALOG_TENANT_MISMATCH");
        }
    }
}
