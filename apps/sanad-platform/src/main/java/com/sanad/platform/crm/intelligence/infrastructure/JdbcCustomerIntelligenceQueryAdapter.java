package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.intelligence.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
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
 * JDBC implementation of {@link CustomerIntelligenceQueryPort}.
 * Reads from crm_customer_scores, crm_customer_score_history,
 * crm_next_best_actions, crm_segment_memberships, crm_customer_segments.
 */
@Repository
public class JdbcCustomerIntelligenceQueryAdapter implements CustomerIntelligenceQueryPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCustomerIntelligenceQueryAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<StoredScore> findLatestScore(UUID tenantId, UUID accountId, String scoreType) {
        try {
            String sql = """
                    SELECT id, tenant_id, account_id, score_type, score_value, score_band,
                           components::text AS components_json, confidence, calculated_at,
                           trigger_reason, version
                    FROM crm_customer_scores
                    WHERE tenant_id = :tenantId AND account_id = :accountId AND score_type = :scoreType
                    ORDER BY calculated_at DESC
                    LIMIT 1
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("accountId", accountId)
                    .addValue("scoreType", scoreType);
            return Optional.of(jdbc.queryForObject(sql, params, new StoredScoreRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<StoredScore> findLatestScores(UUID tenantId, UUID accountId) {
        String sql = """
                SELECT DISTINCT ON (score_type)
                       id, tenant_id, account_id, score_type, score_value, score_band,
                       components::text AS components_json, confidence, calculated_at,
                       trigger_reason, version
                FROM crm_customer_scores
                WHERE tenant_id = :tenantId AND account_id = :accountId
                ORDER BY score_type, calculated_at DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId);
        return jdbc.query(sql, params, new StoredScoreRowMapper());
    }

    @Override
    public List<ScoreHistoryEntry> findScoreHistory(UUID tenantId, UUID accountId, String scoreType, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, account_id, score_type, previous_value, previous_band,
                       new_value, new_band, delta, changed_at, changed_by, trigger_reason
                FROM crm_customer_score_history
                WHERE tenant_id = :tenantId AND account_id = :accountId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId)
                .addValue("limit", limit);
        if (scoreType != null && !scoreType.isBlank()) {
            sql.append(" AND score_type = :scoreType");
            params.addValue("scoreType", scoreType);
        }
        sql.append(" ORDER BY changed_at DESC LIMIT :limit");
        return jdbc.query(sql.toString(), params, new ScoreHistoryRowMapper());
    }

    @Override
    public List<NextBestAction> findNextBestActions(UUID tenantId, UUID accountId) {
        String sql = """
                SELECT id, tenant_id, account_id, action_code, description, confidence,
                       reasoning, status, generated_at, expires_at,
                       human_confirmation_required, resolved_at, resolved_by, version
                FROM crm_next_best_actions
                WHERE tenant_id = :tenantId AND account_id = :accountId
                  AND status = 'PENDING'
                  AND expires_at > NOW()
                ORDER BY generated_at DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId);
        return jdbc.query(sql, params, new NextBestActionRowMapper());
    }

    @Override
    public List<SegmentMembership> findActiveSegments(UUID tenantId, UUID accountId) {
        String sql = """
                SELECT m.id, m.tenant_id, m.account_id, m.segment_id, m.membership_type,
                       m.assigned_at, m.assigned_by, m.active
                FROM crm_segment_memberships m
                WHERE m.tenant_id = :tenantId AND m.account_id = :accountId AND m.active = TRUE
                ORDER BY m.assigned_at DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId);
        return jdbc.query(sql, params, (rs, rowNum) -> new SegmentMembership(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("segment_id", UUID.class),
                rs.getString("membership_type"),
                getInstant(rs, "assigned_at"),
                rs.getObject("assigned_by", UUID.class),
                rs.getBoolean("active")
        ));
    }

    @Override
    public List<Segment> findAllSegments(UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, segment_code, segment_name, segment_type,
                       description, criteria, active, created_at, updated_at
                FROM crm_customer_segments
                WHERE tenant_id = :tenantId AND active = TRUE
                ORDER BY segment_name
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId);
        return jdbc.query(sql, params, (rs, rowNum) -> {
            String criteriaJson = rs.getString("criteria");
            JsonNode criteria = null;
            if (criteriaJson != null) {
                try { criteria = mapper.readTree(criteriaJson); } catch (Exception ignored) {}
            }
            return new Segment(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getString("segment_code"),
                    rs.getString("segment_name"),
                    rs.getString("segment_type"),
                    rs.getString("description"),
                    criteria,
                    rs.getBoolean("active"),
                    getInstant(rs, "created_at"),
                    getInstant(rs, "updated_at")
            );
        });
    }

    // ── Row Mappers ──

    private static Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    private static class StoredScoreRowMapper implements RowMapper<StoredScore> {
        @Override
        public StoredScore mapRow(ResultSet rs, int rowNum) throws SQLException {
            Double confidence = rs.getObject("confidence") != null
                    ? rs.getDouble("confidence") : null;
            return new StoredScore(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getString("score_type"),
                    rs.getDouble("score_value"),
                    rs.getString("score_band"),
                    rs.getString("components_json"),
                    confidence,
                    getInstant(rs, "calculated_at"),
                    rs.getString("trigger_reason"),
                    rs.getLong("version")
            );
        }
    }

    private static class ScoreHistoryRowMapper implements RowMapper<ScoreHistoryEntry> {
        @Override
        public ScoreHistoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            Double prevVal = rs.getObject("previous_value") != null
                    ? rs.getDouble("previous_value") : null;
            return new ScoreHistoryEntry(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getString("score_type"),
                    prevVal,
                    rs.getString("previous_band"),
                    rs.getDouble("new_value"),
                    rs.getString("new_band"),
                    rs.getDouble("delta"),
                    getInstant(rs, "changed_at"),
                    rs.getObject("changed_by", UUID.class),
                    rs.getString("trigger_reason")
            );
        }
    }

    private static class NextBestActionRowMapper implements RowMapper<NextBestAction> {
        @Override
        public NextBestAction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new NextBestAction(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getString("action_code"),
                    rs.getString("description"),
                    rs.getDouble("confidence"),
                    rs.getString("reasoning"),
                    rs.getString("status"),
                    getInstant(rs, "generated_at"),
                    getInstant(rs, "expires_at"),
                    rs.getBoolean("human_confirmation_required"),
                    getInstant(rs, "resolved_at"),
                    rs.getObject("resolved_by", UUID.class),
                    rs.getLong("version")
            );
        }
    }
}
