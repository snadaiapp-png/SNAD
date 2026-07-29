package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.NextBestActionPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link NextBestActionPort}.
 */
@Repository
public class JdbcNextBestActionAdapter implements NextBestActionPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNextBestActionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public NextBestAction create(UUID tenantId, UUID accountId, String actionCode,
                                  String description, double confidence, String reasoning,
                                  Instant expiresAt, boolean humanConfirmationRequired) {
        Instant now = Instant.now();
        String sql = """
                INSERT INTO crm_next_best_actions
                    (tenant_id, account_id, action_code, description, confidence, reasoning,
                     status, generated_at, expires_at, human_confirmation_required)
                VALUES
                    (:tenantId, :accountId, :actionCode, :description, :confidence, :reasoning,
                     'PENDING', :generatedAt, :expiresAt, :humanConfirmationRequired)
                RETURNING id, version
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId)
                .addValue("actionCode", actionCode)
                .addValue("description", description)
                .addValue("confidence", confidence)
                .addValue("reasoning", reasoning)
                .addValue("generatedAt", java.sql.Timestamp.from(now))
                .addValue("expiresAt", java.sql.Timestamp.from(expiresAt))
                .addValue("humanConfirmationRequired", humanConfirmationRequired);
        var row = jdbc.queryForMap(sql, params);
        return new NextBestAction(
                (UUID) row.get("id"), tenantId, accountId, actionCode,
                description, confidence, reasoning, NextBestAction.STATUS_PENDING,
                now, expiresAt, humanConfirmationRequired, null, null,
                ((Number) row.get("version")).longValue()
        );
    }

    @Override
    public Optional<NextBestAction> resolve(UUID tenantId, UUID actionId, String resolution,
                                             UUID resolvedBy, long expectedVersion) {
        String sql = """
                UPDATE crm_next_best_actions
                SET status = :resolution, resolved_at = NOW(), resolved_by = :resolvedBy,
                    version = version + 1
                WHERE tenant_id = :tenantId AND id = :actionId
                  AND status = 'PENDING' AND version = :expectedVersion
                RETURNING id, tenant_id, account_id, action_code, description, confidence,
                          reasoning, status, generated_at, expires_at,
                          human_confirmation_required, resolved_at, resolved_by, version
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("actionId", actionId)
                .addValue("resolution", resolution)
                .addValue("resolvedBy", resolvedBy)
                .addValue("expectedVersion", expectedVersion);
        try {
            return Optional.of(jdbc.queryForObject(sql, params, (rs, rowNum) -> new NextBestAction(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getString("action_code"),
                    rs.getString("description"),
                    rs.getDouble("confidence"),
                    rs.getString("reasoning"),
                    rs.getString("status"),
                    rs.getTimestamp("generated_at").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getBoolean("human_confirmation_required"),
                    rs.getTimestamp("resolved_at") != null
                            ? rs.getTimestamp("resolved_at").toInstant() : null,
                    rs.getObject("resolved_by", UUID.class),
                    rs.getLong("version")
            )));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public int expireStale(UUID tenantId) {
        String sql = """
                UPDATE crm_next_best_actions
                SET status = 'EXPIRED', resolved_at = NOW(), version = version + 1
                WHERE tenant_id = :tenantId AND status = 'PENDING' AND expires_at <= NOW()
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId);
        return jdbc.update(sql, params);
    }
}
