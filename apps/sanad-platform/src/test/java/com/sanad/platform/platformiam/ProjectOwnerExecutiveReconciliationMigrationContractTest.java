package com.sanad.platform.platformiam;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectOwnerExecutiveReconciliationMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20261001_7__reconcile_project_owner_executive_permissions.sql";

    @Test
    void reconciliationMigrationMustExist() throws Exception {
        assertNotNull(migration());
    }

    @Test
    void reconciliationMustTargetCanonicalOwnerAndBothExecutiveRoles() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("snad.ai.app@gmail.com"));
        assertTrue(sql.contains("PLATFORM_OWNER"));
        assertTrue(sql.contains("ADMIN"));
        assertTrue(sql.contains("platform_admin = TRUE"));
    }

    @Test
    void reconciliationMustGrantEveryActiveCapabilityWithoutRlsBypass() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("access_capabilities"));
        assertTrue(sql.contains("c.status = 'ACTIVE'"));
        assertTrue(sql.contains("role_capabilities"));
        assertTrue(sql.contains("access_scope_grants"));
        assertTrue(sql.contains("scope_type = 'TENANT'") || sql.contains("'TENANT'"));
        assertTrue(sql.contains("set_config('app.tenant_id'"));
        assertTrue(!sql.toUpperCase().contains("BYPASSRLS"));
    }

    @Test
    void reconciliationMustBeIdempotentAndFailClosed() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertTrue(sql.contains("RAISE EXCEPTION"));
        assertTrue(sql.contains("missing"));
    }

    private String migration() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
