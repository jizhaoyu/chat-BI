package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStateMachine;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.util.UUID;

public final class DataSourceLifecycleService {
    private static final int EXTERNAL_POOL_SIZE = 3;
    private static final int MAX_CONNECTION_TIMEOUT_SECONDS = 30;

    private final DataSourceRepository repository;
    private final CredentialVaultPort credentials;
    private final ExternalDataSourceConnectionProbe probe;
    private final AuditPort audit;

    public DataSourceLifecycleService(
            DataSourceRepository repository,
            CredentialVaultPort credentials,
            ExternalDataSourceConnectionProbe probe,
            AuditPort audit) {
        this.repository = repository;
        this.credentials = credentials;
        this.probe = probe;
        this.audit = audit;
    }

    public ConnectionProbeResult test(UserPrincipal actor, UUID dataSourceId) {
        requireDataAdmin(actor);
        DataSourceView source = repository.findByTenantAndId(actor.tenantId(), dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (!DataSourceStateMachine.canTransition(source.status(), DataSourceStatus.TESTING)) {
            throw new IllegalStateException("DATASOURCE_STATE_TRANSITION_NOT_ALLOWED");
        }
        repository.transitionStatus(actor.tenantId(), dataSourceId, source.status(), DataSourceStatus.TESTING);

        ConnectionProbeResult result;
        try {
            String password = credentials.resolve(actor.tenantId(), dataSourceId, source.credentialRef());
            ExternalDataSourceConnectionSpec spec = new ExternalDataSourceConnectionSpec(
                    actor.tenantId(), dataSourceId, source.host(), source.port(), source.database(),
                    source.username(), password, source.dialect(), EXTERNAL_POOL_SIZE,
                    Math.min(source.timeoutSeconds(), MAX_CONNECTION_TIMEOUT_SECONDS));
            result = probe.probe(spec);
        } catch (RuntimeException exception) {
            result = ConnectionProbeResult.failure("DATASOURCE_UNAVAILABLE", "数据源连接失败");
        }

        DataSourceStatus target = result.successful() ? DataSourceStatus.READY : DataSourceStatus.FAILED;
        repository.transitionStatus(actor.tenantId(), dataSourceId, DataSourceStatus.TESTING, target);
        audit.append(new AuditEvent(actor.tenantId(), actor.userId(),
                result.successful() ? "DATASOURCE_TEST_SUCCEEDED" : "DATASOURCE_TEST_FAILED",
                "DATA_SOURCE", dataSourceId, result.successful() ? "ALLOWED" : "DENIED",
                "{\"code\":\"" + result.code() + "\"}"));
        return result;
    }

    private static void requireDataAdmin(UserPrincipal actor) {
        if (actor == null || !actor.has(Role.DATA_ADMIN)) {
            throw new SecurityException("FORBIDDEN");
        }
    }
}
