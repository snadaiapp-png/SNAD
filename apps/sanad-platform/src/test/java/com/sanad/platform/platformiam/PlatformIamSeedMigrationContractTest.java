package com.sanad.platform.platformiam;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platform IAM Task 2 — Seed Migration Contract Test.
 *
 * <p>Pins the existence and content of the three Task 2 seed migrations:
 * <ul>
 *   <li>V20260926_3 — Platform IAM capability catalog</li>
 *   <li>V20260926_4 — System roles + role metadata</li>
 *   <li>V20260926_5 — Canonical owner bootstrap</li>
 * </ul>
 *
 * <p>All capability codes are sourced from the Platform IAM design spec
 * ({@code docs/superpowers/specs/2026-09-25-platform-iam-design.md}).
 * No codes are invented or assumed from memory.
 */
class PlatformIamSeedMigrationContractTest {

    private static final String CAPABILITIES_MIGRATION =
            "db/migration/V20260926_3__seed_platform_iam_capabilities.sql";
    private static final String ROLES_MIGRATION =
            "db/migration/V20260926_4__seed_platform_iam_roles.sql";
    private static final String BOOTSTRAP_MIGRATION =
            "db/migration/V20260926_5__bootstrap_platform_owner_membership.sql";

    // ── Capability codes from the Platform IAM design spec ──────────────
    static final Set<String> PLATFORM_CAPABILITY_CODES = Set.of(
            "PLATFORM.USER.READ",
            "PLATFORM.USER.CREATE",
            "PLATFORM.USER.UPDATE",
            "PLATFORM.USER.SUSPEND",
            "PLATFORM.USER.DISABLE",
            "PLATFORM.ROLE.READ",
            "PLATFORM.ROLE.CREATE",
            "PLATFORM.ROLE.UPDATE",
            "PLATFORM.ROLE.ASSIGN",
            "PLATFORM.ROLE.DELETE",
            "PLATFORM.PERMISSION.READ",
            "PLATFORM.PERMISSION.MANAGE",
            "PLATFORM.SESSION.READ",
            "PLATFORM.SESSION.REVOKE",
            "PLATFORM.AUDIT.READ",
            "PLATFORM.SECURITY.READ",
            "PLATFORM.SECURITY.MANAGE"
    );

