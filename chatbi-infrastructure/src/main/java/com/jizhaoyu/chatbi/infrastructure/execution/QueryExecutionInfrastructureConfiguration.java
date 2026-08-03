package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(QueryConcurrencyProperties.class)
@EnableScheduling
public class QueryExecutionInfrastructureConfiguration {
    @Bean
    QueryExecutionLimiter queryExecutionLimiter(QueryConcurrencyProperties properties) {
        return new InMemoryQueryExecutionLimiter(properties);
    }
}
