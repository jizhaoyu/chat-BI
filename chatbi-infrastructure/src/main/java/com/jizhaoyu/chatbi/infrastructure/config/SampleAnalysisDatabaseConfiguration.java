package com.jizhaoyu.chatbi.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "app.sample-analysis.enabled", havingValue = "true")
@EnableConfigurationProperties(AnalysisDataSourceProperties.class)
public class SampleAnalysisDatabaseConfiguration {
    @Bean(name = "analysisDataSource", destroyMethod = "close")
    DataSource analysisDataSource(AnalysisDataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(properties.maximumPoolSize());
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setPoolName("chatbi-sample-analysis-read-only");
        return new HikariDataSource(config);
    }

    @Bean(name = "analysisJdbcTemplate")
    JdbcTemplate analysisJdbcTemplate(@Qualifier("analysisDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "analysisTransactionManager")
    PlatformTransactionManager analysisTransactionManager(@Qualifier("analysisDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
