package com.sanad.platform.crm.query.infrastructure;

import com.sanad.platform.crm.query.domain.TimelineProjectionRepository;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class JdbcTimelineProjectionRepository implements TimelineProjectionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcTimelineProjectionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TimelineEvent> findBySubject(UUID tenantId, String subjectType, UUID subjectId, int limit) {
        return jdbc.queryForList("SELECT * FROM crm_timeline_events WHERE tenant_id=:t AND subject_type=:subjectType AND subject_id=:subjectId ORDER BY occurred_at DESC, id DESC LIMIT :limit",
                new MapSqlParameterSource().addValue("t",tenantId).addValue("subjectType",subjectType.toUpperCase()).addValue("subjectId",subjectId).addValue("limit",limit))
                .stream().map(r -> new TimelineEvent((UUID)r.get("id"),(String)r.get("subject_type"),(UUID)r.get("subject_id"),(String)r.get("event_type"),(String)r.get("summary"),(String)r.get("source_type"),(UUID)r.get("source_id"),((java.sql.Timestamp)r.get("occurred_at")).toInstant(),(UUID)r.get("created_by"))).toList();
    }
}
