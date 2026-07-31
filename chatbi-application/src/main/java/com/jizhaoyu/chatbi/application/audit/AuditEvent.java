package com.jizhaoyu.chatbi.application.audit;

import java.util.UUID;

public record AuditEvent(
        UUID tenantId,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        String decision,
        String detailJson) {
}
