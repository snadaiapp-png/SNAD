package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class JdbcTimelineEventAdapter implements TimelineEventPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcTimelineEventAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID tenantId, String subjectType, UUID subjectId,
                       String eventType, String summary, String sourceType, UUID sourceId,
                       UUID actorId, Instant occurredAt) {
        jdbc.update(
                "INSERT INTO crm_timeline_events (id, tenant_id, subject_type, subject_id, event_type, " +
                "summary, source_type, source_id, occurred_at, created_by) " +
                "VALUES (:id, :tenantId, :subjectType, :subjectId, :eventType, :summary, " +
                ":sourceType, :sourceId, :occurredAt, :createdBy)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", tenantId)
                        .addValue("subjectType", subjectType)
                        .addValue("subjectId", subjectId)
                        .addValue("eventType", eventType)
                        .addValue("summary", summary)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId)
                        .addValue("occurredAt", Timestamp.from(occurredAt))
                        .addValue("createdBy", actorId));
    }
}
