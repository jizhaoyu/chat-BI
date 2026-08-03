package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;

import java.util.Objects;
import java.util.UUID;

public record ExternalDataSourceConnectionSpec(
        UUID tenantId,
        UUID dataSourceId,
        String host,
        int port,
        String database,
        String username,
        String password,
        DataSourceDialect dialect,
        int maximumPoolSize,
        int connectionTimeoutSeconds) {

    public ExternalDataSourceConnectionSpec {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        host = requireText(host, "host");
        database = requireText(database, "database");
        username = requireText(username, "username");
        password = requireText(password, "password");
        Objects.requireNonNull(dialect, "dialect");
        if (port < 1 || port > 65535 || maximumPoolSize < 1 || connectionTimeoutSeconds < 1) {
            throw new IllegalArgumentException("DATASOURCE_CONNECTION_POLICY_NOT_ALLOWED");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DATASOURCE_" + field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    @Override
    public String toString() {
        return "ExternalDataSourceConnectionSpec[tenantId=" + tenantId + ", dataSourceId=" + dataSourceId
                + ", host=" + host + ", port=" + port + ", database=" + database + ", username=" + username
                + ", password=[REDACTED], dialect=" + dialect + ", maximumPoolSize=" + maximumPoolSize
                + ", connectionTimeoutSeconds=" + connectionTimeoutSeconds + ']';
    }
}
