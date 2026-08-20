package com.sanad.platform.crm.calls.infrastructure;

import com.sanad.platform.crm.calls.domain.CallDirection;
import com.sanad.platform.crm.calls.domain.CallDisposition;
import com.sanad.platform.crm.calls.domain.CallEvent;
import com.sanad.platform.crm.calls.domain.CallEventRepository;
import com.sanad.platform.crm.calls.domain.CallStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of the call aggregate port (G8-03 §18).
 *
 * <p>All reads/writes are tenant-scoped; RLS (ENABLE + FORCE) is the
 * defense-in-depth layer alongside the explicit {@code tenant_id} predicates.
 */
@Repository
public class JdbcCallEventRepository implements CallEventRepository {

    private static final String COLUMNS = """
            id, tenant_id, version, provider, provider_call_id, direction, source,
            from_number_normalized, to_number_normalized, match_status,
            matched_entity_type, matched_entity_id, matched_contact_id, matched_account_id,
            match_source, agent_user_id, device_id, status,
            ringing_at, answered_at, ended_at, duration_seconds, disposition,
            created_by, updated_by, created_at, updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCallEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CallEvent create(UUID tenantId, UUID actorId, CallEvent event, Instant now) {
        jdbc.update("""
                INSERT INTO crm_call_events (id, tenant_id, version, provider, provider_call_id,
                    direction, source, from_number_normalized, to_number_normalized, match_status,
                    matched_entity_type, matched_entity_id, matched_contact_id, matched_account_id,
                    match_source, agent_user_id, device_id, status,
                    ringing_at, answered_at, ended_at, duration_seconds, disposition,
                    created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :provider, :providerCallId, :direction, :source,
                    :fromNumber, :toNumber, :matchStatus,
                    :matchedType, :matchedId, :matchedContactId, :matchedAccountId,
                    :matchSource, :agentUserId, :deviceId, :status,
                    :ringingAt, :answeredAt, :endedAt, :duration, :disposition,
                    :actorId, :actorId, :now, :now)
                """, params()
                .addValue("id", event.id()).addValue("tenantId", tenantId)
                .addValue("provider", event.provider()).addValue("providerCallId", event.providerCallId())
                .addValue("direction", event.direction().name()).addValue("source", event.source().name())
                .addValue("fromNumber", event.fromNumberNormalized()).addValue("toNumber", event.toNumberNormalized())
                .addValue("matchStatus", event.matchStatus())
                .addValue("matchedType", event.matchedEntityType())
                .addValue("matchedId", event.matchedEntityId())
                .addValue("matchedContactId", event.matchedContactId())
                .addValue("matchedAccountId", event.matchedAccountId())
                .addValue("matchSource", event.matchSource())
                .addValue("agentUserId", event.agentUserId()).addValue("deviceId", event.deviceId())
                .addValue("status", event.status().name())
                .addValue("ringingAt", ts(event.ringingAt())).addValue("answeredAt", ts(event.answeredAt()))
                .addValue("endedAt", ts(event.endedAt())).addValue("duration", event.durationSeconds())
                .addValue("disposition", event.disposition() == null ? null : event.disposition().name())
                .addValue("actorId", actorId).addValue("now", ts(now)));
        return event;
    }

    @Override
    public Optional<CallEvent> get(UUID tenantId, UUID callId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_call_events " +
                        "WHERE tenant_id = :tenantId AND id = :id",
                params().addValue("tenantId", tenantId).addValue("id", callId), rowMapper)
                .stream().findFirst();
    }

    @Override
    public Optional<CallEvent> findByProviderCallId(UUID tenantId, String provider, String providerCallId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_call_events " +
                        "WHERE tenant_id = :tenantId AND provider = :provider AND provider_call_id = :providerCallId",
                params().addValue("tenantId", tenantId).addValue("provider", provider)
                        .addValue("providerCallId", providerCallId), rowMapper)
                .stream().findFirst();
    }

    @Override
    public CallEvent transition(UUID tenantId, UUID callId, long expectedVersion, UUID actorId,
                                CallStatus toStatus, Instant occurredAt, Instant now) {
        jdbc.update("""
                UPDATE crm_call_events
                SET status = :status,
                    answered_at = COALESCE(answered_at,
                        CASE WHEN CAST(:status AS TEXT) = 'ANSWERED'
                             THEN CAST(:occurredAt AS TIMESTAMP WITH TIME ZONE) END),
                    updated_by = :actorId, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
                """, params().addValue("status", toStatus.name()).addValue("occurredAt", ts(occurredAt))
                .addValue("actorId", actorId).addValue("now", ts(now))
                .addValue("tenantId", tenantId).addValue("id", callId)
                .addValue("expectedVersion", expectedVersion));
        return get(tenantId, callId).orElseThrow();
    }

    @Override
    public CallEvent complete(UUID tenantId, UUID callId, long expectedVersion, UUID actorId,
                              CallStatus terminalStatus, Instant endedAt, int durationSeconds,
                              CallDisposition disposition, Instant now) {
        jdbc.update("""
                UPDATE crm_call_events
                SET status = :status, ended_at = :endedAt,
                    duration_seconds = :duration, disposition = :disposition,
                    updated_by = :actorId, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
                """, params().addValue("status", terminalStatus.name()).addValue("endedAt", ts(endedAt))
                .addValue("duration", durationSeconds)
                .addValue("disposition", disposition == null ? null : disposition.name())
                .addValue("actorId", actorId).addValue("now", ts(now))
                .addValue("tenantId", tenantId).addValue("id", callId)
                .addValue("expectedVersion", expectedVersion));
        return get(tenantId, callId).orElseThrow();
    }

    @Override
    public List<CallEvent> list(UUID tenantId, String status, long cursorMs, UUID cursorId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM crm_call_events " +
                "WHERE tenant_id = :tenantId ");
        MapSqlParameterSource p = params().addValue("tenantId", tenantId).addValue("limit", limit);
        if (status != null && !status.isBlank()) {
            sql.append("AND status = :status ");
            p.addValue("status", status);
        }
        if (cursorMs > 0) {
            sql.append("AND (EXTRACT(EPOCH FROM created_at) * 1000 < :cursorMs " +
                    "OR (EXTRACT(EPOCH FROM created_at) * 1000 = :cursorMs AND id < :cursorId)) ");
            p.addValue("cursorMs", cursorMs).addValue("cursorId", cursorId);
        }
        sql.append("ORDER BY created_at DESC, id DESC LIMIT :limit");
        return jdbc.query(sql.toString(), p, rowMapper);
    }

    private static final RowMapper<CallEvent> rowMapper = (rs, rowNum) -> map(rs);

    private static CallEvent map(ResultSet rs) throws SQLException {
        return new CallEvent(
                uuid(rs, "id"), uuid(rs, "tenant_id"), rs.getLong("version"),
                rs.getString("provider"), rs.getString("provider_call_id"),
                CallDirection.valueOf(rs.getString("direction")),
                CallEvent.CallerSourceOfRecord.valueOf(rs.getString("source")),
                rs.getString("from_number_normalized"), rs.getString("to_number_normalized"),
                rs.getString("match_status"), rs.getString("matched_entity_type"),
                uuid(rs, "matched_entity_id"), uuid(rs, "matched_contact_id"), uuid(rs, "matched_account_id"),
                rs.getString("match_source"),
                uuid(rs, "agent_user_id"), uuid(rs, "device_id"),
                CallStatus.valueOf(rs.getString("status")),
                instant(rs, "ringing_at"), instant(rs, "answered_at"), instant(rs, "ended_at"),
                rs.getObject("duration_seconds") == null ? null : rs.getInt("duration_seconds"),
                rs.getString("disposition") == null ? null : CallDisposition.valueOf(rs.getString("disposition")),
                uuid(rs, "created_by"), uuid(rs, "updated_by"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }
}
