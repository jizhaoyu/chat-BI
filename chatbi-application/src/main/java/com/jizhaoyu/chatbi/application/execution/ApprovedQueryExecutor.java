package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;

public interface ApprovedQueryExecutor {
    QueryExecutionResult execute(ApprovedQuery query);
}
