package com.jizhaoyu.chatbi.infrastructure.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

@Component
@ConditionalOnBean(name = "analysisJdbcTemplate")
public class SampleAnalysisReadProbe {
    private final JdbcTemplate analysisJdbc;
    private final DataSource platformDataSource;

    public SampleAnalysisReadProbe(
            @Qualifier("analysisJdbcTemplate") JdbcTemplate analysisJdbc,
            @Qualifier("platformDataSource") DataSource platformDataSource) {
        this.analysisJdbc = analysisJdbc;
        this.platformDataSource = platformDataSource;
    }

    public int countOrders() {
        if (TransactionSynchronizationManager.hasResource(platformDataSource)) {
            throw new IllegalStateException("PLATFORM_TRANSACTION_MUST_NOT_WRAP_ANALYSIS_QUERY");
        }
        Integer count = analysisJdbc.queryForObject("SELECT COUNT(*) FROM fact_order", Integer.class);
        return count == null ? 0 : count;
    }
}