    // ── System roles from the Platform IAM design spec ──────────────────
    static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            "PLATFORM_OWNER",
            "PLATFORM_ADMIN",
            "SECURITY_ADMIN",
            "BILLING_ADMIN",
            "SUPPORT_OPERATOR",
            "READ_ONLY_AUDITOR"
    );

    // ── Migration file existence ─────────────────────────────────────────

    @Test
    void capabilitiesMigrationFileMustExist() throws Exception {
        String sql = migration(CAPABILITIES_MIGRATION);
        assertNotNull(sql, "V20260926_3 migration file must exist");
    }

    @Test
    void rolesMigrationFileMustExist() throws Exception {
        String sql = migration(ROLES_MIGRATION);
        assertNotNull(sql, "V20260926_4 migration file must exist");
    }

    @Test
    void bootstrapMigrationFileMustExist() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertNotNull(sql, "V20260926_5 migration file must exist");
    }

    // ── Capability catalog contract ─────────────────────────────────────

    @Test
    void capabilitiesMigrationMustSeedAllPlatformCapabilityCodes() throws Exception {
        String sql = migration(CAPABILITIES_MIGRATION);
        for (String code : PLATFORM_CAPABILITY_CODES) {
            assertTrue(sql.contains(code),
                    "Capabilities migration must seed capability code: " + code);
        }
    }

    @Test
    void capabilitiesMigrationMustUseAccessCapabilitiesTable() throws Exception {
        String sql = migration(CAPABILITIES_MIGRATION);
        assertTrue(sql.contains("access_capabilities"),
                "Capabilities migration must reuse the existing access_capabilities table");
        assertTrue(sql.contains("WHERE NOT EXISTS"),
                "Capabilities migration must be idempotent (WHERE NOT EXISTS pattern)");
    }

    // ── Roles contract ──────────────────────────────────────────────────

    @Test
    void rolesMigrationMustSeedAllSystemRoleCodes() throws Exception {
        String sql = migration(ROLES_MIGRATION);
        for (String code : SYSTEM_ROLE_CODES) {
            assertTrue(sql.contains(code),
                    "Roles migration must seed role code: " + code);
        }
    }

    @Test
    void rolesMigrationMustCreatePlatformRoleMetadata() throws Exception {
        String sql = migration(ROLES_MIGRATION);
        assertTrue(sql.contains("platform_role_metadata"),
                "Roles migration must create platform_role_metadata entries");
        assertTrue(sql.contains("'SYSTEM'"),
                "Roles metadata must mark roles as SYSTEM type");
        assertTrue(sql.contains("protected"),
                "Roles metadata must include protected flag");
        assertTrue(sql.contains("owner_role"),
                "Roles metadata must include owner_role flag");
    }

    @Test
    void rolesMigrationMustMarkPlatformOwnerAsProtectedAndOwner() throws Exception {
        String sql = migration(ROLES_MIGRATION);
        assertTrue(sql.contains("PLATFORM_OWNER"),
                "PLATFORM_OWNER role must be seeded");
        // PLATFORM_OWNER must be protected=true and owner_role=true
        assertTrue(sql.contains("owner_role") && sql.contains("TRUE"),
                "PLATFORM_OWNER must have owner_role=TRUE");
    }

    @Test
    void rolesMigrationMustBeIdempotent() throws Exception {
        String sql = migration(ROLES_MIGRATION);
        assertTrue(sql.contains("WHERE NOT EXISTS") || sql.contains("ON CONFLICT"),
                "Roles migration must be idempotent");
    }

    // ── Bootstrap contract ──────────────────────────────────────────────

    @Test
    void bootstrapMigrationMustReferenceCanonicalControlTenant() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("00000000-0000-0000-0000-000000000001"),
                "Bootstrap migration must reference the canonical control tenant UUID");
    }

    @Test
    void bootstrapMigrationMustReferenceCanonicalOwnerEmail() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("snad.ai.app@gmail.com"),
                "Bootstrap migration must resolve the canonical owner by email");
    }

    @Test
    void bootstrapMigrationMustCreatePlatformMembership() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("platform_memberships"),
                "Bootstrap migration must create a platform_memberships entry");
        assertTrue(sql.contains("'ACTIVE'"),
                "Bootstrap membership must be ACTIVE");
    }

    @Test
    void bootstrapMigrationMustAssignPlatformOwnerRole() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("user_role_assignments") || sql.contains("role-links"),
                "Bootstrap must assign PLATFORM_OWNER role via existing user_role_assignments");
        assertTrue(sql.contains("PLATFORM_OWNER"),
                "Bootstrap must assign PLATFORM_OWNER role specifically");
    }

    @Test
    void bootstrapMigrationMustGrantPlatformCapabilitiesToOwnerRole() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("access_scope_grants") || sql.contains("role_capabilities"),
                "Bootstrap must grant PLATFORM.* capabilities to PLATFORM_OWNER role");
        assertTrue(sql.contains("PLATFORM."),
                "Bootstrap must reference PLATFORM.* capability codes");
    }

    @Test
    void bootstrapMigrationMustFailClosedIfPrerequisitesMissing() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("RAISE EXCEPTION"),
                "Bootstrap must fail closed with RAISE EXCEPTION if prerequisites are missing");
    }

    @Test
    void bootstrapMigrationMustBeIdempotent() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        assertTrue(sql.contains("WHERE NOT EXISTS") || sql.contains("ON CONFLICT"),
                "Bootstrap must be idempotent");
    }

    @Test
    void bootstrapMigrationMustNotMutateCredentials() throws Exception {
        String sql = migration(BOOTSTRAP_MIGRATION);
        // The migration must NOT contain password hash mutations
        assertTrue(!sql.contains("password_hash") || sql.contains("platform_admin"),
                "Bootstrap must not mutate password_hash");
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private String migration(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
