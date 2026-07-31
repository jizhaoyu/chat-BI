package com.jizhaoyu.chatbi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.sample-analysis.datasource")
public record AnalysisDataSourceProperties(String url, String username, String password, int maximumPoolSize) {
    @Override
    public String toString() {
        return "AnalysisDataSourceProperties[url=<redacted>, username=<redacted>, password=<redacted>, maximumPoolSize=" + maximumPoolSize + "]";
    }
}
