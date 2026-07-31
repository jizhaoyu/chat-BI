package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;

public record DataSourceCommand(
        String name,
        String host,
        int port,
        String database,
        String username,
        String credentialRef,
        DataSourceDialect dialect,
        int maxRows,
        int timeoutSeconds) {
}
