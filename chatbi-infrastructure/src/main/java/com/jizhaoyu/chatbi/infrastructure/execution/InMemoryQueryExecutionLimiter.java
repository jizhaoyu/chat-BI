package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionLimiter;
import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryQueryExecutionLimiter implements QueryExecutionLimiter {
    private final QueryConcurrencyProperties limits;
    private final Semaphore global;
    private final ConcurrentHashMap<UUID, Slot> tenants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScopedKey, Slot> dataSources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScopedKey, Slot> users = new ConcurrentHashMap<>();

    public InMemoryQueryExecutionLimiter(QueryConcurrencyProperties limits) {
        this.limits = limits;
        this.global = semaphore(limits.global());
    }

    @Override
    public Optional<Permit> tryAcquire(ApprovedQuery query) {
        List<Runnable> releases = new ArrayList<>(4);
        if (!acquire(global, releases)
                || !acquire(tenants, query.tenantId(), limits.tenant(), releases)
                || !acquire(dataSources, new ScopedKey(query.tenantId(), query.dataSourceId()),
                        limits.dataSource(), releases)
                || !acquire(users, new ScopedKey(query.tenantId(), query.userId()),
                        limits.user(), releases)) {
            release(releases);
            return Optional.empty();
        }
        return Optional.of(new AcquiredPermit(releases));
    }

    private static boolean acquire(Semaphore semaphore, List<Runnable> releases) {
        if (!semaphore.tryAcquire()) {
            return false;
        }
        releases.add(semaphore::release);
        return true;
    }

    private static <K> boolean acquire(
            ConcurrentHashMap<K, Slot> slots, K key, int limit, List<Runnable> releases) {
        AtomicReference<Slot> retained = new AtomicReference<>();
        slots.compute(key, (ignored, current) -> {
            Slot slot = current == null ? new Slot(semaphore(limit)) : current;
            slot.references++;
            retained.set(slot);
            return slot;
        });
        Slot slot = retained.get();
        if (!slot.semaphore.tryAcquire()) {
            releaseReference(slots, key, slot);
            return false;
        }
        releases.add(() -> {
            slot.semaphore.release();
            releaseReference(slots, key, slot);
        });
        return true;
    }

    private static <K> void releaseReference(ConcurrentHashMap<K, Slot> slots, K key, Slot retained) {
        slots.computeIfPresent(key, (ignored, current) -> {
            if (current != retained) {
                return current;
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static void release(List<Runnable> releases) {
        for (int index = releases.size() - 1; index >= 0; index--) {
            releases.get(index).run();
        }
    }

    private static Semaphore semaphore(int permits) {
        return new Semaphore(permits, true);
    }

    private record ScopedKey(UUID tenantId, UUID resourceId) {
    }

    private static final class Slot {
        private final Semaphore semaphore;
        private int references;

        private Slot(Semaphore semaphore) {
            this.semaphore = semaphore;
        }
    }

    private static final class AcquiredPermit implements Permit {
        private final List<Runnable> releases;
        private final AtomicBoolean closed = new AtomicBoolean();

        private AcquiredPermit(List<Runnable> releases) {
            this.releases = List.copyOf(releases);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(releases);
            }
        }
    }
}
