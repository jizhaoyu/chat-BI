package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

public final class QueryApprovalService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final QueryApprovalRepository repository;
    private final Clock clock;
    private final Duration lifetime;
    private final String ruleVersion;

    public QueryApprovalService(
            QueryApprovalRepository repository, Clock clock, Duration lifetime, String ruleVersion) {
        this.repository = repository;
        this.clock = clock;
        this.lifetime = lifetime;
        this.ruleVersion = ruleVersion;
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("SQL_APPROVAL_LIFETIME_INVALID");
        }
    }

    String issue(QueryApprovalEnvelope envelope) {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        String approvalId = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        repository.save(hash(approvalId), envelope);
        return approvalId;
    }

    public ApprovedQuery claim(UserPrincipal actor, String approvalId) {
        if (approvalId == null || approvalId.length() < 40 || approvalId.length() > 64) {
            throw new SecurityException("APPROVAL_INVALID");
        }
        QueryApprovalEnvelope envelope = repository.consume(hash(approvalId), actor, clock.instant(), ruleVersion);
        return new ApprovedQuery(envelope);
    }

    Duration lifetime() {
        return lifetime;
    }

    static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible);
        }
    }
}
