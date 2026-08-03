package com.jizhaoyu.chatbi.domain.catalog;

import java.util.Set;

public record CatalogSnapshotDiff(
        Set<String> addedTables,
        Set<String> removedTables,
        Set<String> changedTables,
        Set<String> addedColumns,
        Set<String> removedColumns,
        Set<String> changedColumns,
        Set<String> addedRelations,
        Set<String> removedRelations) {
    public CatalogSnapshotDiff {
        addedTables = Set.copyOf(addedTables);
        removedTables = Set.copyOf(removedTables);
        changedTables = Set.copyOf(changedTables);
        addedColumns = Set.copyOf(addedColumns);
        removedColumns = Set.copyOf(removedColumns);
        changedColumns = Set.copyOf(changedColumns);
        addedRelations = Set.copyOf(addedRelations);
        removedRelations = Set.copyOf(removedRelations);
    }
}
