package com.jizhaoyu.chatbi.application.catalog;

import java.util.List;

public record DiscoveredRelation(
        String sourceTable,
        List<String> sourceColumns,
        String targetTable,
        List<String> targetColumns,
        String relationType) {
    public DiscoveredRelation {
        sourceColumns = List.copyOf(sourceColumns);
        targetColumns = List.copyOf(targetColumns);
    }
}
