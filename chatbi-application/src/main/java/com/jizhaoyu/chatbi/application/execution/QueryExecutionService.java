package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

public final class QueryExecutionService {
    private final QueryExecutionPreparationService preparation;
    private final ApprovedQueryExecutor executor;
    private final QueryExecutionCompletionService completion;

    public QueryExecutionService(
            QueryExecutionPreparationService preparation,
            ApprovedQueryExecutor executor,
            QueryExecutionCompletionService completion) {
        this.preparation = preparation;
        this.executor = executor;
        this.completion = completion;
    }

    public QueryExecutionResponse execute(UserPrincipal actor, String approvalId) {
        requireExecutorRole(actor);
        PreparedQueryExecution prepared = preparation.prepare(actor, approvalId);
        QueryExecutionResult result;
        try {
            result = executor.execute(prepared.query());
        } catch (QueryExecutionFailure failure) {
            completion.complete(actor, prepared, failure.status(), 0, false, failure.getMessage(), "");
            throw failure;
        } catch (RuntimeException failure) {
            completion.complete(actor, prepared, QueryExecutionStatus.FAILED, 0, false,
                    "QUERY_EXECUTION_FAILED", "");
            throw new QueryExecutionFailure(QueryExecutionStatus.FAILED, "QUERY_EXECUTION_FAILED");
        }
        QueryExecutionStatus status = result.truncated()
                ? QueryExecutionStatus.TRUNCATED : QueryExecutionStatus.SUCCEEDED;
        completion.complete(actor, prepared, status, result.rows().size(), result.truncated(),
                "", result.resultDigest());
        return new QueryExecutionResponse(prepared.executionId(), status, result);
    }

    private static void requireExecutorRole(UserPrincipal actor) {
        if (actor == null || (!actor.has(Role.ANALYST) && !actor.has(Role.DATA_ADMIN))) {
            throw new SecurityException("FORBIDDEN");
        }
    }
}
