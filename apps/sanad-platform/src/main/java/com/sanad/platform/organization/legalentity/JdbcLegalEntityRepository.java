package com.sanad.platform.organization.legalentity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
                (rs, rowNum) -> new LegalEntity(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("registered_country_code"),
                        rs.getString("statutory_country_code"),
                        LegalEntityStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                tenantId, id
        ).stream().findFirst();
    }

    @Override
    public Optional<LegalEntity> findByTenantIdAndCode(UUID tenantId, String code) {
        return jdbc.query(
                "SELECT id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at FROM legal_entities WHERE tenant_id = ? AND code = ?",
                (rs, rowNum) -> new LegalEntity(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("registered_country_code"),
                        rs.getString("statutory_country_code"),
                        LegalEntityStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                tenantId, code
        ).stream().findFirst();
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
}
