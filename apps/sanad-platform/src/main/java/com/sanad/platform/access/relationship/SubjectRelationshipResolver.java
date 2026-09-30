package com.sanad.platform.access.relationship;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wave 1 minimal relationship-policy resolver backed by the canonical
 * {@code subject_relationships} table (FORCE RLS, tenant-jailed).
 *
 * <p>W1 semantics (fail-closed): an active relationship row is a candidate
 * RELATIONSHIP_POLICY source whose derived scope must then win Stage D scope
 * resolution. {@code TENANT} objects derive {@code TENANT_ALL}; employee
 * objects derive {@code OWN}; every other object type derives the object type
 * itself as the scope family. Task 12 extends this resolver with the
 * HR-scoped predicates without changing this contract.</p>
 */
@Component
public class SubjectRelationshipResolver implements RelationshipResolver {

    private static final String ACTIVE_WINDOW =
            " AND (valid_from IS NULL OR valid_from <= NOW()) "
                    + "AND (valid_until IS NULL OR valid_until > NOW()) ";

    private final JdbcTemplate jdbc;

    public SubjectRelationshipResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RelationshipPolicyGrant> resolveRelationshipGrant(
            UUID tenantId, UUID userId, UUID capabilityId, UUID organizationId) {
        List<RelationshipPolicyGrant> grants = jdbc.query(
                "SELECT id, relationship_type, object_type, object_id "
                        + "FROM subject_relationships "
                        + "WHERE tenant_id = ? AND subject_user_id = ?"
                        + ACTIVE_WINDOW
                        + "ORDER BY created_at, id",
                (rs, rowNum) -> new RelationshipPolicyGrant(
                        rs.getObject("id", UUID.class),
                        rs.getString("relationship_type"),
                        rs.getString("object_type"),
                        rs.getObject("object_id", UUID.class),
                        scopeFor(rs.getString("object_type"))),
                tenantId, userId);
        return grants.stream().findFirst();
    }

    @Override
    public boolean supportsRelationshipScope(
            UUID tenantId, UUID userId, String scopeType, UUID scopeReference) {
        if (scopeType == null) {
            return false;
        }
        String objectType = objectFamilyFor(scopeType);
        if (objectType == null) {
            return false;
        }
        Integer matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subject_relationships "
                        + "WHERE tenant_id = ? AND subject_user_id = ? "
                        + "AND object_type = ? "
                        + "AND (?::uuid IS NULL OR object_id = ?::uuid)"
                        + ACTIVE_WINDOW,
                Integer.class, tenantId, userId, objectType, scopeReference, scopeReference);
        return matches != null && matches > 0;
    }

    private static String scopeFor(String objectType) {
        return switch (objectType) {
            case "TENANT" -> "TENANT_ALL";
            case "EMPLOYEE" -> "OWN";
            default -> objectType;
        };
    }

    private static String objectFamilyFor(String scopeType) {
        return switch (scopeType) {
            case "TENANT_ALL" -> "TENANT";
            case "OWN", "SELF" -> "EMPLOYEE";
            case "TEAM", "DEPARTMENT", "ORG_UNIT", "BRANCH", "BUSINESS_UNIT",
                    "LEGAL_ENTITY", "ORGANIZATION", "PROJECT" -> scopeType;
            default -> null;
        };
    }
}
