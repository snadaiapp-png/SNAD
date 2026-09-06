package com.sanad.platform.organization.legalentity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLegalEntityOrganizationEligibilityRepository implements LegalEntityOrganizationEligibilityRepository {

    private final JdbcTemplate jdbc;

    public JdbcLegalEntityOrganizationEligibilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isEligibleOn(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM organization_legal_entities
                WHERE tenant_id = ?
                  AND legal_entity_id = ?
                  AND organization_id = ?
                  AND status = 'ACTIVE'
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """, Integer.class, tenantId, legalEntityId, organizationId,
                Date.valueOf(effectiveDate), Date.valueOf(effectiveDate));
        return count != null && count > 0;
    }

    @Override
    public Optional<LegalEntityOrganizationEligibility> findActiveOn(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate) {
        return jdbc.query(
                """
                SELECT id, tenant_id, organization_id, legal_entity_id, effective_from, effective_to, status, created_at
                FROM organization_legal_entities
                WHERE tenant_id = ?
                  AND legal_entity_id = ?
                  AND organization_id = ?
                  AND status = 'ACTIVE'
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """,
                (rs, rowNum) -> new LegalEntityOrganizationEligibility(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("legal_entity_id", UUID.class),
                        rs.getDate("effective_from").toLocalDate(),
                        rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                tenantId, legalEntityId, organizationId,
                Date.valueOf(effectiveDate), Date.valueOf(effectiveDate)
        ).stream().findFirst();
    }
}
