package com.jizhaoyu.chatbi.infrastructure.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-identity")
public record BootstrapIdentityProperties(boolean enabled, String tenantName, String username, String password) {
    @Override
    public String toString() {
        return "BootstrapIdentityProperties[enabled=" + enabled + ", tenantName=" + tenantName + ", username=<redacted>, password=<redacted>]";
    }
}
