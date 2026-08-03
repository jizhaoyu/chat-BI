package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalService;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class QueryExecutionPreparationService {
    private final QueryApprovalService approvals;
    private final AuditPort audit;
    private final Clock clock;

    public QueryExecutionPreparationService(QueryApprovalService approvals, AuditPort audit, Clock clock) {
        this.approvals = approvals;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PreparedQueryExecution prepare(UserPrincipal actor, String approvalId) {
        UUID executionId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        ApprovedQuery query = approvals.claimAndStart(actor, approvalId, executionId);
        audit.append(new AuditEvent(actor.tenantId(), actor.userId(), "QUERY_EXECUTION_STARTED",
                "DATA_SOURCE", query.dataSourceId(), "ALLOWED", detail(executionId, "")));
        return new PreparedQueryExecution(executionId, query, startedAt);
    }

    static String detail(UUID executionId, String errorCode) {
        return "{\"executionId\":\"" + executionId + "\",\"errorCode\":\"" + errorCode + "\"}";
    }
}
