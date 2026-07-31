package com.jizhaoyu.chatbi.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(PlatformDataSourceProperties.class)
public class PlatformDatabaseConfiguration {
    @Bean(name = "platformDataSource", destroyMethod = "close")
    @Primary
    DataSource platformDataSource(PlatformDataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(properties.maximumPoolSize());
        config.setPoolName("chatbi-platform");
        return new HikariDataSource(config);
    }

    @Bean(name = "platformJdbcTemplate")
    @Primary
    JdbcTemplate platformJdbcTemplate(@Qualifier("platformDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "platformTransactionManager")
    @Primary
    PlatformTransactionManager platformTransactionManager(@Qualifier("platformDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean(initMethod = "migrate")
    Flyway platformFlyway(@Qualifier("platformDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/platform").load();
    }
}
