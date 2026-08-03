package com.jizhaoyu.chatbi.application.catalog;

import java.util.UUID;

public interface CatalogMetadataReader {
    DiscoveredCatalog read(UUID tenantId, UUID dataSourceId);
}
