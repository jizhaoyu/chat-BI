package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalEnvelope;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalRepository;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalService;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryExecutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private final List<String> events = new ArrayList<>();
    private final QueryApprovalEnvelope envelope = envelope();
    private final UserPrincipal actor = new UserPrincipal(
            envelope.userId(), envelope.tenantId(), Set.of(Role.ANALYST));

    @Test
    void executorPortAcceptsOnlyApprovedQuery() {
        assertThat(ApprovedQueryExecutor.class.getDeclaredMethods()).singleElement().satisfies(method -> {
            assertThat(method.getName()).isEqualTo("execute");
            assertThat(method.getParameterTypes())
                    .containsExactly(com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery.class);
            assertThat(method.getParameterTypes()).doesNotContain(String.class);
        });
    }

    @Test
    void startsExecutionBeforeCallingApprovedExecutorAndPersistsSuccess() {
        MemoryApprovals repository = new MemoryApprovals(false);
        QueryApprovalService approvals = approvals(repository);
        String token = issue(approvals, envelope);
        RecordingExecutions executions = new RecordingExecutions();
        QueryExecutionService service = new QueryExecutionService(
                preparation(approvals),
                permissiveLimiter(),
                query -> {
                    events.add("execute");
                    assertThat(events).containsSubsequence("claim-and-start", "execute");
                    return new QueryExecutionResult(
                            List.of(new QueryResultColumn("id", "BIGINT")),
                            List.of(List.of(1L)), false, "digest");
                },
                completion(executions));

        QueryExecutionResponse response = service.execute(actor, token);

        assertThat(response.status()).isEqualTo(QueryExecutionStatus.SUCCEEDED);
        assertThat(events).containsSubsequence(
                "claim-and-start", "QUERY_EXECUTION_STARTED", "execute", "permit-released",
                "QUERY_EXECUTION_SUCCEEDED");
        assertThat(executions.status).isEqualTo(QueryExecutionStatus.SUCCEEDED);
        assertThat(executions.rowCount).isEqualTo(1);
    }

    @Test
    void doesNotCallExecutorWhenApprovalCannotBeConsumed() {
        MemoryApprovals repository = new MemoryApprovals(true);
        QueryApprovalService approvals = approvals(repository);
        String token = issue(approvals, envelope);
        QueryExecutionService service = new QueryExecutionService(
                preparation(approvals),
                permissiveLimiter(),
                query -> {
                    events.add("execute");
                    throw new AssertionError("executor must not be called");
                },
                completion(new RecordingExecutions()));

        assertThatThrownBy(() -> service.execute(actor, token))
                .isInstanceOf(SecurityException.class).hasMessage("APPROVAL_INVALID");
        assertThat(events).doesNotContain("execute", "QUERY_EXECUTION_STARTED");
    }

    @Test
    void sanitizesUnexpectedExecutorFailureAndKeepsApprovalConsumed() {
        MemoryApprovals repository = new MemoryApprovals(false);
        QueryApprovalService approvals = approvals(repository);
        String token = issue(approvals, envelope);
        RecordingExecutions executions = new RecordingExecutions();
        QueryExecutionService service = new QueryExecutionService(
                new QueryExecutionPreparationService(
                        approvals, event -> events.add(event.detailJson()), Clock.fixed(NOW, ZoneOffset.UTC)),
                permissiveLimiter(),
                query -> { throw new IllegalStateException("jdbc:mysql://secret/ raw SQL password"); },
                new QueryExecutionCompletionService(
                        executions, event -> events.add(event.detailJson()),
                        Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)));

        assertThatThrownBy(() -> service.execute(actor, token))
                .isInstanceOf(QueryExecutionFailure.class).hasMessage("QUERY_EXECUTION_FAILED");
        assertThat(executions.status).isEqualTo(QueryExecutionStatus.FAILED);
        assertThat(executions.errorCode).isEqualTo("QUERY_EXECUTION_FAILED");
        assertThat(events).contains("permit-released");
        assertThat(events).allMatch(detail -> !detail.contains("jdbc:mysql") && !detail.contains("password"));
    }

    @Test
    void recordsFailureWithoutCallingExecutorWhenConcurrencyIsExhausted() {
        QueryApprovalService approvals = approvals(new MemoryApprovals(false));
        String token = issue(approvals, envelope);
        RecordingExecutions executions = new RecordingExecutions();
        QueryExecutionService service = new QueryExecutionService(
                preparation(approvals),
                query -> Optional.empty(),
                query -> { throw new AssertionError("executor must not be called"); },
                completion(executions));

        assertThatThrownBy(() -> service.execute(actor, token))
                .isInstanceOf(QueryExecutionFailure.class)
                .hasMessage("QUERY_CONCURRENCY_EXCEEDED");
        assertThat(executions.status).isEqualTo(QueryExecutionStatus.FAILED);
        assertThat(executions.errorCode).isEqualTo("QUERY_CONCURRENCY_EXCEEDED");
        assertThat(events).doesNotContain("execute");
    }

    private QueryApprovalService approvals(QueryApprovalRepository repository) {
        return new QueryApprovalService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(2), "mysql-select-v1");
    }

    private QueryExecutionPreparationService preparation(QueryApprovalService approvals) {
        return new QueryExecutionPreparationService(
                approvals, event -> events.add(event.action()), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private QueryExecutionCompletionService completion(QueryExecutionRepository executions) {
        return new QueryExecutionCompletionService(
                executions, event -> events.add(event.action()),
                Clock.fixed(NOW.plusMillis(25), ZoneOffset.UTC));
    }

    private QueryExecutionLimiter permissiveLimiter() {
        return query -> Optional.of(() -> events.add("permit-released"));
    }

    private static String issue(QueryApprovalService service, QueryApprovalEnvelope envelope) {
        try {
            var method = QueryApprovalService.class.getDeclaredMethod("issue", QueryApprovalEnvelope.class);
            method.setAccessible(true);
            return (String) method.invoke(service, envelope);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static QueryApprovalEnvelope envelope() {
        return new QueryApprovalEnvelope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, 1, "mysql-select-v1", "policy",
                "SELECT id FROM fact_order LIMIT 10", "sql", "parameters", 10, 30,
                List.of(), NOW.plusSeconds(120));
    }

    private final class MemoryApprovals implements QueryApprovalRepository {
        private final boolean reject;
        private byte[] tokenHash;
        private QueryApprovalEnvelope stored;

        private MemoryApprovals(boolean reject) {
            this.reject = reject;
        }

        @Override
        public void save(byte[] tokenHash, QueryApprovalEnvelope envelope) {
            this.tokenHash = tokenHash.clone();
            this.stored = envelope;
        }

        @Override
        public QueryApprovalEnvelope consumeAndStart(
                byte[] tokenHash, UserPrincipal actor, Instant now, String rule, UUID executionId) {
            if (reject) {
                throw new SecurityException("APPROVAL_INVALID");
            }
            assertThat(tokenHash).containsExactly(this.tokenHash);
            events.add("claim-and-start");
            return stored;
        }
    }

    private static final class RecordingExecutions implements QueryExecutionRepository {
        private QueryExecutionStatus status;
        private int rowCount;
        private String errorCode;

        @Override
        public void complete(
                UUID tenantId, UUID executionId, QueryExecutionStatus status, Instant completedAt,
                long durationMillis, int rowCount, boolean truncated, String errorCode, String resultDigest) {
            this.status = status;
            this.rowCount = rowCount;
            this.errorCode = errorCode;
        }

        @Override
        public List<StaleQueryExecution> recoverStale(
                Instant completedAt, Instant startedBefore, String errorCode) {
            return List.of();
        }
    }
}
