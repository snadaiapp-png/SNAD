package com.sanad.platform.security.tenant.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

@TestConfiguration
@ConditionalOnProperty(name = "tenant.fixture.database.username")
public class TenantFixtureSeederConfig {

    @Bean
    public TenantFixtureSeeder tenantFixtureSeeder(
            @Qualifier("tenantFixtureDataSource") DataSource fixtureDs) {
        return new TenantFixtureSeederImpl(fixtureDs);
    }

    static class TenantFixtureSeederImpl implements TenantFixtureSeeder {

        private final JdbcTemplate jdbc;

        TenantFixtureSeederImpl(DataSource fixtureDs) {
            this.jdbc = new JdbcTemplate(fixtureDs);
        }

        @Override
        public TenantTestFixture seedCrudFixture() {
            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID capabilityId = UUID.randomUUID();
            UUID roleGrantId = UUID.randomUUID();
            String pwHash = "$2a$10$dummyhashvaluereplacedinrealusecase1234567890123456";

            // Insert tenants
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    tenantA, "Tenant A " + tenantA, "ta-" + tenantA);
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    tenantB, "Tenant B " + tenantB, "tb-" + tenantB);

            // Insert users
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    userA, tenantA, "alice-a@example.com", "Alice A", pwHash);
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    userB, tenantB, "bob-b@example.com", "Bob B", pwHash);

            // Insert a role in Tenant A
            jdbc.update("INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) VALUES (?, ?, 'ADMIN', 'Admin', 'Admin role', 'ACTIVE', NOW(), NOW())",
                    roleId, tenantA);

            // Grant multiple capabilities to Role A (V14 seeds these with fixed UUIDs)
            // ORGANIZATION.READ — for listing organizations
            // USER.READ — for listing users
            String[] capCodes = {"ORGANIZATION.READ", "USER.READ", "ORGANIZATION.CREATE", "ORGANIZATION.WRITE"};
            UUID firstCapId = null;
            for (String code : capCodes) {
                try {
                    UUID capId = jdbc.queryForObject(
                        "SELECT id FROM access_capabilities WHERE code = '" + code + "'", UUID.class);
                    if (firstCapId == null) firstCapId = capId;
                    UUID roleCapId = UUID.randomUUID();
                    jdbc.update("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) VALUES (?, ?, ?, ?, NOW())",
                            roleCapId, tenantA, roleId, capId);
                } catch (Exception e) {
                    // Capability not seeded by V14 — skip
                }
            }
            capabilityId = firstCapId != null ? firstCapId : UUID.randomUUID();

            // Grant the role to User A in Tenant A
            jdbc.update("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    roleGrantId, tenantA, userA, roleId);

            // Create Role B in Tenant B with same capabilities (independent)
            UUID roleBId = UUID.randomUUID();
            jdbc.update("INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) VALUES (?, ?, 'ADMIN', 'Admin B', 'Admin role B', 'ACTIVE', NOW(), NOW())",
                    roleBId, tenantB);
            for (String code : capCodes) {
                try {
                    UUID capId = jdbc.queryForObject(
                        "SELECT id FROM access_capabilities WHERE code = '" + code + "'", UUID.class);
                    UUID roleBCapId = UUID.randomUUID();
                    jdbc.update("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) VALUES (?, ?, ?, ?, NOW())",
                            roleBCapId, tenantB, roleBId, capId);
                } catch (Exception e) {
                    // skip
                }
            }
            UUID roleBGrantId = UUID.randomUUID();
            jdbc.update("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    roleBGrantId, tenantB, userB, roleBId);

            // --- Security chain fixtures (§6) ---
            // Suspended user in Tenant A
            UUID suspendedUserId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'SUSPENDED', ?, 0, NOW(), NOW())",
                    suspendedUserId, tenantA, "suspended@example.com", "Suspended", pwHash);

            // User with revoked membership (ACTIVE user, REVOKED grant)
            UUID revokedMembershipUserId = UUID.randomUUID();
            UUID revokedMembershipGrantId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    revokedMembershipUserId, tenantA, "revoked@example.com", "Revoked", pwHash);
            jdbc.update("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'REVOKED', NOW(), NOW())",
                    revokedMembershipGrantId, tenantA, revokedMembershipUserId, roleId);

            // Archived tenant with a user
            UUID archivedTenantId = UUID.randomUUID();
            UUID archivedTenantUserId = UUID.randomUUID();
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, ?, ?, 'ARCHIVED', NOW(), NOW())",
                    archivedTenantId, "Archived Tenant", "at-" + archivedTenantId);
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    archivedTenantUserId, archivedTenantId, "archived@example.com", "Archived", pwHash);

            // User without capability in Tenant A (ACTIVE, no role grant)
            UUID userWithoutCapId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, session_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    userWithoutCapId, tenantA, "nocap@example.com", "NoCap", pwHash);

            return new TenantTestFixture(tenantA, tenantB, userA, userB,
                    pwHash, pwHash, null, null, null, null, roleId, capabilityId, roleGrantId,
                    suspendedUserId, tenantA,
                    revokedMembershipUserId, tenantA, revokedMembershipGrantId,
                    archivedTenantId, archivedTenantUserId,
                    userWithoutCapId, tenantA,
                    roleBId, roleBGrantId);
        }

        @Override
        public TenantTestFixture seedPaginationFixture() {
            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();

            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    tenantA, "Tenant A " + tenantA, "pa-" + tenantA);
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    tenantB, "Tenant B " + tenantB, "pb-" + tenantB);

            for (int i = 1; i <= 3; i++) {
                UUID orgId = UUID.randomUUID();
                jdbc.update("INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                        orgId, tenantA, "Alpha-A" + i, "desc A" + i);
            }
            for (int i = 1; i <= 2; i++) {
                UUID orgId = UUID.randomUUID();
                jdbc.update("INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                        orgId, tenantB, "Bravo-B" + i, "desc B" + i);
            }

            return new TenantTestFixture(tenantA, tenantB, null, null,
                    null, null, null, null, null, null, null, null, null);
        }

        @Override
        public TenantTestFixture seedAuthFixture() {
            return seedCrudFixture(); // Reuse CRUD fixture for auth tests
        }

        @Override
        public TenantTestFixture seedCapabilityFixture() {
            return seedCrudFixture(); // Reuse CRUD fixture (has role grants)
        }

        @Override
        public TenantTestFixture seedSessionFixture() {
            return seedCrudFixture(); // Reuse CRUD fixture (has session version 0)
        }

        @Override
        public void incrementSessionVersion(UUID tenantId, UUID userId) {
            jdbc.update("UPDATE users SET session_version = session_version + 1 WHERE tenant_id = ? AND id = ?",
                    tenantId, userId);
        }

        @Override
        public void revokeRoleGrant(UUID tenantId, UUID grantId) {
            jdbc.update("UPDATE user_role_assignments SET status = 'REVOKED', updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                    tenantId, grantId);
        }

        @Override
        public void cleanup(TenantTestFixture fixture) {
            if (fixture == null) return;

            // Cleanup archived tenant first (if exists)
            if (fixture.archivedTenantId() != null) {
                jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", fixture.archivedTenantId());
                jdbc.update("DELETE FROM users WHERE tenant_id = ?", fixture.archivedTenantId());
                jdbc.update("DELETE FROM tenants WHERE id = ?", fixture.archivedTenantId());
            }

            // Cleanup Tenant A (includes all security fixture users)
            if (fixture.tenantAId() != null) {
                jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM organization_memberships WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM organizations WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM password_reset_tokens WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM roles WHERE tenant_id = ?", fixture.tenantAId());
                jdbc.update("DELETE FROM users WHERE tenant_id = ?", fixture.tenantAId());
            }
            // Cleanup Tenant B
            if (fixture.tenantBId() != null) {
                jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM organization_memberships WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM organizations WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM password_reset_tokens WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM roles WHERE tenant_id = ?", fixture.tenantBId());
                jdbc.update("DELETE FROM users WHERE tenant_id = ?", fixture.tenantBId());
            }
            if (fixture.tenantAId() != null) {
                jdbc.update("DELETE FROM tenants WHERE id = ?", fixture.tenantAId());
            }
            if (fixture.tenantBId() != null) {
                jdbc.update("DELETE FROM tenants WHERE id = ?", fixture.tenantBId());
            }
        }
    }
}
