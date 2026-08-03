package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QueryConcurrencyProperties.class)
public class QueryExecutionInfrastructureConfiguration {
    @Bean
    QueryExecutionLimiter queryExecutionLimiter(QueryConcurrencyProperties properties) {
        return new InMemoryQueryExecutionLimiter(properties);
    }
}
