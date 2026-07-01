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

            // Insert USER.READ capability (global reference data)
            // V14 migration seeds capabilities, so it should already exist.
            // Use a try-catch to handle the case where it doesn't.
            try {
                capabilityId = jdbc.queryForObject(
                    "SELECT id FROM access_capabilities WHERE code = 'USER.READ'", UUID.class);
            } catch (Exception e) {
                // Capability doesn't exist — create it
                capabilityId = UUID.randomUUID();
                jdbc.update("INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at) VALUES (?, 'USER.READ', 'Read Users', 'Read user records', 'ACTIVE', NOW(), NOW())",
                        capabilityId);
            }

            // Link role to USER.READ capability in Tenant A
            UUID roleCapId = UUID.randomUUID();
            jdbc.update("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) VALUES (?, ?, ?, ?, NOW())",
                    roleCapId, tenantA, roleId, capabilityId);

            // Grant the role to User A in Tenant A
            jdbc.update("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    roleGrantId, tenantA, userA, roleId);

            return new TenantTestFixture(tenantA, tenantB, userA, userB,
                    pwHash, pwHash, null, null, null, null, roleId, capabilityId, roleGrantId);
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
