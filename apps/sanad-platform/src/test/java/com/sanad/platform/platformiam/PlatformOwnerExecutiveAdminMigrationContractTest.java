package com.sanad.platform.platformiam;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class PlatformOwnerExecutiveAdminMigrationContractTest {
    private static final String MIGRATION = "db/migration/V20261001_7__reconcile_canonical_platform_owner_executive_admin.sql";

    @Test void reconciliationMigrationMustExist() throws Exception { assertTrue(migration().contains("00000000-0000-0000-0000-000000000001")); }
    @Test void reconciliationMustTargetOnlyTheCanonicalOwnerIdentity() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000001"));
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000010"));
        assertTrue(sql.contains("snad.ai.app@gmail.com"));
        assertTrue(sql.contains("PLATFORM_OWNER"));
        assertTrue(sql.contains("ADMIN"));
        assertFalse(sql.contains("GENERAL_MANAGER"));
    }
    @Test void reconciliationMustUseTheRealCapabilityEngineWithoutWildcardBypass() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("access_capabilities"));
        assertTrue(sql.contains("role_capabilities"));
        assertTrue(sql.contains("access_scope_grants"));
        assertTrue(sql.contains("status = 'ACTIVE'") || sql.contains("status='ACTIVE'"));
        assertFalse(sql.contains("LIKE '%.%'") || sql.contains("LIKE '%'"));
    }
    @Test void reconciliationMustBeIdempotentAndFailClosed() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertTrue(sql.contains("RAISE EXCEPTION"));
        assertTrue(sql.contains("set_config('app.tenant_id'"));
    }
    @Test void reconciliationMustPreserveRlsAndCredentials() throws Exception {
        String sql = migration();
        String upper = sql.toUpperCase(Locale.ROOT);
        String lower = sql.toLowerCase(Locale.ROOT);
        assertFalse(upper.contains("BYPASSRLS"));
        assertFalse(upper.contains("DISABLE ROW LEVEL SECURITY"));
        assertFalse(upper.contains("NO FORCE ROW LEVEL SECURITY"));
        assertFalse(lower.contains("password_hash"));
        assertFalse(lower.contains("password ="));
    }
    @Test void reconciliationMustVerifyAllActiveCapabilitiesAndTenantIsolation() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("missing_capability_count"));
        assertTrue(sql.contains("missing_scope_count"));
        assertTrue(sql.contains("PLATFORM_OWNER") && sql.contains("control_tenant"));
        assertTrue(sql.contains("tenant_id != control_tenant") || sql.contains("tenant_id <> control_tenant"));
    }
    private String migration() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            if (is == null) return fail("Expected reconciliation migration is missing: " + MIGRATION);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
