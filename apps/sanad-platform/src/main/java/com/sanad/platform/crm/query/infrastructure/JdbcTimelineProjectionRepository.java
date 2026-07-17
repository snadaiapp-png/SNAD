package com.sanad.platform.crm.query.infrastructure;

import com.sanad.platform.crm.query.domain.TimelineProjectionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcTimelineProjectionRepository implements TimelineProjectionRepository {
    private static final String FIND_BY_SUBJECT_SQL = """
            SELECT id, subject_type, subject_id, event_type, summary,
                   source_type, source_id, occurred_at, created_by
            FROM crm_timeline_events
            WHERE tenant_id = :tenantId
              AND subject_type = :subjectType
              AND subject_id = :subjectId
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTimelineProjectionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TimelineEvent> findBySubject(
            UUID tenantId,
            String subjectType,
            UUID subjectId,
            int limit
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("subjectType", subjectType.toUpperCase())
                .addValue("subjectId", subjectId)
                .addValue("limit", limit);

        return jdbc.query(FIND_BY_SUBJECT_SQL, parameters, (resultSet, rowNumber) ->
                new TimelineEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("subject_type"),
                        resultSet.getObject("subject_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("summary"),
                        resultSet.getString("source_type"),
                        resultSet.getObject("source_id", UUID.class),
                        toInstant(resultSet.getObject("occurred_at")),
                        resultSet.getObject("created_by", UUID.class)
                ));
    }

    static Instant toInstant(Object value) {
        if (value == null) {
            throw new IllegalStateException("crm_timeline_events.occurred_at must not be null");
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException(
                "Unsupported occurred_at JDBC type: " + value.getClass().getName());
    }
}
