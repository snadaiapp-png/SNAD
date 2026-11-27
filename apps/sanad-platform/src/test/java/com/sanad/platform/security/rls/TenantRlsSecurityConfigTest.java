package com.sanad.platform.security.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Decision-table tests for {@link TenantRlsSecurityConfig} — the fail-safe
 * startup contract for the signed tenant-context binding (G1 F-INT-05/06).
 *
 * <p>Proves that missing or incorrect PRODUCTION configuration leads to a
 * safe, explicit rejection (context refuses to start) and that test settings
 * can never leak onto the production path:</p>
 *
 * <ul>
 *   <li>prod profile + unset topology → STARTUP FAILURE (no test fallback).</li>
 *   <li>separated topology without an explicit key → STARTUP FAILURE.</li>
 *   <li>separated topology with the documented fixture key → STARTUP FAILURE.</li>
 *   <li>unknown topology value → STARTUP FAILURE.</li>
 *   <li>separated + strict disablement without the governance triple → STARTUP
 *       FAILURE; with the full triple → allowed and audited.</li>
 *   <li>fixture topologies → fixture-key fallback with the fixture signer
 *       installed (disposable topologies only).</li>
 * </ul>
 */
@DisplayName("TenantRlsSecurityConfig — deployment-topology and strictness governance")
class TenantRlsSecurityConfigTest {

    private static final String PROD_KEY_HEX =
            "aa1122334455667700aabbccddeeff00112233445566778899aabbccddeeff00";

    @AfterEach
    void tearDown() {
        TenantRlsRuntimePolicy.reset();
    }

    private MockEnvironment env(String topology, String... profileAndKey) {
        MockEnvironment environment = new MockEnvironment();
        if (topology != null) {
            environment.setProperty("snad.rls.deployment-topology", topology);
        }
        for (int i = 0; i < profileAndKey.length; i += 2) {
            environment.setProperty(profileAndKey[i], profileAndKey[i + 1]);
        }
        return environment;
    }

    private void withProdProfile(MockEnvironment environment) {
        environment.setActiveProfiles("prod");
    }

    // ── F-INT-05: production fail-safe ───────────────────────────────────

    @Test
    @DisplayName("prod profile with UNSET topology refuses to start (no test fallback)")
    void prodWithUnsetTopologyFails() {
        MockEnvironment env = env(null);
        withProdProfile(env);
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_TOPOLOGY_UNDECLARED_IN_PRODUCTION");
        // fail-closed default installed
        assertThat(TenantRlsRuntimePolicy.current().signer()).isNull();
        assertThat(TenantRlsRuntimePolicy.current().strictMissingContext()).isTrue();
    }

