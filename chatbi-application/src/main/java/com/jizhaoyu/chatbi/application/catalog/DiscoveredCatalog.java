package com.jizhaoyu.chatbi.application.catalog;

import java.util.List;

public record DiscoveredCatalog(List<DiscoveredTable> tables, List<DiscoveredRelation> relations) {
    public DiscoveredCatalog {
        tables = List.copyOf(tables);
        relations = List.copyOf(relations);
    }
}
