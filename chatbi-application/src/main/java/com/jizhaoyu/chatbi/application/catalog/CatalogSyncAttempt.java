package com.jizhaoyu.chatbi.application.catalog;

import java.util.UUID;

public record CatalogSyncAttempt(UUID snapshotId, long version) {
}
