package com.jizhaoyu.chatbi.infrastructure.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.query.concurrency")
public record QueryConcurrencyProperties(int global, int tenant, int dataSource, int user) {
    public QueryConcurrencyProperties {
        if (global < 1 || tenant < 1 || dataSource < 1 || user < 1) {
            throw new IllegalArgumentException("QUERY_CONCURRENCY_LIMIT_INVALID");
        }
    }
}
