package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;

import java.util.UUID;

public record DataSourceView(
        UUID id,
        String name,
        String host,
        int port,
        String database,
        String username,
        String credentialRef,
        DataSourceDialect dialect,
        DataSourceStatus status,
        int maxRows,
        int timeoutSeconds,
        long version,
        long authorizationVersion) {
}
