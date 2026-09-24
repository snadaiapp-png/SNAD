package com.sanad.platform.ai.infrastructure;

import com.sanad.platform.ai.domain.AiInference;
import com.sanad.platform.ai.domain.AiInferenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAiInferenceRepository implements AiInferenceRepository {

    private final JdbcTemplate jdbc;

    public JdbcAiInferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AiInference> MAPPER = (rs, rowNum) -> new AiInference(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("agent_id", UUID.class),
            rs.getObject("invoked_by", UUID.class),
            rs.getString("input_summary"),
            rs.getString("input_hash"),
            rs.getString("output_summary"),
            rs.getString("output_hash"),
            rs.getBoolean("advisory"),
            AiInference.Status.valueOf(rs.getString("status")),
            rs.getString("error_message"),
            (Integer) rs.getObject("tokens_input"),
            (Integer) rs.getObject("tokens_output"),
            (Long) rs.getObject("latency_ms"),
            rs.getInt("cost_cents"),
            rs.getObject("correlation_id", UUID.class),
            rs.getString("business_entity_type"),
            rs.getObject("business_entity_id", UUID.class),
            rs.getObject("workflow_instance_id", UUID.class),
            rs.getTimestamp("created_at").toInstant()
    );

    @Override
    public AiInference save(AiInference inference) {
        jdbc.update("""
                INSERT INTO ai_inference_log
                    (id, tenant_id, agent_id, invoked_by, input_summary, input_hash,
                     output_summary, output_hash, advisory, status, error_message,
                     tokens_input, tokens_output, latency_ms, cost_cents,
                     correlation_id, business_entity_type, business_entity_id,
                     workflow_instance_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    output_summary = EXCLUDED.output_summary,
                    output_hash = EXCLUDED.output_hash,
                    status = EXCLUDED.status,
                    error_message = EXCLUDED.error_message,
                    tokens_input = EXCLUDED.tokens_input,
                    tokens_output = EXCLUDED.tokens_output,
                    latency_ms = EXCLUDED.latency_ms,
                    cost_cents = EXCLUDED.cost_cents,
                    workflow_instance_id = EXCLUDED.workflow_instance_id
                """,
                inference.id(), inference.tenantId(), inference.agentId(), inference.invokedBy(),
                inference.inputSummary(), inference.inputHash(),
                inference.outputSummary(), inference.outputHash(),
                inference.advisory(), inference.status().name(), inference.errorMessage(),
                inference.tokensInput(), inference.tokensOutput(), inference.latencyMs(),
                inference.costCents(), inference.correlationId(),
                inference.businessEntityType(), inference.businessEntityId(),
                inference.workflowInstanceId(),
                Timestamp.from(inference.createdAt())
        );
        return inference;
    }

    @Override
    public Optional<AiInference> findById(UUID tenantId, UUID id) {
        return jdbc.query(
                "SELECT * FROM ai_inference_log WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<AiInference> findByTenant(UUID tenantId, int limit) {
        return jdbc.query(
                "SELECT * FROM ai_inference_log WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public List<AiInference> findByAgent(UUID tenantId, UUID agentId, int limit) {
        return jdbc.query(
                "SELECT * FROM ai_inference_log WHERE tenant_id = ? AND agent_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, agentId, Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public List<AiInference> findByBusinessEntity(UUID tenantId, String entityType, UUID entityId) {
        return jdbc.query(
                "SELECT * FROM ai_inference_log WHERE tenant_id = ? AND business_entity_type = ? AND business_entity_id = ? ORDER BY created_at DESC",
                MAPPER, tenantId, entityType, entityId);
    }

    @Override
    public long countByTenantThisMonth(UUID tenantId) {
        var monthStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
                .minus(Instant.now().atZone(java.time.ZoneOffset.UTC).getDayOfMonth() - 1, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS);
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_inference_log WHERE tenant_id = ? AND created_at >= ?",
                Long.class, tenantId, Timestamp.from(monthStart));
        return count != null ? count : 0L;
    }
}
