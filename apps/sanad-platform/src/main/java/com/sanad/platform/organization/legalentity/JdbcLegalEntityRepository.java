package com.sanad.platform.organization.legalentity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLegalEntityRepository implements LegalEntityRepository {

    private final JdbcTemplate jdbc;

    public JdbcLegalEntityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LegalEntity> findByTenantIdAndId(UUID tenantId, UUID id) {
        return jdbc.query(
                "SELECT id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at FROM legal_entities WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> mapLegalEntity(rs),
                tenantId, id
        ).stream().findFirst();
    }

    @Override
    public Optional<LegalEntity> findByTenantIdAndCode(UUID tenantId, String code) {
        return jdbc.query(
                "SELECT id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at FROM legal_entities WHERE tenant_id = ? AND code = ?",
                (rs, rowNum) -> mapLegalEntity(rs),
                tenantId, code
        ).stream().findFirst();
    }

    @Override
    public List<LegalEntity> findActiveEligibleForOrganization(
            UUID tenantId,
            UUID organizationId,
            LocalDate effectiveDate) {
        return jdbc.query("""
                SELECT DISTINCT le.id, le.tenant_id, le.code, le.name,
                       le.registered_country_code, le.statutory_country_code,
                       le.status, le.created_at, le.updated_at
                FROM legal_entities le
                JOIN organization_legal_entities ole
                  ON ole.tenant_id = le.tenant_id
                 AND ole.legal_entity_id = le.id
                WHERE le.tenant_id = ?
                  AND ole.organization_id = ?
                  AND le.status = 'ACTIVE'
                  AND ole.effective_from <= ?
                  AND (ole.effective_to IS NULL OR ole.effective_to >= ?)
                ORDER BY le.code, le.id
                """,
                (rs, rowNum) -> mapLegalEntity(rs),
                tenantId, organizationId, effectiveDate, effectiveDate);
    }

    @Override
    public LegalEntity save(LegalEntity entity) {
        if (entity.id() == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """, id, entity.tenantId(), entity.code(), entity.name(),
                    entity.registeredCountryCode(), entity.statutoryCountryCode(),
                    entity.status().name());
            return findByTenantIdAndId(entity.tenantId(), id).orElseThrow();
        } else {
            jdbc.update("""
                    UPDATE legal_entities SET code = ?, name = ?, registered_country_code = ?, statutory_country_code = ?, status = ?, updated_at = NOW()
                    WHERE tenant_id = ? AND id = ?
                    """, entity.code(), entity.name(),
                    entity.registeredCountryCode(), entity.statutoryCountryCode(),
                    entity.status().name(), entity.tenantId(), entity.id());
            return findByTenantIdAndId(entity.tenantId(), entity.id()).orElseThrow();
        }
    }

    private LegalEntity mapLegalEntity(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LegalEntity(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("registered_country_code"),
                rs.getString("statutory_country_code"),
                LegalEntityStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
