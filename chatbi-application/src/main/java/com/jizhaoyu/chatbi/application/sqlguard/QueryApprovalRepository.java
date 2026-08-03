package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.time.Instant;
import java.util.UUID;

public interface QueryApprovalRepository {
    void save(byte[] tokenHash, QueryApprovalEnvelope envelope);

    QueryApprovalEnvelope consumeAndStart(
            byte[] tokenHash, UserPrincipal actor, Instant now, String currentRuleVersion, UUID executionId);
}
