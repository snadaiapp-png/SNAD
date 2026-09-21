package com.sanad.platform.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectOwnerGlobalPermissionsMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20260921_1__project_owner_global_permissions.sql";

    @Test
    void migrationEnforcesSingleCanonicalOwnerAndFullAdminAuthority() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "Project-owner permissions migration must exist");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("snad.ai.app@gmail.com"));
            assertTrue(sql.contains("00000000-0000-0000-0000-000000000010"));
            assertTrue(sql.contains("00000000-0000-0000-0000-000000000001"));
            assertTrue(sql.contains("platform_admin = TRUE"));
            assertTrue(sql.contains("c.status = 'ACTIVE'"));
            assertTrue(sql.contains("INSERT INTO role_capabilities"));
            assertTrue(sql.contains("INSERT INTO access_scope_grants"));
            assertTrue(sql.contains("scope_type = 'TENANT'"));
            assertTrue(sql.contains("status = 'ARCHIVED'"));
            assertTrue(sql.contains("Duplicate canonical project-owner login still exists"));
        }
    }
}
