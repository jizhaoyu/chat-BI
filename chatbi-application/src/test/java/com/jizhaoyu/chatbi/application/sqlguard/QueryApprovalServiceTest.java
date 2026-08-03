package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryApprovalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private final MemoryRepository repository = new MemoryRepository();
    private final QueryApprovalService service = new QueryApprovalService(
            repository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2), "mysql-select-v1");

    @Test
    void storesOnlyTokenHashAndClaimsApprovedQuery() {
        QueryApprovalEnvelope envelope = envelope();

        String approvalId = service.issue(envelope);
        UUID executionId = UUID.randomUUID();
        ApprovedQuery approved = service.claimAndStart(actor(envelope), approvalId, executionId);

        assertThat(approvalId).hasSizeGreaterThanOrEqualTo(40);
        assertThat(repository.hash).hasSize(32);
        assertThat(new String(repository.hash, java.nio.charset.StandardCharsets.US_ASCII))
                .doesNotContain(approvalId);
        assertThat(approved.normalizedSql()).isEqualTo(envelope.normalizedSql());
        assertThat(repository.executionId).isEqualTo(executionId);
        assertThat(approved.toString()).doesNotContain(envelope.normalizedSql());
    }

    @Test
    void rejectsMalformedApprovalBeforeRepositoryLookup() {
        assertThatThrownBy(() -> service.claimAndStart(
                new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Role.ANALYST)),
                "short", UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("APPROVAL_INVALID");
        assertThat(repository.consumed).isFalse();
    }

    private static QueryApprovalEnvelope envelope() {
        return new QueryApprovalEnvelope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, 2, "mysql-select-v1", "policy", "SELECT id LIMIT 10",
                "sql-hash", "parameter-hash", 10, 30, List.of(), NOW.plusSeconds(120));
    }

    private static UserPrincipal actor(QueryApprovalEnvelope envelope) {
        return new UserPrincipal(envelope.userId(), envelope.tenantId(), Set.of(Role.ANALYST));
    }

    private static final class MemoryRepository implements QueryApprovalRepository {
        private byte[] hash;
        private QueryApprovalEnvelope envelope;
        private boolean consumed;
        private UUID executionId;

        @Override
        public void save(byte[] tokenHash, QueryApprovalEnvelope envelope) {
            this.hash = tokenHash.clone();
            this.envelope = envelope;
        }

        @Override
        public QueryApprovalEnvelope consumeAndStart(
                byte[] tokenHash, UserPrincipal actor, Instant now, String currentRuleVersion, UUID executionId) {
            consumed = true;
            this.executionId = executionId;
            assertThat(tokenHash).containsExactly(hash);
            assertThat(actor.tenantId()).isEqualTo(envelope.tenantId());
            assertThat(currentRuleVersion).isEqualTo(envelope.ruleVersion());
            return envelope;
        }
    }
}
