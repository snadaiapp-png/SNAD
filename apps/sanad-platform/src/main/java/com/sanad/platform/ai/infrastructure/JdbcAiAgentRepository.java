package com.sanad.platform.ai.infrastructure;

import com.sanad.platform.ai.domain.AiAgent;
import com.sanad.platform.ai.domain.AiAgentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAiAgentRepository implements AiAgentRepository {

    private final JdbcTemplate jdbc;

    public JdbcAiAgentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AiAgent> MAPPER = (rs, rowNum) -> new AiAgent(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            AiAgent.Provider.valueOf(rs.getString("provider")),
            rs.getString("model_name"),
            rs.getString("system_prompt"),
            rs.getString("configuration"),
            AiAgent.Status.valueOf(rs.getString("status")),
            (Integer) rs.getObject("max_tokens"),
            rs.getObject("temperature") != null ? rs.getDouble("temperature") : null,
            rs.getObject("created_by", UUID.class),
            rs.getLong("version_lock"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public AiAgent save(AiAgent agent) {
        jdbc.update("""
                INSERT INTO ai_agents
                    (id, tenant_id, code, name, description, provider, model_name,
                     system_prompt, configuration, status, max_tokens, temperature,
                     created_by, version_lock, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    description = EXCLUDED.description,
                    provider = EXCLUDED.provider,
                    model_name = EXCLUDED.model_name,
                    system_prompt = EXCLUDED.system_prompt,
                    configuration = EXCLUDED.configuration,
                    status = EXCLUDED.status,
                    max_tokens = EXCLUDED.max_tokens,
                    temperature = EXCLUDED.temperature,
                    version_lock = EXCLUDED.version_lock,
                    version = EXCLUDED.version,
                    updated_at = EXCLUDED.updated_at
                """,
                agent.id(), agent.tenantId(), agent.code(), agent.name(), agent.description(),
                agent.provider().name(), agent.modelName(), agent.systemPrompt(),
                agent.configuration(), agent.status().name(),
                agent.maxTokens(), agent.temperature(),
                agent.createdBy(), agent.versionLock(), agent.version(),
                Timestamp.from(agent.createdAt()), Timestamp.from(agent.updatedAt())
        );
        return agent;
    }

    @Override
    public Optional<AiAgent> findById(UUID tenantId, UUID id) {
        return jdbc.query(
                "SELECT * FROM ai_agents WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<AiAgent> findByCode(UUID tenantId, String code) {
        return jdbc.query(
                "SELECT * FROM ai_agents WHERE tenant_id = ? AND code = ?",
                MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<AiAgent> findByTenant(UUID tenantId, int limit) {
        return jdbc.query(
                "SELECT * FROM ai_agents WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public List<AiAgent> findByTenantAndStatus(UUID tenantId, AiAgent.Status status, int limit) {
        return jdbc.query(
                "SELECT * FROM ai_agents WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM ai_agents WHERE tenant_id = ? AND id = ?", tenantId, id);
    }
}
