package com.sanad.platform.platformiam;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformIamSchemaMigrationContractTest {

    private static final String MEMBERSHIP_MIGRATION =
            "db/migration/V20260926_1__create_platform_iam_memberships.sql";
    private static final String ROLE_METADATA_MIGRATION =
            "db/migration/V20260926_2__create_platform_role_metadata.sql";

    @Test
    void platformMembershipSchemaMustEnforceControlTenantUserIdentity() throws Exception {
        String sql = migration(MEMBERSHIP_MIGRATION);

        assertTrue(sql.contains("UNIQUE (control_tenant_id, user_id)"));
        assertTrue(sql.contains("FOREIGN KEY (control_tenant_id, user_id)"));
        assertTrue(sql.contains("REFERENCES users(tenant_id, id)"));
        assertTrue(sql.contains("CREATE INDEX"));
        assertTrue(sql.contains("(control_tenant_id, status)"));
        assertTrue(sql.contains("(control_tenant_id, user_id)"));
    }

    @Test
    void platformMembershipSchemaMustRestrictLifecycleValues() throws Exception {
        String sql = migration(MEMBERSHIP_MIGRATION);

        assertTrue(sql.contains("'INVITED'"));
        assertTrue(sql.contains("'ACTIVE'"));
        assertTrue(sql.contains("'SUSPENDED'"));
        assertTrue(sql.contains("'LOCKED'"));
        assertTrue(sql.contains("'DISABLED'"));
        assertTrue(sql.contains("CHECK (status IN"));
    }

    @Test
    void platformRoleMetadataMustReferenceExistingControlTenantRole() throws Exception {
        String sql = migration(ROLE_METADATA_MIGRATION);

        assertTrue(sql.contains("UNIQUE (control_tenant_id, role_id)"));
        assertTrue(sql.contains("FOREIGN KEY (control_tenant_id, role_id)"));
        assertTrue(sql.contains("REFERENCES roles(tenant_id, id)"));
    }

    @Test
    void platformRoleMetadataMustExposeProtectedAndOwnerFlags() throws Exception {
        String sql = migration(ROLE_METADATA_MIGRATION);

        assertTrue(sql.contains("role_type"));
        assertTrue(sql.contains("protected"));
        assertTrue(sql.contains("owner_role"));
        assertTrue(sql.contains("'SYSTEM'"));
        assertTrue(sql.contains("'CUSTOM'"));
    }

    private String migration(String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Required Platform IAM migration must exist: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
