package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionLimiter;
import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalEnvelope;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalRepository;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalService;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryQueryExecutionLimiterTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void enforcesDatasourceAndUserScopesAndReleasesIdempotently() {
        InMemoryQueryExecutionLimiter limiter = new InMemoryQueryExecutionLimiter(
                new QueryConcurrencyProperties(4, 4, 1, 1));
        UUID tenant = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        ApprovedQuery first = approved(tenant, user, source);
        ApprovedQuery sameSource = approved(tenant, UUID.randomUUID(), source);
        ApprovedQuery sameUser = approved(tenant, user, UUID.randomUUID());

        QueryExecutionLimiter.Permit permit = limiter.tryAcquire(first).orElseThrow();

        assertThat(limiter.tryAcquire(sameSource)).isEmpty();
        assertThat(limiter.tryAcquire(sameUser)).isEmpty();
        permit.close();
        permit.close();
        assertThat(limiter.tryAcquire(sameSource)).isPresent().get().satisfies(QueryExecutionLimiter.Permit::close);
        assertThat(limiter.tryAcquire(sameUser)).isPresent().get().satisfies(QueryExecutionLimiter.Permit::close);
    }

    @Test
    void enforcesGlobalAndTenantScopesIndependently() {
        InMemoryQueryExecutionLimiter limiter = new InMemoryQueryExecutionLimiter(
                new QueryConcurrencyProperties(2, 1, 2, 2));
        UUID tenant = UUID.randomUUID();
        QueryExecutionLimiter.Permit first = limiter.tryAcquire(
                approved(tenant, UUID.randomUUID(), UUID.randomUUID())).orElseThrow();

        assertThat(limiter.tryAcquire(approved(tenant, UUID.randomUUID(), UUID.randomUUID()))).isEmpty();
        QueryExecutionLimiter.Permit otherTenant = limiter.tryAcquire(
                approved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).orElseThrow();
        assertThat(limiter.tryAcquire(
                approved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))).isEmpty();

        first.close();
        otherTenant.close();
    }

    @Test
    void neverExceedsDatasourceLimitUnderConcurrentContention() throws Exception {
        int limit = 3;
        int contenders = 12;
        InMemoryQueryExecutionLimiter limiter = new InMemoryQueryExecutionLimiter(
                new QueryConcurrencyProperties(contenders, contenders, limit, contenders));
        UUID tenant = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(contenders);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger acquired = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(contenders)) {
            List<? extends Future<?>> futures = java.util.stream.IntStream.range(0, contenders)
                    .mapToObj(index -> executor.submit(() -> {
                        await(start);
                        var permit = limiter.tryAcquire(approved(tenant, UUID.randomUUID(), source));
                        attempted.countDown();
                        permit.ifPresent(acquiredPermit -> {
                            int current = active.incrementAndGet();
                            maximumActive.accumulateAndGet(current, Math::max);
                            acquired.incrementAndGet();
                            await(release);
                            active.decrementAndGet();
                            acquiredPermit.close();
                        });
                    })).toList();
            start.countDown();
            await(attempted);
            assertThat(acquired).hasValue(limit);
            release.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertThat(acquired).hasValue(limit);
        assertThat(maximumActive).hasValue(limit);
        assertThat(limiter.tryAcquire(approved(tenant, UUID.randomUUID(), source))).isPresent()
                .get().satisfies(QueryExecutionLimiter.Permit::close);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrency test timed out");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static ApprovedQuery approved(UUID tenant, UUID user, UUID source) {
        QueryApprovalEnvelope envelope = new QueryApprovalEnvelope(
                UUID.randomUUID(), tenant, user, source, UUID.randomUUID(), 1, 1,
                "mysql-select-v1", "policy", "SELECT id FROM fact_order LIMIT 1",
                "sql", "parameters", 1, 30, List.of(), NOW.plusSeconds(120));
        MemoryRepository repository = new MemoryRepository(envelope);
        QueryApprovalService approvals = new QueryApprovalService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2), "mysql-select-v1");
        String token = issue(approvals, envelope);
        return approvals.claimAndStart(
                new UserPrincipal(user, tenant, Set.of(Role.ANALYST)), token, UUID.randomUUID());
    }

    private static String issue(QueryApprovalService service, QueryApprovalEnvelope envelope) {
        try {
            Method method = QueryApprovalService.class.getDeclaredMethod("issue", QueryApprovalEnvelope.class);
            method.setAccessible(true);
            return (String) method.invoke(service, envelope);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private record MemoryRepository(QueryApprovalEnvelope envelope) implements QueryApprovalRepository {
        @Override
        public void save(byte[] tokenHash, QueryApprovalEnvelope stored) {
        }

        @Override
        public QueryApprovalEnvelope consumeAndStart(
                byte[] tokenHash, UserPrincipal actor, Instant now, String ruleVersion, UUID executionId) {
            return envelope;
        }
    }
}
