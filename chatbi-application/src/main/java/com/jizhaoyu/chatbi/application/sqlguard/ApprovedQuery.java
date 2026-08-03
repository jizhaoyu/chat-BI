package com.jizhaoyu.chatbi.application.sqlguard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApprovedQuery {
    private final QueryApprovalEnvelope envelope;

    ApprovedQuery(QueryApprovalEnvelope envelope) {
        this.envelope = envelope;
    }

    public UUID approvalRecordId() { return envelope.id(); }
    public UUID tenantId() { return envelope.tenantId(); }
    public UUID userId() { return envelope.userId(); }
    public UUID dataSourceId() { return envelope.dataSourceId(); }
    public UUID metadataSnapshotId() { return envelope.metadataSnapshotId(); }
    public String normalizedSql() { return envelope.normalizedSql(); }
    public int maximumRows() { return envelope.maximumRows(); }
    public int timeoutSeconds() { return envelope.timeoutSeconds(); }
    public List<SqlObjectReference> references() { return envelope.references(); }
    public Instant expiresAt() { return envelope.expiresAt(); }

    @Override
    public String toString() {
        return "ApprovedQuery[approvalRecordId=" + approvalRecordId()
                + ", tenantId=" + tenantId() + ", userId=" + userId()
                + ", dataSourceId=" + dataSourceId() + ", maximumRows=" + maximumRows() + "]";
    }
}
