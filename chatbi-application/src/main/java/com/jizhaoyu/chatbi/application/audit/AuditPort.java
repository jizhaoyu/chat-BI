package com.jizhaoyu.chatbi.application.audit;

public interface AuditPort {
    void append(AuditEvent event);
}
