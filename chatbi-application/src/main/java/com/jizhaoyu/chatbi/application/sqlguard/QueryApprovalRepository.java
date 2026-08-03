package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.time.Instant;

public interface QueryApprovalRepository {
    void save(byte[] tokenHash, QueryApprovalEnvelope envelope);

    QueryApprovalEnvelope consume(byte[] tokenHash, UserPrincipal actor, Instant now, String currentRuleVersion);
}
