package com.jizhaoyu.chatbi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.platform.datasource")
public record PlatformDataSourceProperties(String url, String username, String password, int maximumPoolSize) {
    @Override
    public String toString() {
        return "PlatformDataSourceProperties[url=<redacted>, username=<redacted>, password=<redacted>, maximumPoolSize=" + maximumPoolSize + "]";
    }
}
