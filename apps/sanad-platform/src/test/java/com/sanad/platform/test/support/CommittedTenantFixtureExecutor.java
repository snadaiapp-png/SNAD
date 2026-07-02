package com.sanad.platform.test.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Stage 05A.2.5 §7.1 — Test-only fixture executor that creates and commits
 * tenant/user/membership/organization data in a REQUIRES_NEW transaction.
 *
 * <p>This ensures fixture data is committed to the database BEFORE any
 * REQUIRES_NEW transaction (like IdempotencyReservationTransactionExecutor)
 * attempts to read it. Without this, tests that create fixture data in
 * the test transaction would fail because REQUIRES_NEW transactions
 * cannot see uncommitted data.</p>
 *
 * <p>Also provides cleanup that deletes in the correct FK order to avoid
 * referential integrity violations from audit_events (append-only, FK
 * ON DELETE RESTRICT).</p>
 */
@Component
public class CommittedTenantFixtureExecutor {

    private final JdbcTemplate jdbc;

    public CommittedTenantFixtureExecutor(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Creates and commits a tenant, user, organization, and membership.
     * Returns a record with all created IDs.
     * Uses REQUIRES_NEW to ensure the data is committed independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FixtureData createFullFixture(String tenantName, String userEmail, String orgName) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        // Tenant
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, tenantName, "test-" + tenantId.toString().substring(0, 8));

        // User
        jdbc.update(
                "INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                userId, tenantId, userEmail, "Test User",
                "$2a$10$dummyhashvaluereplacedinrealusecase1234567890123456");

        // Organization
        jdbc.update(
                "INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                orgId, tenantId, orgName, "Test org");

        // Membership
        jdbc.update(
                "INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                membershipId, tenantId, orgId, userId, userEmail, "Test User");

        return new FixtureData(tenantId, userId, orgId, membershipId);
    }

    /**
     * Cleans up ALL test data in the correct FK order.
     * audit_events and audit_chain_heads have FK ON DELETE RESTRICT,
     * so they must be deleted FIRST (before tenants).
     * audit_events has append-only triggers that block DELETE on PostgreSQL,
     * but on H2 (local profile) the triggers are not present.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupAll() {
        // Delete in reverse dependency order
        // audit_events is append-only on PostgreSQL — DELETE will fail.
        // On H2 (local), there are no triggers, so DELETE succeeds.
        try { jdbc.execute("DELETE FROM audit_events"); } catch (Exception ignored) {}
        try { jdbc.execute("DELETE FROM audit_chain_heads"); } catch (Exception ignored) {}
        try { jdbc.execute("DELETE FROM idempotency_records"); } catch (Exception ignored) {}
        jdbc.execute("DELETE FROM user_role_assignments");
        jdbc.execute("DELETE FROM role_capabilities");
        jdbc.execute("DELETE FROM organization_memberships");
        jdbc.execute("DELETE FROM organizations");
        jdbc.execute("DELETE FROM refresh_tokens");
        jdbc.execute("DELETE FROM password_reset_tokens");
        jdbc.execute("DELETE FROM roles");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM tenants");
    }

    public record FixtureData(UUID tenantId, UUID userId, UUID organizationId, UUID membershipId) {}
}
