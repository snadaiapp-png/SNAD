package com.sanad.platform.access.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 (Users — Effective Access + Audit) structural gate.
 *
 * <p>Mirrors the repo-canonical {@code AccessAdminAuditContractTest} pattern:
 * the user administration mutations, the credential administration mutations,
 * and the role/capability mutation surfaces must carry the exact canonical
 * Phase 7 audit event names through the centralized audit adapter
 * ({@code AccessMutationAuditSupport} / {@code PlatformAuditWriter}) — no
 * parallel audit mechanism.</p>
 *
 * <p>Canonical Phase 7 contract (docs/superpowers/plans/
 * 2026-10-03-user-administration-module-access-expansion-implementation.md):</p>
 *
 * <pre>
 * USER_CREATED, USER_PROFILE_UPDATED, USER_USERNAME_CHANGED, USER_ACTIVATED,
 * USER_SUSPENDED, USER_DEACTIVATED, USER_ARCHIVED, USER_CREDENTIAL_INITIALIZED,
 * USER_RESET_LINK_ISSUED, USER_ROLE_GRANTED, USER_ROLE_REVOKED,
 * USER_SCOPE_CHANGED, ROLE_CAPABILITY_ATTACHED, ROLE_CAPABILITY_DETACHED
 * </pre>
 */
class UsersPhase7AuditContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/sanad/platform");

    @Test
    void userServiceLifecycleMutationsCarryCanonicalPhase7AuditWiring() throws Exception {
        String source = read("user/service/UserService.java");
        assertThat(source).contains("AccessMutationAuditSupport");
        assertThat(source).contains("USER_CREATED");
        assertThat(source).contains("USER_PROFILE_UPDATED");
        assertThat(source).contains("USER_USERNAME_CHANGED");
        assertThat(source).contains("USER_ACTIVATED");
        assertThat(source).contains("USER_SUSPENDED");
        assertThat(source).contains("USER_DEACTIVATED");
        assertThat(source).contains("USER_ARCHIVED");
    }

    @Test
    void bootstrapCredentialCreationAuditsCredentialInitialization() throws Exception {
        String source = read("user/service/UserService.java");
        // createUser(initialCredential=...) is an administrative credential
        // initialization: the credential bootstrap path must emit the canonical
        // credential event through the same audit adapter.
        assertThat(source).contains("USER_CREDENTIAL_INITIALIZED");
    }

    @Test
    void credentialAdministrationCarriesCanonicalPhase7AuditWiring() throws Exception {
        String initializer = read("security/service/AuthService.java");
        assertThat(initializer).contains("USER_CREDENTIAL_INITIALIZED");
        assertThat(initializer).contains("USER_RESET_LINK_ISSUED");

        String coordinator = read("security/notification/PasswordRecoveryNotificationCoordinator.java");
        assertThat(coordinator).contains("USER_RESET_LINK_ISSUED");
    }

    @Test
    void grantSurfacesUseCanonicalPhase7EventNames() throws Exception {
        String grants = read("access/grant/UserRoleGrantService.java");
        assertThat(grants).contains("USER_ROLE_GRANTED");
        assertThat(grants).contains("USER_ROLE_REVOKED");
        // Organization-scoped grant/revoke is the canonical governed scope
        // mutation; it must emit the distinct scope fact.
        assertThat(grants).contains("USER_SCOPE_CHANGED");
    }

    @Test
    void roleCapabilitySurfacesUseCanonicalPhase7EventNames() throws Exception {
        String roleCaps = read("access/role/RoleCapabilityService.java");
        assertThat(roleCaps).contains("ROLE_CAPABILITY_ATTACHED");
        assertThat(roleCaps).contains("ROLE_CAPABILITY_DETACHED");
    }

    @Test
    void credentialAuditPayloadsAreBuiltFromExplicitlySafeFieldSets() throws Exception {
        String initializer = read("security/service/AuthService.java");
        String coordinator = read("security/notification/PasswordRecoveryNotificationCoordinator.java");
        // The credential audit payload builders must be local, explicit, and
        // must NOT pass the raw request credential, the raw reset token, or the
        // delivery URL through to the audit writer.
        for (String source : new String[]{initializer, coordinator}) {
            String auditRegion = auditCallsRegion(source);
            assertThat(auditRegion).doesNotContain("initialCredential");
            assertThat(auditRegion).doesNotContain("rawToken");
            assertThat(auditRegion).doesNotContain("resetUrl");
            assertThat(auditRegion).doesNotContain("passwordHash");
        }
    }

    /** Extracts the audit call sites from a source file for payload inspection. */
    private static String auditCallsRegion(String source) {
        StringBuilder region = new StringBuilder();
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            if (line.contains("writeSuccess") || line.contains("writeFailure")
                    || line.contains("audit.success(")) {
                region.append(line).append('\n');
            }
        }
        return region.toString();
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
