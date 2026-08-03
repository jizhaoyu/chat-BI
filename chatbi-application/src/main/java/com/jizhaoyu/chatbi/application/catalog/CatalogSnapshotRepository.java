package com.jizhaoyu.chatbi.application.catalog;

import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CatalogSnapshotRepository {
    CatalogSyncAttempt beginSync(UUID tenantId, UUID dataSourceId, UUID snapshotId, Instant createdAt);

    CatalogSnapshot completeAndActivate(CatalogSnapshot snapshot);

    void markFailed(UUID tenantId, UUID dataSourceId, UUID snapshotId);

    Optional<CatalogSnapshot> findActive(UUID tenantId, UUID dataSourceId);

    Optional<CatalogSnapshot> findById(UUID tenantId, UUID dataSourceId, UUID snapshotId);

    void updateColumnSemantic(
            UUID tenantId, UUID dataSourceId, UUID columnId, SemanticMetadata semantic, boolean enabled);
}
