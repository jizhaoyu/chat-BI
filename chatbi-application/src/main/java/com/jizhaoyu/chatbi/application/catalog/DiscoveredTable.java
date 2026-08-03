package com.jizhaoyu.chatbi.application.catalog;

import java.util.List;

public record DiscoveredTable(String schemaName, String name, String comment, List<DiscoveredColumn> columns) {
    public DiscoveredTable {
        columns = List.copyOf(columns);
    }

    public String qualifiedName() {
        return schemaName + "." + name;
    }
}
