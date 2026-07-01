package com.sanad.platform.security.tenant.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Stage 04A.3.1 §4 — Implementation of {@link TenantFixtureSeeder}.
 *
 * <p>Uses the Fixture DataSource (migration_owner account) to create
 * test data WITHOUT RLS restrictions. The Runtime DataSource
 * (sanad_runtime_app) is NEVER used for fixture creation.</p>
 */
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
            String pwHash = "$2a$10$dummyhashvaluereplacedinrealusecase1234567890123456";

            // Insert tenants (no RLS on tenants table)
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", tenantA, "Tenant A " + tenantA, "ta-" + tenantA);
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", tenantB, "Tenant B " + tenantB, "tb-" + tenantB);

            // Insert users (RLS-protected, but fixture DS bypasses RLS)
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, " +
                    "password_hash, session_version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    userA, tenantA, "alice-a@example.com", "Alice A", pwHash);
            jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, " +
                    "password_hash, session_version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())",
                    userB, tenantB, "bob-b@example.com", "Bob B", pwHash);

            return new TenantTestFixture(tenantA, tenantB, userA, userB,
                    pwHash, pwHash, null, null, null, null, null, null, null);
        }

        @Override
        public TenantTestFixture seedPaginationFixture() {
            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            UUID orgA = UUID.randomUUID();
            UUID orgB = UUID.randomUUID();

            // Insert tenants
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", tenantA, "Tenant A " + tenantA, "pa-" + tenantA);
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", tenantB, "Tenant B " + tenantB, "pb-" + tenantB);

            // Insert organizations for tenant A (3 orgs)
            for (int i = 1; i <= 3; i++) {
                UUID orgId = (i == 1) ? orgA : UUID.randomUUID();
                jdbc.update("INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                        orgId, tenantA, "Alpha-A" + i, "desc A" + i);
            }

            // Insert organizations for tenant B (2 orgs)
            for (int i = 1; i <= 2; i++) {
                UUID orgId = (i == 1) ? orgB : UUID.randomUUID();
                jdbc.update("INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                        orgId, tenantB, "Bravo-B" + i, "desc B" + i);
            }

            return new TenantTestFixture(tenantA, tenantB, null, null,
                    null, null, orgA, orgB, null, null, null, null, null);
        }

        @Override
        public void cleanup(TenantTestFixture fixture) {
            if (fixture == null) return;

            // Delete ALL users for these tenants first (including test-created ones)
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
