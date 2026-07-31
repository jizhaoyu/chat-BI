package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.datasource.StructuredDataSourceConfig;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public class DataSourceApplicationService {
    private final DataSourceRepository repository;
    private final AuditPort auditPort;

    public DataSourceApplicationService(DataSourceRepository repository, AuditPort auditPort) {
        this.repository = repository;
        this.auditPort = auditPort;
    }

    @Transactional
    public DataSourceView create(UserPrincipal principal, DataSourceCommand command) {
        requireDataAdmin(principal);
        validateName(command.name());
        new StructuredDataSourceConfig(command.host(), command.port(), command.database(), command.username(),
                command.credentialRef(), command.dialect(), command.maxRows(), command.timeoutSeconds());
        DataSourceView view = repository.save(principal.tenantId(), command);
        auditPort.append(new AuditEvent(principal.tenantId(), principal.userId(), "DATASOURCE_CREATED",
                "DATA_SOURCE", view.id(), "ALLOWED", "{}"));
        return view;
    }

    @Transactional(readOnly = true)
    public List<DataSourceView> list(UserPrincipal principal) {
        if (!principal.has(Role.DATA_ADMIN) && !principal.has(Role.SYSTEM_ADMIN)) {
            throw new SecurityException("FORBIDDEN");
        }
        return repository.findAllByTenant(principal.tenantId());
    }

    @Transactional(readOnly = true)
    public DataSourceView get(UserPrincipal principal, UUID id) {
        if (!principal.has(Role.DATA_ADMIN) && !principal.has(Role.SYSTEM_ADMIN)) {
            throw new SecurityException("FORBIDDEN");
        }
        return repository.findByTenantAndId(principal.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
    }

    @Transactional
    public DataSourceView update(UserPrincipal principal, UUID id, DataSourceCommand command) {
        requireDataAdmin(principal);
        validateName(command.name());
        new StructuredDataSourceConfig(command.host(), command.port(), command.database(), command.username(),
                command.credentialRef(), command.dialect(), command.maxRows(), command.timeoutSeconds());
        DataSourceView current = repository.findByTenantAndId(principal.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (current.status() != DataSourceStatus.DRAFT) {
            throw new IllegalStateException("DATASOURCE_CONFIG_UPDATE_NOT_ALLOWED");
        }
        DataSourceView updated = repository.update(principal.tenantId(), id, command);
        auditPort.append(new AuditEvent(principal.tenantId(), principal.userId(), "DATASOURCE_UPDATED",
                "DATA_SOURCE", id, "ALLOWED", "{}"));
        return updated;
    }

    @Transactional
    public DataSourceView disable(UserPrincipal principal, UUID id) {
        requireDataAdmin(principal);
        DataSourceView current = repository.findByTenantAndId(principal.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (!com.jizhaoyu.chatbi.domain.datasource.DataSourceStateMachine.canTransition(current.status(), DataSourceStatus.DISABLED)) {
            throw new IllegalStateException("DATASOURCE_STATE_TRANSITION_NOT_ALLOWED");
        }
        DataSourceView updated = repository.updateStatus(principal.tenantId(), id, DataSourceStatus.DISABLED);
        auditPort.append(new AuditEvent(principal.tenantId(), principal.userId(), "DATASOURCE_DISABLED",
                "DATA_SOURCE", id, "ALLOWED", "{}"));
        return updated;
    }

    private static void requireDataAdmin(UserPrincipal principal) {
        if (!principal.has(Role.DATA_ADMIN)) {
            throw new SecurityException("FORBIDDEN");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("DATASOURCE_NAME_INVALID");
        }
    }
}
