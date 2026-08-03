package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueryExecutionRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:10:00Z");

    @Test
    void recoversStaleRowsAndWritesSanitizedFailureAudits() {
        UUID executionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        RecordingRepository repository = new RecordingRepository(
                List.of(new StaleQueryExecution(executionId, tenantId, userId, sourceId, NOW.minusSeconds(181))));
        List<AuditEvent> audits = new ArrayList<>();
        QueryExecutionRecoveryService service = new QueryExecutionRecoveryService(
                repository, audits::add, Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofSeconds(180));

        assertThat(service.recoverNow()).isEqualTo(1);
        assertThat(repository.completedAt).isEqualTo(NOW);
        assertThat(repository.startedBefore).isEqualTo(NOW.minusSeconds(180));
        assertThat(audits).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("QUERY_EXECUTION_FAILED");
            assertThat(event.detailJson()).contains("EXECUTION_ABANDONED", executionId.toString())
                    .doesNotContain("jdbc:", "password", "SELECT");
        });
    }

    private static final class RecordingRepository implements QueryExecutionRepository {
        private final List<StaleQueryExecution> stale;
        private Instant completedAt;
        private Instant startedBefore;

        private RecordingRepository(List<StaleQueryExecution> stale) {
            this.stale = stale;
        }

        @Override
        public void complete(UUID tenantId, UUID executionId, QueryExecutionStatus status, Instant completedAt,
                             long durationMillis, int rowCount, boolean truncated, String errorCode,
                             String resultDigest) {
        }

        @Override
        public List<StaleQueryExecution> recoverStale(
                Instant completedAt, Instant startedBefore, String errorCode) {
            this.completedAt = completedAt;
            this.startedBefore = startedBefore;
            return stale;
        }
    }
}
