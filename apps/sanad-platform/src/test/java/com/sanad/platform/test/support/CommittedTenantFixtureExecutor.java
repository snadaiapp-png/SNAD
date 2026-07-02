package com.sanad.platform.test.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Stage 05A.2.6 §1 — Test-only fixture executor that creates and commits
 * tenant/user/membership/organization data in a REQUIRES_NEW transaction.
 *
 * <p>Uses the FIXTURE DataSource (sanad_fixture_ci, BYPASSRLS) explicitly
 * via @Qualifier to avoid DataSource ambiguity.</p>
 */
@Component
@ConditionalOnBean(name = "tenantFixtureDataSource")
public class CommittedTenantFixtureExecutor {

    private final JdbcTemplate jdbc;

    public CommittedTenantFixtureExecutor(
            @Qualifier("tenantFixtureDataSource") DataSource fixtureDataSource) {
        this.jdbc = new JdbcTemplate(fixtureDataSource);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FixtureData createFullFixture(String tenantName, String userEmail, String orgName) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, tenantName, "test-" + tenantId.toString().substring(0, 8));

        jdbc.update(
                "INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                userId, tenantId, userEmail, "Test User",
                "$2a$10$dummyhashvaluereplacedinrealusecase1234567890123456");

        jdbc.update(
                "INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                orgId, tenantId, orgName, "Test org");

        jdbc.update(
                "INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                membershipId, tenantId, orgId, userId, userEmail, "Test User");

        return new FixtureData(tenantId, userId, orgId, membershipId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupAll() {
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
