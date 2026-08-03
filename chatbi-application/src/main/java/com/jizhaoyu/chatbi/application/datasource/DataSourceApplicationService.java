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
    private static final String VALIDATION_CREDENTIAL_REF = "credential/server-managed";
    private final DataSourceRepository repository;
    private final AuditPort auditPort;
    private final CredentialVaultPort credentialVault;
    private final ExternalDataSourcePool externalPools;

    public DataSourceApplicationService(DataSourceRepository repository, AuditPort auditPort,
                                        CredentialVaultPort credentialVault,
                                        ExternalDataSourcePool externalPools) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.credentialVault = credentialVault;
        this.externalPools = externalPools;
    }

    @Transactional
    public DataSourceView create(UserPrincipal principal, DataSourceCommand command) {
        requireDataAdmin(principal);
        validateName(command.name());
        validateSecret(command.password());
        validateConfig(command);
        UUID id = UUID.randomUUID();
        String credentialRef = credentialVault.store(principal.tenantId(), id, command.password());
        DataSourceView view = repository.save(principal.tenantId(), id, command, credentialRef);
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
        validateSecret(command.password());
        validateConfig(command);
        DataSourceView current = repository.findByTenantAndId(principal.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (current.status() != DataSourceStatus.DRAFT) {
            throw new IllegalStateException("DATASOURCE_CONFIG_UPDATE_NOT_ALLOWED");
        }
        String credentialRef = credentialVault.store(principal.tenantId(), id, command.password());
        DataSourceView updated = repository.update(principal.tenantId(), id, command, credentialRef);
        auditPort.append(new AuditEvent(principal.tenantId(), principal.userId(), "DATASOURCE_UPDATED",
                "DATA_SOURCE", id, "ALLOWED", "{}"));
        return updated;
    }

    public DataSourceView disable(UserPrincipal principal, UUID id) {
        requireDataAdmin(principal);
        DataSourceView current = repository.findByTenantAndId(principal.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (!com.jizhaoyu.chatbi.domain.datasource.DataSourceStateMachine.canTransition(current.status(), DataSourceStatus.DISABLED)) {
            throw new IllegalStateException("DATASOURCE_STATE_TRANSITION_NOT_ALLOWED");
        }
        DataSourceView updated = repository.transitionStatus(
                principal.tenantId(), id, current.status(), DataSourceStatus.DISABLED);
        externalPools.destroy(principal.tenantId(), id);
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

    private static void validateSecret(String secret) {
        if (secret == null || secret.length() < 12 || secret.length() > 1024) {
            throw new IllegalArgumentException("DATASOURCE_PASSWORD_INVALID");
        }
    }

    private static void validateConfig(DataSourceCommand command) {
        new StructuredDataSourceConfig(command.host(), command.port(), command.database(), command.username(),
                VALIDATION_CREDENTIAL_REF, command.dialect(), command.maxRows(), command.timeoutSeconds());
    }
}
