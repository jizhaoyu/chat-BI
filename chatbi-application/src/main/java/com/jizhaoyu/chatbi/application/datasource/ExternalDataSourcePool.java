package com.jizhaoyu.chatbi.application.datasource;

import javax.sql.DataSource;
import java.util.UUID;

public interface ExternalDataSourcePool extends AutoCloseable {
    DataSource getOrCreate(ExternalDataSourceConnectionSpec connectionSpec);

    void destroy(UUID tenantId, UUID dataSourceId);

    @Override
    void close();
}
