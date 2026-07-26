package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.application.CrmEntitySnapshotPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** JDBC implementation of the CRM entity snapshot application port. */
@Component
public class JdbcCrmEntitySnapshotAdapter implements CrmEntitySnapshotPort {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "ACCOUNT", "CONTACT", "LEAD", "OPPORTUNITY", "ACTIVITY");

    private final JdbcTemplate jdbc;

    public JdbcCrmEntitySnapshotAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CrmEntitySnapshot load(UUID tenantId, String entityType, UUID entityId) {
        if (!SUPPORTED_TYPES.contains(entityType)) {
            return null;
        }

        String tableName = switch (entityType) {
            case "ACCOUNT" -> "crm_accounts";
            case "CONTACT" -> "crm_contacts";
            case "LEAD" -> "crm_leads";
            case "OPPORTUNITY" -> "crm_opportunities";
            case "ACTIVITY" -> "crm_activities";
            default -> throw new IllegalStateException("Unsupported allowlisted CRM entity type: " + entityType);
        };

        try {
            return jdbc.queryForObject(
                    "SELECT tenant_id, version, status FROM " + tableName
                            + " WHERE tenant_id = ? AND id = ?",
                    (rs, row) -> new CrmEntitySnapshot(
                            (UUID) rs.getObject("tenant_id"),
                            entityType,
                            entityId,
                            rs.getLong("version"),
                            rs.getString("status"),
                            !"ARCHIVED".equals(rs.getString("status"))
                                    && !"CANCELLED".equals(rs.getString("status"))),
                    tenantId, entityId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
