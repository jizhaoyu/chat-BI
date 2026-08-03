package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class QueryExecutionRecoveryService {
    static final String ERROR_CODE = "EXECUTION_ABANDONED";
    private final QueryExecutionRepository executions;
    private final AuditPort audit;
    private final Clock clock;
    private final Duration staleAfter;

    public QueryExecutionRecoveryService(
            QueryExecutionRepository executions, AuditPort audit, Clock clock, Duration staleAfter) {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("QUERY_RECOVERY_THRESHOLD_INVALID");
        }
        this.executions = executions;
        this.audit = audit;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public int recoverNow() {
        Instant completedAt = clock.instant();
        Instant startedBefore = completedAt.minus(staleAfter);
        var recovered = executions.recoverStale(completedAt, startedBefore, ERROR_CODE);
        recovered.forEach(execution -> audit.append(new AuditEvent(
                execution.tenantId(), execution.executorUserId(), "QUERY_EXECUTION_FAILED",
                "DATA_SOURCE", execution.dataSourceId(), "DENIED",
                QueryExecutionPreparationService.detail(execution.executionId(), ERROR_CODE))));
        return recovered.size();
    }
}
