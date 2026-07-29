package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.intelligence.domain.Segment;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import com.sanad.platform.crm.intelligence.domain.SegmentPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link SegmentPort}.
 */
@Repository
public class JdbcSegmentAdapter implements SegmentPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcSegmentAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Segment createSegment(UUID tenantId, String segmentCode, String segmentName,
                                  String segmentType, String description, String criteriaJson) {
        String sql = """
                INSERT INTO crm_customer_segments
                    (tenant_id, segment_code, segment_name, segment_type, description, criteria)
                VALUES
                    (:tenantId, :segmentCode, :segmentName, :segmentType, :description,
                     CAST(:criteriaJson AS jsonb))
                RETURNING id, created_at, updated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("segmentCode", segmentCode)
                .addValue("segmentName", segmentName)
                .addValue("segmentType", segmentType)
                .addValue("description", description)
                .addValue("criteriaJson", criteriaJson != null ? criteriaJson : "null");
        var row = jdbc.queryForMap(sql, params);
        JsonNode criteria = null;
        if (criteriaJson != null) {
            try { criteria = mapper.readTree(criteriaJson); } catch (Exception ignored) {}
        }
        return new Segment(
                (UUID) row.get("id"),
                tenantId, segmentCode, segmentName, segmentType,
                description, criteria, true,
                (Instant) row.get("created_at"), (Instant) row.get("updated_at")
        );
    }

    @Override
    public Optional<Segment> findByCode(UUID tenantId, String segmentCode) {
        String sql = """
                SELECT id, tenant_id, segment_code, segment_name, segment_type,
                       description, criteria, active, created_at, updated_at
                FROM crm_customer_segments
                WHERE tenant_id = :tenantId AND segment_code = :segmentCode
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("segmentCode", segmentCode);
        try {
            return Optional.of(jdbc.queryForObject(sql, params, (rs, rowNum) -> {
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
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                );
            }));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SegmentMembership assignSegment(UUID tenantId, UUID accountId, UUID segmentId,
                                            String membershipType, UUID assignedBy) {
        String sql = """
                INSERT INTO crm_segment_memberships
                    (tenant_id, account_id, segment_id, membership_type, assigned_by, active)
                VALUES
                    (:tenantId, :accountId, :segmentId, :membershipType, :assignedBy, TRUE)
                RETURNING id, assigned_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId)
                .addValue("segmentId", segmentId)
                .addValue("membershipType", membershipType != null ? membershipType : "MANUAL")
                .addValue("assignedBy", assignedBy);
        var row = jdbc.queryForMap(sql, params);
        return new SegmentMembership(
                (UUID) row.get("id"),
                tenantId, accountId, segmentId,
                membershipType != null ? membershipType : "MANUAL",
                (Instant) row.get("assigned_at"),
                assignedBy, true
        );
    }

    @Override
    public void deactivateMembership(UUID tenantId, UUID accountId, UUID segmentId) {
        String sql = """
                UPDATE crm_segment_memberships SET active = FALSE
                WHERE tenant_id = :tenantId AND account_id = :accountId AND segment_id = :segmentId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId)
                .addValue("segmentId", segmentId);
        jdbc.update(sql, params);
    }

    @Override
    public List<SegmentMembership> findActiveMemberships(UUID tenantId, UUID accountId) {
        String sql = """
                SELECT id, tenant_id, account_id, segment_id, membership_type,
                       assigned_at, assigned_by, active
                FROM crm_segment_memberships
                WHERE tenant_id = :tenantId AND account_id = :accountId AND active = TRUE
                ORDER BY assigned_at DESC
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
                rs.getTimestamp("assigned_at").toInstant(),
                rs.getObject("assigned_by", UUID.class),
                rs.getBoolean("active")
        ));
    }
}
