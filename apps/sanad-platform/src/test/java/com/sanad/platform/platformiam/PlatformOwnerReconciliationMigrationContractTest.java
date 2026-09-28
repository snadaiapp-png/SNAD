package com.sanad.platform.platformiam;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformOwnerReconciliationMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20261001_7__reconcile_canonical_platform_owner_admin_authority.sql";

    @Test
    void reconciliationMigrationMustExistAndBindCanonicalIdentity() throws Exception {
        String sql = migration();
        assertNotNull(sql, "canonical Platform Owner reconciliation migration must exist");
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000001"));
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000010"));
        assertTrue(sql.contains("snad.ai.app@gmail.com"));
    }

    @Test
    void reconciliationMustRequireActiveOwnerMembershipAndBothAuthorityLayers() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("platform_admin"));
        assertTrue(sql.contains("platform_memberships"));
        assertTrue(sql.contains("PLATFORM_OWNER"));
        assertTrue(sql.contains("code = 'ADMIN'") || sql.contains("code='ADMIN'"));
        assertTrue(sql.contains("user_role_assignments"));
        assertTrue(sql.contains("status = 'ACTIVE'") || sql.contains("'ACTIVE'"));
    }

    @Test
    void reconciliationMustGrantEveryActiveCapabilityThroughAuditableEngine() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("access_capabilities"));
        assertTrue(sql.contains("role_capabilities"));
        assertTrue(sql.contains("access_scope_grants"));
        assertTrue(sql.contains("status = 'ACTIVE'"));
        assertTrue(sql.contains("scope_type = 'TENANT'") || sql.contains("'TENANT'"));
        assertTrue(!sql.contains("BYPASSRLS"), "migration must never grant or depend on BYPASSRLS");
        assertTrue(!sql.contains("DISABLE ROW LEVEL SECURITY"));
    }

    @Test
    void reconciliationMustBeIdempotentAndFailClosed() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("WHERE NOT EXISTS") || sql.contains("ON CONFLICT"));
        assertTrue(sql.contains("RAISE EXCEPTION"));
        assertTrue(sql.contains("set_config('app.tenant_id'"));
    }

    @Test
    void reconciliationMustRejectPlatformOwnerOutsideControlTenantAndAvoidCredentialMutation() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("PLATFORM_OWNER") && sql.contains("!= control_tenant"));
        assertTrue(!sql.contains("password_hash ="));
        assertTrue(!sql.contains("password ="));
    }

    private String migration() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
