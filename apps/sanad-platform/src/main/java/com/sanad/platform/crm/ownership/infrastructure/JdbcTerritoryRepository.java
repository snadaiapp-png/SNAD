package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcTerritoryRepository implements TerritoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcTerritoryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public Territory save(Territory t) {
        UUID id = t.id() != null ? t.id() : UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_territories
              (id, tenant_id, code, display_name, parent_id, description, status, rule_type, rule_definition, priority,
               created_at, updated_at, created_by, updated_by)
            VALUES
              (:id, :tenantId, :code, :displayName, :parentId, :description, :status, :ruleType, :ruleDefinition, :priority,
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", t.tenantId()).addValue("code", t.code())
                .addValue("displayName", t.displayName()).addValue("parentId", t.parentId())
                .addValue("description", t.description()).addValue("status", t.status().name())
                .addValue("ruleType", t.ruleType().name()).addValue("ruleDefinition", t.ruleDefinition() != null ? t.ruleDefinition() : "{}")
                .addValue("priority", t.priority()).addValue("createdBy", t.createdBy()).addValue("updatedBy", t.updatedBy()));
        Territory saved = findById(t.tenantId(), id).orElseThrow();
        rebuildClosure(t.tenantId());
        return saved;
    }

    @Override
    public Optional<Territory> findById(UUID tenantId, UUID territoryId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_territories WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", territoryId), territoryMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<Territory> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_territories WHERE tenant_id=:tenantId AND code=:code",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("code", code), territoryMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<Territory> findByTenant(UUID tenantId, TerritoryStatus status) {
        return jdbc.query("SELECT * FROM crm_territories WHERE tenant_id=:tenantId AND status=:status ORDER BY display_name",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("status", status.name()), territoryMapper());
    }

    @Override
    public List<Territory> findChildren(UUID tenantId, UUID parentId) {
        return jdbc.query("SELECT * FROM crm_territories WHERE tenant_id=:tenantId AND parent_id=:parentId AND status='ACTIVE' ORDER BY display_name",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("parentId", parentId), territoryMapper());
    }

    @Override
    public List<Territory> findAncestors(UUID tenantId, UUID territoryId) {
        return jdbc.query("""
            SELECT t.* FROM crm_territories t
            JOIN crm_territory_closure c ON c.ancestor_id = t.id AND c.tenant_id = t.tenant_id
            WHERE c.tenant_id = :tenantId AND c.descendant_id = :territoryId AND c.depth > 0
            ORDER BY c.depth DESC
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("territoryId", territoryId), territoryMapper());
    }

    @Override
    public List<Territory> findDescendants(UUID tenantId, UUID territoryId) {
        return jdbc.query("""
            SELECT t.* FROM crm_territories t
            JOIN crm_territory_closure c ON c.descendant_id = t.id AND c.tenant_id = t.tenant_id
            WHERE c.tenant_id = :tenantId AND c.ancestor_id = :territoryId AND c.depth > 0
            ORDER BY c.depth
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("territoryId", territoryId), territoryMapper());
    }

    @Override @Transactional
    public void updateParent(UUID tenantId, UUID territoryId, UUID newParentId, UUID updatedBy) {
        if (newParentId != null && wouldCreateCycle(tenantId, territoryId, newParentId)) {
            throw new TerritoryCycleException(tenantId, territoryId, newParentId);
        }
        int rows = jdbc.update("UPDATE crm_territories SET parent_id=:parentId, updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:id",
            new MapSqlParameterSource().addValue("parentId", newParentId).addValue("updatedBy", updatedBy).addValue("tenantId", tenantId).addValue("id", territoryId));
        if (rows == 0) throw new TerritoryNotFoundException(tenantId, territoryId);
        rebuildClosure(tenantId);
    }

    @Override
    public void rebuildClosure(UUID tenantId) {
        // Delete existing closure for this tenant and rebuild
        jdbc.update("DELETE FROM crm_territory_closure WHERE tenant_id=:tenantId",
            new MapSqlParameterSource().addValue("tenantId", tenantId));
        // Self-references (depth 0)
        jdbc.update("""
            INSERT INTO crm_territory_closure (tenant_id, ancestor_id, descendant_id, depth)
            SELECT tenant_id, id, id, 0 FROM crm_territories WHERE tenant_id=:tenantId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId));
        // Build closure iteratively up to max depth
        for (int depth = 1; depth <= 20; depth++) {
            int inserted = jdbc.update("""
                INSERT INTO crm_territory_closure (tenant_id, ancestor_id, descendant_id, depth)
                SELECT t.tenant_id, c.ancestor_id, t.id, :depth
                FROM crm_territories t
                JOIN crm_territory_closure c ON c.descendant_id = t.parent_id AND c.tenant_id = t.tenant_id
                WHERE t.tenant_id=:tenantId AND t.parent_id IS NOT NULL
                AND NOT EXISTS (
                    SELECT 1 FROM crm_territory_closure existing
                    WHERE existing.tenant_id = t.tenant_id
                    AND existing.ancestor_id = c.ancestor_id
                    AND existing.descendant_id = t.id
                    AND existing.depth = :depth
                )
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("depth", depth));
            if (inserted == 0) break;
        }
    }

    @Override @Transactional
    public void archive(UUID tenantId, UUID territoryId, UUID updatedBy) {
        int rows = jdbc.update("UPDATE crm_territories SET status='ARCHIVED', updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", territoryId).addValue("updatedBy", updatedBy));
        if (rows == 0) throw new TerritoryNotFoundException(tenantId, territoryId);
    }

    @Override
    public boolean wouldCreateCycle(UUID tenantId, UUID territoryId, UUID proposedParentId) {
        if (territoryId.equals(proposedParentId)) return true;
        // Check if territoryId is an ancestor of proposedParentId (would create cycle)
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM crm_territory_closure
            WHERE tenant_id=:tenantId AND ancestor_id=:territoryId AND descendant_id=:proposedParentId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("territoryId", territoryId).addValue("proposedParentId", proposedParentId), Integer.class);
        return count != null && count > 0;
    }
}
