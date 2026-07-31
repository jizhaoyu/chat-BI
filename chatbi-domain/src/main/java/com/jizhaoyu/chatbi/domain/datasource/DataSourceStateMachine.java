package com.jizhaoyu.chatbi.domain.datasource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class DataSourceStateMachine {
    private static final Map<DataSourceStatus, Set<DataSourceStatus>> ALLOWED = new EnumMap<>(DataSourceStatus.class);

    static {
        ALLOWED.put(DataSourceStatus.DRAFT, EnumSet.of(DataSourceStatus.TESTING));
        ALLOWED.put(DataSourceStatus.TESTING, EnumSet.of(DataSourceStatus.READY, DataSourceStatus.FAILED));
        ALLOWED.put(DataSourceStatus.READY, EnumSet.of(DataSourceStatus.DISABLED, DataSourceStatus.FAILED));
        ALLOWED.put(DataSourceStatus.DISABLED, EnumSet.of(DataSourceStatus.TESTING));
        ALLOWED.put(DataSourceStatus.FAILED, EnumSet.of(DataSourceStatus.TESTING));
    }

    private DataSourceStateMachine() {
    }

    public static boolean canTransition(DataSourceStatus from, DataSourceStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static DataSourceStatus transition(DataSourceStatus from, DataSourceStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("DATA_SOURCE_STATE_TRANSITION_NOT_ALLOWED");
        }
        return to;
    }
}
