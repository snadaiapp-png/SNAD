package com.sanad.platform.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPlaneOwnerEmailMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20260828_1__canonicalize_control_plane_owner_email.sql";
    private static final String OWNER_ID = "00000000-0000-0000-0000-000000000010";
    private static final String CONTROL_PLANE_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String CANONICAL_EMAIL = "snad.ai.app@gmail.com";

    @Test
    void canonicalOwnerMigrationTargetsTheDeterministicControlPlaneOwner() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "A forward-only migration must canonicalize the project-owner identity");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains(OWNER_ID), "Migration must target the deterministic owner user id");
            assertTrue(sql.contains(CONTROL_PLANE_TENANT_ID), "Migration must stay scoped to the control-plane tenant");
            assertTrue(sql.contains(CANONICAL_EMAIL), "Migration must adopt the approved owner email");
            assertTrue(sql.contains("platform_admin = true"), "Canonical owner must retain platform-admin authority");
            assertTrue(sql.contains("status = 'ACTIVE'"), "Canonical owner must remain active");
            assertFalse(sql.contains("SET email = 'admin@snad.ai'"),
                    "The forward migration must never restore the legacy owner email");
        }
    }
}
