package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.ScoringModel;
import com.sanad.platform.crm.intelligence.domain.ScoringPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link ScoringPort}.
 * Writes to crm_customer_scores (upsert latest) and crm_customer_score_history (insert audit).
 */
@Repository
@Transactional
public class JdbcScoringAdapter implements ScoringPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcScoringAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public StoredScore saveScore(UUID tenantId, UUID accountId, String scoreType,
                                  double scoreValue, String scoreBand,
                                  String componentsJson, Double confidence,
                                  String triggerReason, UUID actorId) {
        Instant now = Instant.now();

        // Insert the new score row
        String insertSql = """
                INSERT INTO crm_customer_scores
                    (tenant_id, account_id, score_type, score_value, score_band,
                     components, confidence, calculated_at, trigger_reason, version)
                VALUES
                    (:tenantId, :accountId, :scoreType, :scoreValue, :scoreBand,
                     CAST(:componentsJson AS jsonb), :confidence, :calculatedAt,
                     :triggerReason, 0)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId)
                .addValue("scoreType", scoreType)
                .addValue("scoreValue", scoreValue)
                .addValue("scoreBand", scoreBand)
                .addValue("componentsJson", componentsJson != null ? componentsJson : "{}")
                .addValue("confidence", confidence)
                .addValue("calculatedAt", java.sql.Timestamp.from(now))
                .addValue("triggerReason", triggerReason);
        jdbc.update(insertSql, params);

        // Record history entry (find previous to compute delta)
        String prevSql = """
                SELECT score_value, score_band FROM crm_customer_scores
                WHERE tenant_id = :tenantId AND account_id = :accountId AND score_type = :scoreType
                  AND calculated_at < :now
                ORDER BY calculated_at DESC LIMIT 1
                """;
        MapSqlParameterSource prevParams = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId)
                .addValue("scoreType", scoreType)
                .addValue("now", java.sql.Timestamp.from(now));

        try {
            var prevRow = jdbc.queryForMap(prevSql, prevParams);
            double prevValue = ((Number) prevRow.get("score_value")).doubleValue();
            String prevBand = (String) prevRow.get("score_band");
            double delta = scoreValue - prevValue;

            String historySql = """
                    INSERT INTO crm_customer_score_history
                        (tenant_id, account_id, score_type, previous_value, previous_band,
                         new_value, new_band, delta, changed_by, trigger_reason)
                    VALUES
                        (:tenantId, :accountId, :scoreType, :prevValue, :prevBand,
                         :newValue, :newBand, :delta, :changedBy, :triggerReason)
                    """;
            MapSqlParameterSource histParams = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("accountId", accountId)
                    .addValue("scoreType", scoreType)
                    .addValue("prevValue", prevValue)
                    .addValue("prevBand", prevBand)
                    .addValue("newValue", scoreValue)
                    .addValue("newBand", scoreBand)
                    .addValue("delta", delta)
                    .addValue("changedBy", actorId)
                    .addValue("triggerReason", triggerReason);
            jdbc.update(historySql, histParams);
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            // First score — no history delta to record
        }

        return new StoredScore(null, tenantId, accountId, scoreType, scoreValue, scoreBand,
                componentsJson, confidence, now, triggerReason, 0);
    }

    @Override
    public Optional<ScoringModel> getActiveModel(UUID tenantId, String scoreType) {
        String sql = """
                SELECT id, tenant_id, score_type, version, weights, active, activated_at
                FROM crm_scoring_models
                WHERE tenant_id = :tenantId AND score_type = :scoreType AND active = TRUE
                ORDER BY activated_at DESC LIMIT 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("scoreType", scoreType);
        try {
            return Optional.of(jdbc.queryForObject(sql, params, (rs, rowNum) -> {
                String weightsJson = rs.getString("weights");
                JsonNode weights = null;
                try { weights = mapper.readTree(weightsJson); } catch (Exception ignored) {}
                return new ScoringModel(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("score_type"),
                        rs.getString("version"),
                        weights,
                        rs.getBoolean("active"),
                        rs.getTimestamp("activated_at") != null
                                ? rs.getTimestamp("activated_at").toInstant() : Instant.now()
                );
            }));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public ScoringModel saveModel(UUID tenantId, String scoreType, String version,
                                   String weightsJson, boolean active) {
        // Deactivate existing models for this type
        if (active) {
            String deactivateSql = """
                    UPDATE crm_scoring_models SET active = FALSE
                    WHERE tenant_id = :tenantId AND score_type = :scoreType AND active = TRUE
                    """;
            MapSqlParameterSource deactParams = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("scoreType", scoreType);
            jdbc.update(deactivateSql, deactParams);
        }

        String insertSql = """
                INSERT INTO crm_scoring_models (tenant_id, score_type, version, weights, active)
                VALUES (:tenantId, :scoreType, :version, CAST(:weightsJson AS jsonb), :active)
                RETURNING id, activated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("scoreType", scoreType)
                .addValue("version", version)
                .addValue("weightsJson", weightsJson)
                .addValue("active", active);

        var result = jdbc.queryForObject(insertSql, params, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            Instant activatedAt = rs.getTimestamp("activated_at") != null
                    ? rs.getTimestamp("activated_at").toInstant() : Instant.now();
            return new ScoringModel(id, tenantId, scoreType, version,
                    parseWeights(weightsJson), active, activatedAt);
        });

        return result;
    }

    private JsonNode parseWeights(String weightsJson) {
        try { return mapper.readTree(weightsJson); } catch (Exception e) { return mapper.createObjectNode(); }
    }
}
