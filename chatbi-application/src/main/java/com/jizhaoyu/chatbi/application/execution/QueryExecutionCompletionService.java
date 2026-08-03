package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class QueryExecutionCompletionService {
    private final QueryExecutionRepository executions;
    private final AuditPort audit;
    private final Clock clock;

    public QueryExecutionCompletionService(QueryExecutionRepository executions, AuditPort audit, Clock clock) {
        this.executions = executions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void complete(
            UserPrincipal actor,
            PreparedQueryExecution prepared,
            QueryExecutionStatus status,
            int rowCount,
            boolean truncated,
            String errorCode,
            String digest) {
        Instant completedAt = clock.instant();
        long duration = Math.max(0, Duration.between(prepared.startedAt(), completedAt).toMillis());
        executions.complete(actor.tenantId(), prepared.executionId(), status, completedAt, duration,
                rowCount, truncated, errorCode, digest);
        String decision = status == QueryExecutionStatus.SUCCEEDED || status == QueryExecutionStatus.TRUNCATED
                ? "ALLOWED" : "DENIED";
        audit.append(new AuditEvent(actor.tenantId(), actor.userId(), "QUERY_EXECUTION_" + status.name(),
                "DATA_SOURCE", prepared.query().dataSourceId(), decision,
                QueryExecutionPreparationService.detail(prepared.executionId(), errorCode)));
    }
}
