package com.jizhaoyu.chatbi.application.catalog;

public record DiscoveredColumn(
        String name,
        String dataType,
        boolean nullable,
        int ordinal,
        String comment) {
}
