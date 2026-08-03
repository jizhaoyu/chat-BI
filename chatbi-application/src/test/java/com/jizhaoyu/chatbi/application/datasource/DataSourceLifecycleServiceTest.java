package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceLifecycleServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final MemoryRepository repository = new MemoryRepository();
    private final List<AuditEvent> audits = new ArrayList<>();

    @Test
    void movesDraftThroughTestingToReady() {
        repository.view = view(DataSourceStatus.DRAFT);
        DataSourceLifecycleService service = service(spec -> ConnectionProbeResult.success(),
                resolvingVault("reader-secret-password"));

        ConnectionProbeResult result = service.test(admin(), sourceId);

        assertThat(result.successful()).isTrue();
        assertThat(repository.transitions)
                .containsExactly("DRAFT->TESTING", "TESTING->READY");
        assertThat(repository.view.status()).isEqualTo(DataSourceStatus.READY);
        assertThat(audits).singleElement()
                .extracting(AuditEvent::action)
                .isEqualTo("DATASOURCE_TEST_SUCCEEDED");
    }

    @Test
    void movesTestingToFailedWhenProbeRejectsWritableAccount() {
        repository.view = view(DataSourceStatus.DRAFT);
        DataSourceLifecycleService service = service(
                spec -> ConnectionProbeResult.failure(
                        "DATASOURCE_NOT_READ_ONLY", "数据源账号不是严格只读账号"),
                resolvingVault("reader-secret-password"));

        ConnectionProbeResult result = service.test(admin(), sourceId);

        assertThat(result.code()).isEqualTo("DATASOURCE_NOT_READ_ONLY");
        assertThat(repository.transitions)
                .containsExactly("DRAFT->TESTING", "TESTING->FAILED");
        assertThat(audits).singleElement()
                .extracting(AuditEvent::decision)
                .isEqualTo("DENIED");
    }

    @Test
    void movesTestingToFailedWhenCredentialCannotBeResolved() {
        repository.view = view(DataSourceStatus.DRAFT);
        DataSourceLifecycleService service = service(
                spec -> {
                    throw new AssertionError("probe must not run");
                },
                new CredentialVaultPort() {
                    @Override
                    public String store(UUID tenant, UUID source, String secret) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String resolve(UUID tenant, UUID source, String reference) {
                        throw new IllegalStateException("CREDENTIAL_DECRYPTION_FAILED");
                    }
                });

        ConnectionProbeResult result = service.test(admin(), sourceId);

        assertThat(result.code()).isEqualTo("DATASOURCE_UNAVAILABLE");
        assertThat(repository.transitions)
                .containsExactly("DRAFT->TESTING", "TESTING->FAILED");
    }

    @Test
    void rejectsAnalystBeforeReadingDatasourceOrCredential() {
        repository.view = view(DataSourceStatus.DRAFT);
        DataSourceLifecycleService service = service(spec -> ConnectionProbeResult.success(),
                resolvingVault("reader-secret-password"));
        UserPrincipal analyst = new UserPrincipal(userId, tenantId, Set.of(Role.ANALYST));

        assertThatThrownBy(() -> service.test(analyst, sourceId))
                .isInstanceOf(SecurityException.class)
                .hasMessage("FORBIDDEN");
        assertThat(repository.transitions).isEmpty();
        assertThat(audits).isEmpty();
    }

    private DataSourceLifecycleService service(
            ExternalDataSourceConnectionProbe probe, CredentialVaultPort vault) {
        return new DataSourceLifecycleService(repository, vault, probe, audits::add);
    }

    private static CredentialVaultPort resolvingVault(String password) {
        return new CredentialVaultPort() {
            @Override
            public String store(UUID tenant, UUID source, String secret) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String resolve(UUID tenant, UUID source, String reference) {
                return password;
            }
        };
    }

    private UserPrincipal admin() {
        return new UserPrincipal(userId, tenantId, Set.of(Role.DATA_ADMIN));
    }

    private DataSourceView view(DataSourceStatus status) {
        return new DataSourceView(sourceId, "sales", "analytics.example.com", 3306, "sample_sales",
                "reader", "credential/" + UUID.randomUUID(), DataSourceDialect.MYSQL, status, 1000, 30);
    }

    private final class MemoryRepository implements DataSourceRepository {
        private DataSourceView view;
        private final List<String> transitions = new ArrayList<>();

        @Override
        public DataSourceView save(UUID tenant, UUID id, DataSourceCommand command, String credentialRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DataSourceView> findAllByTenant(UUID tenant) {
            return List.of(view);
        }

        @Override
        public Optional<DataSourceView> findByTenantAndId(UUID tenant, UUID id) {
            return tenantId.equals(tenant) && sourceId.equals(id) ? Optional.of(view) : Optional.empty();
        }

        @Override
        public DataSourceView update(UUID tenant, UUID id, DataSourceCommand command, String credentialRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DataSourceView transitionStatus(
                UUID tenant, UUID id, DataSourceStatus expected, DataSourceStatus target) {
            if (!tenantId.equals(tenant) || !sourceId.equals(id) || view.status() != expected) {
                throw new IllegalStateException("DATASOURCE_STATE_CONFLICT");
            }
            transitions.add(expected + "->" + target);
            view = view(target);
            return view;
        }

        @Override
        public void disable(UUID tenant, UUID id) {
            transitionStatus(tenant, id, DataSourceStatus.READY, DataSourceStatus.DISABLED);
        }
    }
}
