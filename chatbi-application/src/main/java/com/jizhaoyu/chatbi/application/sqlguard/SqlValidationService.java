package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.application.catalog.CatalogPermissionRepository;
import com.jizhaoyu.chatbi.application.catalog.CatalogSnapshotRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.domain.catalog.CatalogAuthorization;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SqlValidationService {
    public static final String RULE_VERSION = "mysql-select-v1";
    private static final String EMPTY_PARAMETER_HASH = sha256("[]");

    private final DataSourceRepository dataSources;
    private final CatalogSnapshotRepository snapshots;
    private final CatalogPermissionRepository permissions;
    private final SqlGuardPort sqlGuard;
    private final QueryApprovalService approvals;
    private final AuditPort audit;
    private final Clock clock;

    public SqlValidationService(
            DataSourceRepository dataSources,
            CatalogSnapshotRepository snapshots,
            CatalogPermissionRepository permissions,
            SqlGuardPort sqlGuard,
            QueryApprovalService approvals,
            AuditPort audit,
            Clock clock) {
        this.dataSources = dataSources;
        this.snapshots = snapshots;
        this.permissions = permissions;
        this.sqlGuard = sqlGuard;
        this.approvals = approvals;
        this.audit = audit;
        this.clock = clock;
    }

    public SqlValidationApproval validate(UserPrincipal actor, UUID dataSourceId, String candidateSql) {
        requireAnalyst(actor);
        DataSourceView source = dataSources.findByTenantAndId(actor.tenantId(), dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (source.status() != DataSourceStatus.READY || source.dialect() != DataSourceDialect.MYSQL) {
            throw new IllegalStateException("DATASOURCE_NOT_READY");
        }
        CatalogSnapshot active = snapshots.findActive(actor.tenantId(), dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("CATALOG_ACTIVE_SNAPSHOT_NOT_FOUND"));
        CatalogSnapshot authorized = actor.has(Role.DATA_ADMIN) ? active : authorize(actor, active);
        try {
            SqlGuardResult result = sqlGuard.validate(candidateSql, new SqlGuardContext(authorized, source.maxRows()));
            Instant expiresAt = clock.instant().plus(approvals.lifetime());
            String policyHash = sha256(RULE_VERSION + ":" + source.maxRows() + ":" + source.timeoutSeconds());
            QueryApprovalEnvelope envelope = new QueryApprovalEnvelope(
                    UUID.randomUUID(), actor.tenantId(), actor.userId(), dataSourceId, active.id(),
                    source.version(), source.authorizationVersion(), RULE_VERSION, policyHash,
                    result.normalizedSql(), sha256(result.normalizedSql()), EMPTY_PARAMETER_HASH,
                    result.effectiveLimit(), source.timeoutSeconds(), result.references(), expiresAt);
            String approvalId = approvals.issue(envelope);
            appendAudit(actor, dataSourceId, "SQL_VALIDATION_APPROVED", "ALLOWED", result.references().size());
            return new SqlValidationApproval(approvalId, result.normalizedSql(), result.effectiveLimit(),
                    result.references(), expiresAt);
        } catch (RuntimeException failure) {
            appendAudit(actor, dataSourceId, "SQL_VALIDATION_REJECTED", "DENIED", 0);
            throw failure;
        }
    }

    private CatalogSnapshot authorize(UserPrincipal actor, CatalogSnapshot active) {
        CatalogAuthorization authorization = new CatalogAuthorization(
                actor.tenantId(), actor.userId(), active.dataSourceId(),
                permissions.findGranted(actor.tenantId(), actor.userId(), active.dataSourceId()));
        List<CatalogTable> tables = active.tables().stream()
                .filter(authorization::canAccess)
                .map(table -> new CatalogTable(table.id(), table.tenantId(), table.snapshotId(), table.schemaName(),
                        table.name(), table.comment(), table.semantic(), table.enabled(), table.columns().stream()
                                .filter(column -> authorization.canAccess(table, column)).toList()))
                .toList();
        Set<UUID> tableIds = tables.stream().map(CatalogTable::id).collect(Collectors.toSet());
        return new CatalogSnapshot(active.id(), active.tenantId(), active.dataSourceId(), active.version(),
                active.status(), tables, active.relations().stream()
                        .filter(relation -> tableIds.contains(relation.sourceTableId())
                                && tableIds.contains(relation.targetTableId()))
                        .toList(), active.createdAt(), active.activatedAt());
    }

    private static void requireAnalyst(UserPrincipal actor) {
        if (actor == null || (!actor.has(Role.ANALYST) && !actor.has(Role.DATA_ADMIN))) {
            throw new SecurityException("FORBIDDEN");
        }
    }

    private void appendAudit(UserPrincipal actor, UUID dataSourceId, String action, String decision, int references) {
        audit.append(new AuditEvent(actor.tenantId(), actor.userId(), action, "DATA_SOURCE", dataSourceId,
                decision, "{\"referenceCount\":" + references + "}"));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible);
        }
    }
}
