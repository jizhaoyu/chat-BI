package com.jizhaoyu.chatbi.domain.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredDataSourceConfigTest {
    @Test
    void rejectsArbitraryJdbcLikeHostValues() {
        assertThatThrownBy(() -> new StructuredDataSourceConfig(
                "jdbc:mysql://internal", 3306, "sales", "reader", "secret/ref", DataSourceDialect.MYSQL, 1000, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATASOURCE_HOST_NOT_ALLOWED");
    }

    @Test
    void rejectsPrivateNetworkDestinationByDefault() {
        assertThatThrownBy(() -> new StructuredDataSourceConfig(
                "192.168.1.10", 3306, "sales", "reader", "secret/ref", DataSourceDialect.MYSQL, 1000, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATASOURCE_HOST_NOT_ALLOWED");
    }
}
