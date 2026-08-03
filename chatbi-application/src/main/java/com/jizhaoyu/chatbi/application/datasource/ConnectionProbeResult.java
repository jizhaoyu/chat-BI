package com.jizhaoyu.chatbi.application.datasource;

import java.util.Objects;

public record ConnectionProbeResult(Status status, String code, String message) {
    public ConnectionProbeResult {
        Objects.requireNonNull(status, "status");
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    public static ConnectionProbeResult success() {
        return new ConnectionProbeResult(Status.SUCCESS, "DATASOURCE_CONNECTION_OK", "连接成功，账号授权为只读");
    }

    public static ConnectionProbeResult failure(String code, String message) {
        return new ConnectionProbeResult(Status.FAILURE, code, message);
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CONNECTION_PROBE_" + field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
