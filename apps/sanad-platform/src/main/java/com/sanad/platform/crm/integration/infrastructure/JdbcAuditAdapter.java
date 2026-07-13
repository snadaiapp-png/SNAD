package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.AuditPort;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class JdbcAuditAdapter implements AuditPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAuditAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId, AuditChange change, Instant timestamp) {
        try {
            jdbc.update("INSERT INTO platform_audit_logs (id,tenant_id,actor_id,action,entity_type,entity_id,before_state,after_state,created_at) VALUES (:id,:tenantId,:actorId,:action,:entityType,:entityId,:beforeState,:afterState,:createdAt)",
                    new MapSqlParameterSource().addValue("id",UUID.randomUUID()).addValue("tenantId",tenantId).addValue("actorId",actorId).addValue("action",action).addValue("entityType",entityType).addValue("entityId",entityId).addValue("beforeState",change.beforeJson()).addValue("afterState",change.afterJson()).addValue("createdAt",java.sql.Timestamp.from(timestamp)));
        } catch (Exception ignored) { /* audit is best-effort — must not block business operations */ }
    }
}
