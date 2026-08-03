package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class QueryExecutionRecoveryScheduler {
    private final QueryExecutionRecoveryService recovery;

    public QueryExecutionRecoveryScheduler(QueryExecutionRecoveryService recovery) {
        this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${app.query.recovery.fixed-delay-ms:30000}")
    public void recoverStaleExecutions() {
        recovery.recoverNow();
    }
}
