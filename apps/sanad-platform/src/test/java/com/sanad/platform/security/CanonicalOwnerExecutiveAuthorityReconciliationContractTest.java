package com.sanad.platform.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalOwnerExecutiveAuthorityReconciliationContractTest {

    private static final String MIGRATION =
            "db/migration/V20261002_1__reconcile_canonical_owner_executive_authority.sql";

    @Test
    void reconciliationKeepsCanonicalOwnerActiveAndAssignsBothExecutiveRoles() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("snad.ai.app@gmail.com"));
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000010"));
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000001"));
        assertTrue(sql.contains("platform_admin = TRUE"));
        assertTrue(sql.contains("code = 'ADMIN'"));
        assertTrue(sql.contains("code = 'PLATFORM_OWNER'"));
        assertTrue(sql.contains("user_role_assignments"));
    }

    @Test
    void reconciliationGrantsEveryActiveCapabilityWithoutBypassingTenantIsolation() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("c.status = 'ACTIVE'"));
        assertTrue(sql.contains("INSERT INTO role_capabilities"));
        assertTrue(sql.contains("INSERT INTO access_scope_grants"));
        assertTrue(sql.contains("scope_type"));
        assertTrue(sql.contains("'TENANT'"));
        assertTrue(sql.contains("set_config('app.tenant_id'"));
        assertTrue(sql.contains("missing_capability_count"));
        assertTrue(sql.contains("missing_scope_count"));
        assertTrue(!sql.contains("BYPASSRLS"));
        assertTrue(!sql.contains("DISABLE ROW LEVEL SECURITY"));
    }

    @Test
    void reconciliationIsForwardOnlyIdempotentFailClosedAndCredentialSafe() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertTrue(sql.contains("RAISE EXCEPTION"));
        assertTrue(!sql.contains("password_hash ="));
        assertTrue(!sql.contains("ALTER ROLE"));
    }

    private String migration() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "Executive-authority reconciliation migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
