package com.jizhaoyu.chatbi.application.catalog;

import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotDiff;

public record CatalogSyncResult(CatalogSnapshot snapshot, CatalogSnapshotDiff diff) {
}