    @Test
    @DisplayName("separated topology without an explicit key refuses to start")
    void separatedWithoutKeyFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_SEPARATED);
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_KEY_REQUIRED");
    }

    @Test
    @DisplayName("separated topology REJECTS the documented fixture key unconditionally")
    void separatedWithFixtureKeyFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_SEPARATED,
                "snad.rls.context-hmac-key", TenantRlsSecurityConfig.DOCUMENTED_FIXTURE_KEY_HEX);
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_FIXTURE_KEY_REJECTED");
    }

    @Test
    @DisplayName("prod profile REJECTS the documented fixture key even on a declared fixture topology")
    void prodProfileWithFixtureKeyFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_FIXTURE,
                "snad.rls.context-hmac-key", TenantRlsSecurityConfig.DOCUMENTED_FIXTURE_KEY_HEX);
        withProdProfile(env);
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_FIXTURE_KEY_REJECTED");
    }

    @Test
    @DisplayName("unknown topology value refuses to start")
    void unknownTopologyFails() {
        MockEnvironment env = env("staging-something");
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_TOPOLOGY_UNKNOWN");
    }

    @Test
    @DisplayName("separated topology with a valid explicit key installs a production signer")
    void separatedWithValidKeyInstallsSigner() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_SEPARATED,
                "snad.rls.context-hmac-key", PROD_KEY_HEX);
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().signer()).isNotNull();
        assertThat(TenantRlsRuntimePolicy.current().signer().isFixtureFallback()).isFalse();
        assertThat(TenantRlsRuntimePolicy.current().strictMissingContext()).isTrue();
    }

    @Test
    @DisplayName("prod profile with UNSET topology but rls.enabled=false does not fail (no wrapping, fail-closed default)")
    void rlsDisabledInstallsFailClosedDefault() {
        MockEnvironment env = env(null, "snad.rls.enabled", "false");
        withProdProfile(env);
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().signer()).isNull();
        assertThat(TenantRlsRuntimePolicy.current().strictMissingContext()).isTrue();
    }

    // ── F-INT-05: fixture topologies ─────────────────────────────────────

    @Test
    @DisplayName("fixture topology without a key falls back to the documented fixture key")
    void fixtureTopologyFallsBackToFixtureKey() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_FIXTURE);
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().signer()).isNotNull();
        assertThat(TenantRlsRuntimePolicy.current().signer().isFixtureFallback()).isTrue();
    }

    @Test
    @DisplayName("UNSET topology on a NON-prod profile falls back to the fixture key (developer convenience)")
    void unsetNonProdFallsBackToFixtureKey() {
        MockEnvironment env = env(null);
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().signer()).isNotNull();
        assertThat(TenantRlsRuntimePolicy.current().signer().isFixtureFallback()).isTrue();
    }

    @Test
    @DisplayName("invalid key material (short / non-hex) refuses to start")
    void invalidKeyMaterialFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_FIXTURE,
                "snad.rls.context-hmac-key", "abc123");
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_KEY_INVALID");
    }

    // ── F-INT-06: strictness governance ──────────────────────────────────

    @Test
    @DisplayName("disabling strictness on the PRODUCTION path without governance refuses to start")
    void productionStrictDisableWithoutGovernanceFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_SEPARATED,
                "snad.rls.context-hmac-key", PROD_KEY_HEX,
                "snad.rls.strict-missing-context", "false");
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_STRICT_DISABLE_UNAUTHORIZED");
    }

    @Test
    @DisplayName("disabling strictness on the prod PROFILE without governance refuses to start")
    void prodProfileStrictDisableWithoutGovernanceFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_FIXTURE,
                "snad.rls.context-hmac-key", PROD_KEY_HEX,
                "snad.rls.strict-missing-context", "false");
        withProdProfile(env);
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_STRICT_DISABLE_UNAUTHORIZED");
    }

    @Test
    @DisplayName("partial governance (missing fields) on the production path refuses to start")
    void productionPartialGovernanceFails() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_SEPARATED,
                "snad.rls.context-hmac-key", PROD_KEY_HEX,
                "snad.rls.strict-missing-context", "false",
                "snad.rls.strict-missing-context.governance-approved", "true",
                "snad.rls.strict-missing-context.approved-by", "security-council");
        assertThatThrownBy(() -> new TenantRlsSecurityConfig(env).resolveAndInstall())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CTX_STRICT_DISABLE_UNAUTHORIZED");
    }

    @Test
    @DisplayName("the full governance triple allows production disablement (audited)")
    void productionStrictDisableWithFullGovernanceAllowed() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_SEPARATED,
                "snad.rls.context-hmac-key", PROD_KEY_HEX,
                "snad.rls.strict-missing-context", "false",
                "snad.rls.strict-missing-context.governance-approved", "true",
                "snad.rls.strict-missing-context.approved-by", "security-council",
                "snad.rls.strict-missing-context.change-record", "G1-CHANGE-2026-0001");
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().strictMissingContext()).isFalse();
        assertThat(TenantRlsRuntimePolicy.current().signer()).isNotNull();
    }

    @Test
    @DisplayName("fixture topologies may disable strictness freely (disposable)")
    void fixtureStrictDisableAllowed() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_FIXTURE,
                "snad.rls.strict-missing-context", "false");
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().strictMissingContext()).isFalse();
    }

    @Test
    @DisplayName("strictness defaults to STRICT when unspecified")
    void strictnessDefaultsToStrict() {
        MockEnvironment env = env(TenantRlsSecurityConfig.TOPOLOGY_FIXTURE);
        new TenantRlsSecurityConfig(env).resolveAndInstall();
        assertThat(TenantRlsRuntimePolicy.current().strictMissingContext()).isTrue();
    }
}
