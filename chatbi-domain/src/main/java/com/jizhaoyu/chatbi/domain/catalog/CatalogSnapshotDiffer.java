package com.jizhaoyu.chatbi.domain.catalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CatalogSnapshotDiffer {
    public CatalogSnapshotDiff diff(CatalogSnapshot before, CatalogSnapshot after) {
        if (!before.tenantId().equals(after.tenantId()) || !before.dataSourceId().equals(after.dataSourceId())) {
            throw new SecurityException("CATALOG_DIFF_SCOPE_MISMATCH");
        }
        Map<String, CatalogTable> oldTables = indexTables(before);
        Map<String, CatalogTable> newTables = indexTables(after);
        Set<String> addedTables = difference(newTables.keySet(), oldTables.keySet());
        Set<String> removedTables = difference(oldTables.keySet(), newTables.keySet());
        Set<String> commonTables = intersection(oldTables.keySet(), newTables.keySet());
        Set<String> changedTables = commonTables.stream()
                .filter(name -> tableChanged(oldTables.get(name), newTables.get(name)))
                .collect(Collectors.toUnmodifiableSet());

        Map<String, CatalogColumn> oldColumns = indexColumns(oldTables);
        Map<String, CatalogColumn> newColumns = indexColumns(newTables);
        Set<String> changedColumns = intersection(oldColumns.keySet(), newColumns.keySet()).stream()
                .filter(name -> columnChanged(oldColumns.get(name), newColumns.get(name)))
                .collect(Collectors.toUnmodifiableSet());

        Set<String> oldRelations = relationKeys(before, oldTables);
        Set<String> newRelations = relationKeys(after, newTables);
        return new CatalogSnapshotDiff(
                addedTables, removedTables, changedTables,
                difference(newColumns.keySet(), oldColumns.keySet()),
                difference(oldColumns.keySet(), newColumns.keySet()),
                changedColumns,
                difference(newRelations, oldRelations),
                difference(oldRelations, newRelations));
    }

    private static Map<String, CatalogTable> indexTables(CatalogSnapshot snapshot) {
        return snapshot.tables().stream().collect(Collectors.toUnmodifiableMap(CatalogTable::qualifiedName, Function.identity()));
    }

    private static Map<String, CatalogColumn> indexColumns(Map<String, CatalogTable> tables) {
        Map<String, CatalogColumn> columns = new HashMap<>();
        tables.forEach((tableName, table) -> table.columns().forEach(column -> columns.put(tableName + "." + column.name(), column)));
        return Map.copyOf(columns);
    }

    private static boolean tableChanged(CatalogTable before, CatalogTable after) {
        return !before.comment().equals(after.comment())
                || !before.semantic().equals(after.semantic())
                || before.enabled() != after.enabled();
    }

    private static boolean columnChanged(CatalogColumn before, CatalogColumn after) {
        return !before.dataType().equals(after.dataType())
                || before.nullable() != after.nullable()
                || before.ordinal() != after.ordinal()
                || !before.comment().equals(after.comment())
                || !before.semantic().equals(after.semantic())
                || before.enabled() != after.enabled();
    }

    private static Set<String> relationKeys(CatalogSnapshot snapshot, Map<String, CatalogTable> tables) {
        Map<java.util.UUID, String> tableNames = tables.values().stream()
                .collect(Collectors.toMap(CatalogTable::id, CatalogTable::qualifiedName));
        return snapshot.relations().stream().map(relation ->
                tableNames.get(relation.sourceTableId()) + relation.sourceColumns()
                        + "->" + tableNames.get(relation.targetTableId()) + relation.targetColumns()
                        + ":" + relation.relationType()).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }
}
