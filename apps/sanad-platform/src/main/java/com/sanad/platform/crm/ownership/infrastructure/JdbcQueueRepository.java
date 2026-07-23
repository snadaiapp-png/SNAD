package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcQueueRepository implements QueueRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcQueueRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public Queue save(Queue q) {
        UUID id = q.id() != null ? q.id() : UUID.randomUUID();
        String sql = """
            INSERT INTO crm_queues
              (id, tenant_id, code, display_name, record_type, description, status,
               max_items_per_user, sla_minutes, escalation_target_queue_id, default_owner_id,
               created_at, updated_at, created_by, updated_by)
            VALUES
              (:id, :tenantId, :code, :displayName, :recordType, :description, :status,
               :maxItems, :slaMinutes, :escalationQueueId, :defaultOwnerId,
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
            ON CONFLICT (tenant_id, code) DO NOTHING
            RETURNING id
            """;
        var params = new MapSqlParameterSource()
            .addValue("id", id).addValue("tenantId", q.tenantId()).addValue("code", q.code())
            .addValue("displayName", q.displayName()).addValue("recordType", q.recordType().name())
            .addValue("description", q.description()).addValue("status", q.status().name())
            .addValue("maxItems", q.maxItemsPerUser()).addValue("slaMinutes", q.slaMinutes())
            .addValue("escalationQueueId", q.escalationTargetQueueId()).addValue("defaultOwnerId", q.defaultOwnerId())
            .addValue("createdBy", q.createdBy()).addValue("updatedBy", q.updatedBy());
        try {
            jdbc.queryForObject(sql, params, UUID.class);
        } catch (EmptyResultDataAccessException e) {
            // ON CONFLICT DO NOTHING with RETURNING returns nothing if conflict
            throw new OwnershipDomainException("Queue code already exists: " + q.code());
        }
        return findById(q.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<Queue> findById(UUID tenantId, UUID queueId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_queues WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", queueId), queueMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<Queue> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_queues WHERE tenant_id=:tenantId AND code=:code",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("code", code), queueMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<Queue> findByTenant(UUID tenantId, QueueStatus status) {
        return jdbc.query("SELECT * FROM crm_queues WHERE tenant_id=:tenantId AND status=:status ORDER BY display_name",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("status", status.name()), queueMapper());
    }

    @Override
    public List<Queue> findByRecordType(UUID tenantId, QueueRecordType recordType) {
        return jdbc.query("SELECT * FROM crm_queues WHERE tenant_id=:tenantId AND record_type=:recordType AND status='ACTIVE' ORDER BY display_name",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("recordType", recordType.name()), queueMapper());
    }

    @Override @Transactional
    public void updateStatus(UUID tenantId, UUID queueId, QueueStatus status, UUID updatedBy) {
        int rows = jdbc.update("UPDATE crm_queues SET status=:status, updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:id",
            new MapSqlParameterSource().addValue("status", status.name()).addValue("updatedBy", updatedBy)
                .addValue("tenantId", tenantId).addValue("id", queueId));
        if (rows == 0) throw new QueueNotFoundException(tenantId, queueId);
    }
}
