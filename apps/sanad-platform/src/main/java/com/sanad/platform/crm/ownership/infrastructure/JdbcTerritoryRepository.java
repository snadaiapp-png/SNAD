package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.Territory;
import com.sanad.platform.crm.ownership.domain.TerritoryCycleException;
import com.sanad.platform.crm.ownership.domain.TerritoryNotFoundException;
import com.sanad.platform.crm.ownership.domain.TerritoryRepository;
import com.sanad.platform.crm.ownership.domain.TerritoryStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.territoryMapper;

@Repository
public class JdbcTerritoryRepository implements TerritoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTerritoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Territory save(Territory territory) {
        lockTenantHierarchy(territory.tenantId());
        if (territory.parentId() != null) {
            requireTerritory(territory.tenantId(), territory.parentId());
        }

        UUID id = territory.id() != null ? territory.id() : UUID.randomUUID();
        if (territory.parentId() != null && id.equals(territory.parentId())) {
            throw new TerritoryCycleException(territory.tenantId(), id, territory.parentId());
        }

        jdbc.update("""
                INSERT INTO crm_territories
                  (id, tenant_id, code, display_name, parent_id, description,
                   status, rule_type, rule_definition, priority,
                   created_at, updated_at, created_by, updated_by)
                VALUES
                  (:id, :tenantId, :code, :displayName, :parentId, :description,
                   :status, :ruleType, CAST(:ruleDefinition AS jsonb), :priority,
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", territory.tenantId())
                .addValue("code", territory.code())
                .addValue("displayName", territory.displayName())
                .addValue("parentId", territory.parentId())
                .addValue("description", territory.description())
                .addValue("status", territory.status().name())
                .addValue("ruleType", territory.ruleType().name())
                .addValue("ruleDefinition", territory.ruleDefinition() != null
                        ? territory.ruleDefinition() : "{}")
                .addValue("priority", territory.priority())
                .addValue("createdBy", territory.createdBy())
                .addValue("updatedBy", territory.updatedBy()));

        rebuildClosureLocked(territory.tenantId());
        return findById(territory.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<Territory> findById(UUID tenantId, UUID territoryId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_territories WHERE tenant_id=:tenantId AND id=:id",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("id", territoryId), territoryMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Territory> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_territories WHERE tenant_id=:tenantId AND code=:code",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("code", code), territoryMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<Territory> findByTenant(UUID tenantId, TerritoryStatus status) {
        return jdbc.query("""
                SELECT *
                  FROM crm_territories
                 WHERE tenant_id=:tenantId
                   AND status=:status
                 ORDER BY display_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", status.name()), territoryMapper());
    }

