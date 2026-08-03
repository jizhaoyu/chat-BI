package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.application.catalog.CatalogPermissionRepository;
import com.jizhaoyu.chatbi.application.catalog.CatalogSnapshotRepository;
import com.jizhaoyu.chatbi.application.catalog.CatalogSyncAttempt;
import com.jizhaoyu.chatbi.application.datasource.DataSourceCommand;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogObjectType;
import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotStatus;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlValidationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID source = UUID.randomUUID();
    private final UUID snapshot = UUID.randomUUID();
    private final UUID table = UUID.randomUUID();
    private final UUID allowedColumn = UUID.randomUUID();
    private final UUID deniedColumn = UUID.randomUUID();
    private final MemoryApprovalRepository approvals = new MemoryApprovalRepository();

    @Test
    void passesOnlyExplicitlyAuthorizedCatalogToGuardAndIssuesApproval() {
        RecordingGuard guard = new RecordingGuard();
        SqlValidationApproval approval = service(guard).validate(analyst(), source, "SELECT amount FROM fact_order");

        assertThat(guard.context.authorizedCatalog().tables()).singleElement().satisfies(visible ->
                assertThat(visible.columns()).extracting(CatalogColumn::id).containsExactly(allowedColumn));
        assertThat(approval.approvalId()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(approvals.envelope.authorizationVersion()).isEqualTo(7);
        assertThat(approvals.envelope.metadataSnapshotId()).isEqualTo(snapshot);
        assertThat(approvals.envelope.expiresAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void viewerCannotValidateCandidate() {
        assertThatThrownBy(() -> service(new RecordingGuard()).validate(
                new UserPrincipal(user, tenant, Set.of(Role.VIEWER)), source, "SELECT amount FROM fact_order"))
                .isInstanceOf(SecurityException.class).hasMessage("FORBIDDEN");
        assertThat(approvals.envelope).isNull();
    }

    private SqlValidationService service(SqlGuardPort guard) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new SqlValidationService(new MemoryDataSources(), new MemorySnapshots(), new MemoryPermissions(),
                guard, new QueryApprovalService(
                        approvals, clock, Duration.ofMinutes(2), SqlValidationService.RULE_VERSION),
                event -> { }, clock);
    }

    private UserPrincipal analyst() {
        return new UserPrincipal(user, tenant, Set.of(Role.ANALYST));
    }

    private CatalogSnapshot active() {
        CatalogColumn allowed = new CatalogColumn(allowedColumn, tenant, table, "amount", "DECIMAL",
                false, 1, "", SemanticMetadata.physicalOnly(), true);
        CatalogColumn denied = new CatalogColumn(deniedColumn, tenant, table, "cost_price", "DECIMAL",
                false, 2, "", SemanticMetadata.physicalOnly(), true);
        CatalogTable fact = new CatalogTable(table, tenant, snapshot, "sample_sales", "fact_order", "",
                SemanticMetadata.physicalOnly(), true, List.of(allowed, denied));
        return new CatalogSnapshot(snapshot, tenant, source, 3, CatalogSnapshotStatus.ACTIVE,
                List.of(fact), List.of(), NOW, NOW);
    }

    private final class MemoryDataSources implements DataSourceRepository {
        @Override public Optional<DataSourceView> findByTenantAndId(UUID tenantId, UUID id) {
            return tenant.equals(tenantId) && source.equals(id)
                    ? Optional.of(new DataSourceView(source, "sales", "example.com", 3306, "sample_sales",
                    "reader", "credential/test", DataSourceDialect.MYSQL, DataSourceStatus.READY,
                    200, 30, 5, 7)) : Optional.empty();
        }
        @Override public DataSourceView save(UUID t, UUID i, DataSourceCommand c, String r) { throw unsupported(); }
        @Override public List<DataSourceView> findAllByTenant(UUID t) { return List.of(); }
        @Override public DataSourceView update(UUID t, UUID i, DataSourceCommand c, String r) { throw unsupported(); }
        @Override public DataSourceView transitionStatus(UUID t, UUID i, DataSourceStatus e, DataSourceStatus n) { throw unsupported(); }
        @Override public void disable(UUID t, UUID i) { throw unsupported(); }
    }

    private final class MemorySnapshots implements CatalogSnapshotRepository {
        @Override public Optional<CatalogSnapshot> findActive(UUID tenantId, UUID sourceId) { return Optional.of(active()); }
        @Override public CatalogSyncAttempt beginSync(UUID t, UUID s, UUID i, Instant c) { throw unsupported(); }
        @Override public CatalogSnapshot completeAndActivate(CatalogSnapshot s) { throw unsupported(); }
        @Override public void markFailed(UUID t, UUID s, UUID i) { throw unsupported(); }
        @Override public Optional<CatalogSnapshot> findById(UUID t, UUID s, UUID i) { return Optional.empty(); }
        @Override public void updateColumnSemantic(UUID t, UUID s, UUID c, SemanticMetadata m, boolean e) { throw unsupported(); }
    }

    private final class MemoryPermissions implements CatalogPermissionRepository {
        @Override public void replace(UUID t, UUID u, UUID s, List<CatalogPermission> p) { throw unsupported(); }
        @Override public List<CatalogPermission> findGranted(UUID t, UUID u, UUID s) {
            return List.of(
                    new CatalogPermission(UUID.randomUUID(), tenant, "USER", user, source,
                            CatalogObjectType.TABLE, table, ""),
                    new CatalogPermission(UUID.randomUUID(), tenant, "USER", user, source,
                            CatalogObjectType.COLUMN, allowedColumn, ""));
        }
    }

    private static final class RecordingGuard implements SqlGuardPort {
        private SqlGuardContext context;
        @Override public SqlGuardResult validate(String candidateSql, SqlGuardContext context) {
            this.context = context;
            CatalogTable table = context.authorizedCatalog().tables().getFirst();
            CatalogColumn column = table.columns().getFirst();
            return new SqlGuardResult("SELECT amount FROM fact_order LIMIT 200", 200,
                    List.of(new SqlObjectReference(table.id(), column.id(), table.schemaName(), table.name(), column.name())));
        }
    }

    private static final class MemoryApprovalRepository implements QueryApprovalRepository {
        private QueryApprovalEnvelope envelope;
        @Override public void save(byte[] tokenHash, QueryApprovalEnvelope envelope) { this.envelope = envelope; }
        @Override public QueryApprovalEnvelope consume(byte[] tokenHash, UserPrincipal actor, Instant now, String rule) {
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }
}
