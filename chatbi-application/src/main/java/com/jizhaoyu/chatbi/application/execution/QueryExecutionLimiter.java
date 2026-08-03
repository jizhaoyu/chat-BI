package com.jizhaoyu.chatbi.application.execution;

import com.jizhaoyu.chatbi.application.sqlguard.ApprovedQuery;

import java.util.Optional;

public interface QueryExecutionLimiter {
    Optional<Permit> tryAcquire(ApprovedQuery query);

    interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
