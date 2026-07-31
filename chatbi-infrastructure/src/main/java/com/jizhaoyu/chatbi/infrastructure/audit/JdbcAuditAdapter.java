package com.jizhaoyu.chatbi.infrastructure.audit;

import com.jizhaoyu.chatbi.application.audit.AuditEvent;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcAuditAdapter implements AuditPort {
    private final JdbcTemplate jdbc;

    public JdbcAuditAdapter(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEvent event) {
        jdbc.update("""
                INSERT INTO audit_event(id, tenant_id, actor_id, action, resource_type, resource_id, decision, detail_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), event.tenantId().toString(), event.actorId().toString(), event.action(),
                event.resourceType(), event.resourceId().toString(), event.decision(), event.detailJson());
    }
}
