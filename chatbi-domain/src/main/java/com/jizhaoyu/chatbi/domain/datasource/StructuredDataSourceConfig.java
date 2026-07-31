package com.jizhaoyu.chatbi.domain.datasource;

import java.util.Objects;
import java.util.regex.Pattern;

public record StructuredDataSourceConfig(
        String host,
        int port,
        String database,
        String username,
        String credentialRef,
        DataSourceDialect dialect,
        int maxRows,
        int timeoutSeconds) {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]{0,252}");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern CREDENTIAL_REF = Pattern.compile("[A-Za-z0-9._:/-]{1,128}");

    public StructuredDataSourceConfig {
        host = require(host, "host");
        database = require(database, "database");
        username = require(username, "username");
        credentialRef = require(credentialRef, "credentialRef");
        Objects.requireNonNull(dialect, "dialect");
        if (!HOST.matcher(host).matches() || isBlockedHost(host)) {
            throw new IllegalArgumentException("DATASOURCE_HOST_NOT_ALLOWED");
        }
        if (!DATABASE.matcher(database).matches()) {
            throw new IllegalArgumentException("DATASOURCE_DATABASE_NOT_ALLOWED");
        }
        if (!CREDENTIAL_REF.matcher(credentialRef).matches()) {
            throw new IllegalArgumentException("DATASOURCE_CREDENTIAL_REF_NOT_ALLOWED");
        }
        if (port < 1 || port > 65535 || maxRows < 1 || maxRows > 1_000_000 || timeoutSeconds < 1 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("DATASOURCE_RESOURCE_POLICY_NOT_ALLOWED");
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DATASOURCE_" + field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    private static boolean isBlockedHost(String value) {
        String host = value.toLowerCase();
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                || host.equals("0.0.0.0") || host.startsWith("169.254.") || host.startsWith("10.")
                || host.startsWith("192.168.") || host.startsWith("172.16.");
    }
}
