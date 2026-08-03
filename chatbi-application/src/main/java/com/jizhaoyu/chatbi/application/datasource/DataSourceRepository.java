package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataSourceRepository {
    DataSourceView save(UUID tenantId, UUID id, DataSourceCommand command, String credentialRef);

    List<DataSourceView> findAllByTenant(UUID tenantId);

    Optional<DataSourceView> findByTenantAndId(UUID tenantId, UUID id);

    DataSourceView update(UUID tenantId, UUID id, DataSourceCommand command, String credentialRef);

    DataSourceView transitionStatus(
            UUID tenantId, UUID id, DataSourceStatus expectedStatus, DataSourceStatus targetStatus);

    void disable(UUID tenantId, UUID id);
}
