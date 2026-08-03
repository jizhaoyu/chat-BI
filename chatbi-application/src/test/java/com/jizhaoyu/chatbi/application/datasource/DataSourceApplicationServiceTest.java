package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceApplicationServiceTest {
    private final InMemoryRepository repository = new InMemoryRepository();
    private final List<AuditEvent> auditEvents = new ArrayList<>();
    private final DataSourceApplicationService service = new DataSourceApplicationService(
            repository, auditEvents::add, (tenant, dataSource, secret) -> "credential/" + dataSource,
            new NoopExternalPool());
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final DataSourceCommand command = new DataSourceCommand("sales", "sample-sales.example.com", 3306,
            "sample_sales", "reader", "reader-secret-password", DataSourceDialect.MYSQL, 1000, 30);

    @Test
    void rejectsDatasourceCreationForAnalyst() {
        UserPrincipal analyst = new UserPrincipal(userId, tenantId, java.util.Set.of(Role.ANALYST));
        assertThatThrownBy(() -> service.create(analyst, command))
                .isInstanceOf(SecurityException.class)
                .hasMessage("FORBIDDEN");
        assertThat(repository.saved).isNull();
        assertThat(auditEvents).isEmpty();
    }

    @Test
    void createsDraftDatasourceAndAuditForDataAdmin() {
        UserPrincipal admin = new UserPrincipal(userId, tenantId, java.util.Set.of(Role.DATA_ADMIN));
        DataSourceView created = service.create(admin, command);
        assertThat(created.status()).isEqualTo(DataSourceStatus.DRAFT);
        assertThat(repository.savedTenant).isEqualTo(tenantId);
        assertThat(auditEvents).singleElement().extracting(AuditEvent::action).isEqualTo("DATASOURCE_CREATED");
    }

    @Test
    void rejectsDisableWhenCurrentStateCannotTransition() {
        UUID id = UUID.randomUUID();
        repository.saved = view(id, DataSourceStatus.DRAFT);
        UserPrincipal admin = new UserPrincipal(userId, tenantId, java.util.Set.of(Role.DATA_ADMIN));
        assertThatThrownBy(() -> service.disable(admin, id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DATASOURCE_STATE_TRANSITION_NOT_ALLOWED");
        assertThat(repository.statusUpdates).isZero();
    }

    private DataSourceView view(UUID id, DataSourceStatus status) {
        return new DataSourceView(id, "sales", "sample-sales", 3306, "sample_sales", "reader", "env/sample-reader",
                DataSourceDialect.MYSQL, status, 1000, 30, 0, 0);
    }

    private final class InMemoryRepository implements DataSourceRepository {
        private DataSourceView saved;
        private UUID savedTenant;
        private int statusUpdates;

        public DataSourceView save(UUID tenant, UUID id, DataSourceCommand ignored, String credentialRef) {
            savedTenant = tenant;
            saved = view(id, DataSourceStatus.DRAFT);
            return saved;
        }
        public List<DataSourceView> findAllByTenant(UUID tenant) { return saved == null ? List.of() : List.of(saved); }
        public Optional<DataSourceView> findByTenantAndId(UUID tenant, UUID id) { return saved != null && saved.id().equals(id) ? Optional.of(saved) : Optional.empty(); }
        public DataSourceView update(UUID tenant, UUID id, DataSourceCommand command, String credentialRef) { saved = view(id, DataSourceStatus.DRAFT); return saved; }
        public DataSourceView transitionStatus(UUID tenant, UUID id, DataSourceStatus expected, DataSourceStatus target) {
            if (saved.status() != expected) throw new IllegalStateException("DATASOURCE_STATE_CONFLICT");
            statusUpdates++;
            saved = view(id, target);
            return saved;
        }
        public void disable(UUID tenant, UUID id) {
            transitionStatus(tenant, id, DataSourceStatus.READY, DataSourceStatus.DISABLED);
        }
    }

    private static final class NoopExternalPool implements ExternalDataSourcePool {
        @Override
        public javax.sql.DataSource getOrCreate(ExternalDataSourceConnectionSpec connectionSpec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy(UUID tenantId, UUID dataSourceId) {
        }

        @Override
        public void close() {
        }
    }
}