    @Override
    public List<Territory> findChildren(UUID tenantId, UUID parentId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_territories
                 WHERE tenant_id=:tenantId
                   AND parent_id=:parentId
                   AND status='ACTIVE'
                 ORDER BY display_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("parentId", parentId), territoryMapper());
    }

    @Override
    public List<Territory> findAncestors(UUID tenantId, UUID territoryId) {
        return jdbc.query("""
                SELECT territory.*
                  FROM crm_territories territory
                  JOIN crm_territory_closure closure
                    ON closure.ancestor_id = territory.id
                   AND closure.tenant_id = territory.tenant_id
                 WHERE closure.tenant_id=:tenantId
                   AND closure.descendant_id=:territoryId
                   AND closure.depth>0
                 ORDER BY closure.depth DESC, territory.id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("territoryId", territoryId), territoryMapper());
    }

    @Override
    public List<Territory> findDescendants(UUID tenantId, UUID territoryId) {
        return jdbc.query("""
                SELECT territory.*
                  FROM crm_territories territory
                  JOIN crm_territory_closure closure
                    ON closure.descendant_id = territory.id
                   AND closure.tenant_id = territory.tenant_id
                 WHERE closure.tenant_id=:tenantId
                   AND closure.ancestor_id=:territoryId
                   AND closure.depth>0
                 ORDER BY closure.depth, territory.id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("territoryId", territoryId), territoryMapper());
    }

    @Override
    @Transactional
    public void updateParent(UUID tenantId,
                             UUID territoryId,
                             UUID newParentId,
                             UUID updatedBy) {
        lockTenantHierarchy(tenantId);
        requireTerritory(tenantId, territoryId);
        if (newParentId != null) {
            requireTerritory(tenantId, newParentId);
            if (territoryId.equals(newParentId)
                    || wouldCreateCycle(tenantId, territoryId, newParentId)) {
                throw new TerritoryCycleException(tenantId, territoryId, newParentId);
            }
        }

        int rows = jdbc.update("""
                UPDATE crm_territories
                   SET parent_id=:parentId,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                """, new MapSqlParameterSource()
                .addValue("parentId", newParentId)
                .addValue("updatedBy", updatedBy)
                .addValue("tenantId", tenantId)
                .addValue("id", territoryId));
        if (rows != 1) {
            throw new TerritoryNotFoundException(tenantId, territoryId);
        }
        rebuildClosureLocked(tenantId);
    }

    @Override
    @Transactional
    public void rebuildClosure(UUID tenantId) {
        lockTenantHierarchy(tenantId);
        rebuildClosureLocked(tenantId);
    }

    private void rebuildClosureLocked(UUID tenantId) {
        jdbc.update(
                "DELETE FROM crm_territory_closure WHERE tenant_id=:tenantId",
                new MapSqlParameterSource().addValue("tenantId", tenantId));

        jdbc.update("""
                WITH RECURSIVE hierarchy AS (
                    SELECT territory.tenant_id,
                           territory.id AS ancestor_id,
                           territory.id AS descendant_id,
                           0 AS depth,
                           ARRAY[territory.id]::uuid[] AS visited
                      FROM crm_territories territory
                     WHERE territory.tenant_id=:tenantId
                    UNION ALL
                    SELECT child.tenant_id,
                           hierarchy.ancestor_id,
                           child.id,
                           hierarchy.depth + 1,
                           hierarchy.visited || child.id
                      FROM hierarchy
                      JOIN crm_territories child
                        ON child.tenant_id = hierarchy.tenant_id
                       AND child.parent_id = hierarchy.descendant_id
                     WHERE NOT child.id = ANY(hierarchy.visited)
                )
                INSERT INTO crm_territory_closure
                    (tenant_id, ancestor_id, descendant_id, depth)
                SELECT tenant_id, ancestor_id, descendant_id, depth
                  FROM hierarchy
                """, new MapSqlParameterSource().addValue("tenantId", tenantId));

        Integer expected = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territories
                 WHERE tenant_id=:tenantId
                """, new MapSqlParameterSource().addValue("tenantId", tenantId), Integer.class);
        Integer selfRows = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territory_closure
                 WHERE tenant_id=:tenantId
                   AND depth=0
                   AND ancestor_id=descendant_id
                """, new MapSqlParameterSource().addValue("tenantId", tenantId), Integer.class);
        if (expected == null || selfRows == null || !expected.equals(selfRows)) {
            throw new IllegalStateException(
                    "Territory closure rebuild incomplete for tenant=" + tenantId
                            + " territories=" + expected + " selfRows=" + selfRows);
        }
    }

    @Override
    @Transactional
    public void archive(UUID tenantId, UUID territoryId, UUID updatedBy) {
        lockTenantHierarchy(tenantId);
        int rows = jdbc.update("""
                UPDATE crm_territories
                   SET status='ARCHIVED',
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", territoryId)
                .addValue("updatedBy", updatedBy));
        if (rows != 1) {
            throw new TerritoryNotFoundException(tenantId, territoryId);
        }
    }

    @Override
    public boolean wouldCreateCycle(UUID tenantId, UUID territoryId, UUID proposedParentId) {
        if (territoryId.equals(proposedParentId)) {
            return true;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territory_closure
                 WHERE tenant_id=:tenantId
                   AND ancestor_id=:territoryId
                   AND descendant_id=:proposedParentId
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("territoryId", territoryId)
                .addValue("proposedParentId", proposedParentId), Integer.class);
        return count != null && count > 0;
    }

    private Territory requireTerritory(UUID tenantId, UUID territoryId) {
        return findById(tenantId, territoryId)
                .orElseThrow(() -> new TerritoryNotFoundException(tenantId, territoryId));
    }

    private void lockTenantHierarchy(UUID tenantId) {
        Long lockKey = jdbc.queryForObject("""
                WITH acquired AS (
                    SELECT pg_advisory_xact_lock(
                        hashtextextended(CAST(:tenantId AS text), 0)
                    )
                )
                SELECT hashtextextended(CAST(:tenantId AS text), 0)
                  FROM acquired
                """, new MapSqlParameterSource().addValue("tenantId", tenantId), Long.class);
        if (lockKey == null) {
            throw new IllegalStateException(
                    "Unable to acquire territory hierarchy lock for tenant=" + tenantId);
        }
    }
}
