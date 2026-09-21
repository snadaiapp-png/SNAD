package com.sanad.platform.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalProjectOwnerAuthorityMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20260921_1__canonical_project_owner_full_authority.sql";

    @Test
    void migrationEnforcesSingleOwnerAndFullAdminAuthority() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "Forward migration must exist");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("snad.ai.app@gmail.com"));
            assertTrue(sql.contains("00000000-0000-0000-0000-000000000001"));
            assertTrue(sql.contains("00000000-0000-0000-0000-000000000010"));
            assertTrue(sql.contains("platform_admin = TRUE"));
            assertTrue(sql.contains("status = 'ARCHIVED'"),
                    "Foreign owner-email duplicates must be preserved but disabled");
            assertTrue(sql.contains("INSERT INTO role_capabilities"));
            assertTrue(sql.contains("c.status = 'ACTIVE'"),
                    "Control-plane ADMIN must receive every active capability");
            assertTrue(sql.contains("INSERT INTO access_scope_grants"));
            assertTrue(sql.contains("'TENANT'"),
                    "Scoped authorization must receive tenant-wide owner authority");
            assertTrue(sql.contains("Canonical project-owner email must resolve to exactly one active user"),
                    "Migration must fail closed if the owner remains ambiguous");
        }
    }
}
