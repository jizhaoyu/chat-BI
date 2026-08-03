package com.jizhaoyu.chatbi.application.catalog;

import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;

import java.util.List;
import java.util.UUID;

public interface CatalogPermissionRepository {
    void replace(UUID tenantId, UUID subjectId, UUID dataSourceId, List<CatalogPermission> permissions);

    List<CatalogPermission> findGranted(UUID tenantId, UUID subjectId, UUID dataSourceId);
}
