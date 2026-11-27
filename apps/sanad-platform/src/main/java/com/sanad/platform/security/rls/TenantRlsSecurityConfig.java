package com.sanad.platform.security.rls;

import java.util.Arrays;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Resolves the SIGNED tenant-context runtime policy at startup and installs
 * it into {@link TenantRlsRuntimePolicy} — the application-side completion of
 * the V20261124_1/V20261124_2 signed binding contract (G1 F-INT-01/03/04/05/06).
 *
 * <h3>Deployment-topology decision table (F-INT-05 — fail-safe, explicit)</h3>
 *
 * <table>
 *   <tr><th>snad.rls.deployment-topology</th><th>prod profile active</th><th>behavior</th></tr>
 *   <tr><td>{@code separated}</td><td>any</td><td>PRODUCTION PATH: an explicit HMAC key is
 *       REQUIRED (missing → startup failure); the documented fixture key is REJECTED
 *       unconditionally; strictness disablement requires the governance triple.</td></tr>
 *   <tr><td>{@code migration-fixture}</td><td>any</td><td>disposable topology: explicit key or the
 *       documented fixture-key fallback (loud WARNING); strictness disablement allowed (WARNING).</td></tr>
 *   <tr><td>unset</td><td>yes</td><td>STARTUP FAILURE — a production deployment that does not
 *       declare its topology must NOT fall back to test settings or a published test key.</td></tr>
 *   <tr><td>unset</td><td>no</td><td>fixture-key fallback with loud WARNING (developer convenience
 *       only; never production evidence).</td></tr>
 *   <tr><td>unknown value</td><td>any</td><td>STARTUP FAILURE — ambiguity is fail-closed.</td></tr>
 * </table>
 *
 * <h3>Strictness governance (F-INT-06)</h3>
 *
 * <p>{@code snad.rls.strict-missing-context=false} disables the loud rejection
 * of in-transaction statements without tenant context. On the production path
 * ({@code separated} topology) this is accepted ONLY with the full governance
 * triple — {@code snad.rls.strict-missing-context.governance-approved=true}
 * plus non-empty {@code snad.rls.strict-missing-context.approved-by} and
 * {@code snad.rls.strict-missing-context.change-record} — otherwise startup
 * fails with an explicit error. The operational necessity of the flag is the
 * emergency-compatibility valve for a misclassified legacy path; its limit is
 * that it NEVER relaxes Row-Level Security itself (the enforced policies fail
 * closed on unbound sessions regardless of this flag) and it is audited at
 * startup in the logs.</p>
 *
 * <h3>Key format</h3>
 *
 * <p>Hex-encoded, minimum 32 bytes (64 hex chars) — the same encoding the
 * operator provisions into {@code platform_tenant_ctx_keys} via
 * {@code scripts/ops/tenant-ctx/provision-ctx-key.sql}. The value is read from
 * the environment ({@code SANAD_TENANT_CTX_HMAC_KEY}) through the property
 * placeholder; it is never defaulted, logged, or embedded.</p>
 */
@Component
public class TenantRlsSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsSecurityConfig.class);

    /** Documented fixture key — repository-published BY DESIGN for disposable topologies only. */
    static final String DOCUMENTED_FIXTURE_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    static final String TOPOLOGY_SEPARATED = "separated";
    static final String TOPOLOGY_FIXTURE = "migration-fixture";

    private final Environment environment;

    public TenantRlsSecurityConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Validate configuration and install the effective policy. Any violation
     * throws — the Spring context refuses to start (safe, explicit rejection).
     */
    @PostConstruct
    void resolveAndInstall() {
        boolean rlsEnabled = environment.getProperty("snad.rls.enabled", Boolean.class, true);
        if (!rlsEnabled) {
            // snad.rls.enabled=false skips the wrapping entirely; install the
            // fail-closed default anyway so no code path can bind silently.
            TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(null, true));
            log.warn("Tenant RLS disabled (snad.rls.enabled=false) — TenantRlsDataSourcePostProcessor "
                    + "will not wrap any DataSource; signed tenant-context binding is unavailable.");
            return;
        }

        String topology = normalize(environment.getProperty("snad.rls.deployment-topology"));
        boolean prodProfile = activeProfilesIncludeProd();

        ResolvedKey resolved = resolveSigningKey(topology, prodProfile);
        boolean strict = resolveStrictness(topology, prodProfile);

        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(resolved.signer(), strict));

        log.info("Tenant RLS signed-context binding ACTIVE: topology={}, keySource={}, "
                        + "strictMissingContext={} (F-INT-01/03/04/05/06 integration)",
                topology == null ? "<unset:fixture-fallback>" : topology,
                resolved.description(),
                strict ? "STRICT" : "DISABLED(governed)");
        if (resolved.signer().isFixtureFallback()) {
            log.warn("SANAD_CTX_FIXTURE_KEY_IN_USE: the documented fixture signing key is active on a "
                    + "disposable topology — this deployment is NOT production evidence and holds no "
                    + "real tenant data.");
        }
    }

    // ── topology + key resolution ────────────────────────────────────────

    private ResolvedKey resolveSigningKey(String topology, boolean prodProfile) {
        String rawKey = normalize(environment.getProperty("snad.rls.context-hmac-key"));

        if (TOPOLOGY_SEPARATED.equals(topology)) {
            if (rawKey == null) {
                throw new IllegalStateException(
                        "SANAD_CTX_KEY_REQUIRED: production topology 'separated' requires an explicit "
                                + "HMAC signing key (snad.rls.context-hmac-key / SANAD_TENANT_CTX_HMAC_KEY). "
                                + "Provision it via scripts/ops/tenant-ctx/provision-ctx-key.sql from the "
                                + "environment — startup refused (fail-safe).");
            }
            if (DOCUMENTED_FIXTURE_KEY_HEX.equals(rawKey)) {
                throw new IllegalStateException(
                        "SANAD_CTX_FIXTURE_KEY_REJECTED: the documented fixture signing key must never "
                                + "run on the production topology 'separated'. Refusing to start.");
            }
            return new ResolvedKey(new TenantContextSigner(decodeHexKey(rawKey), false), "explicit production key");
        }

        boolean fixtureTopology = TOPOLOGY_FIXTURE.equals(topology);
        if (topology == null && prodProfile) {
            // F-INT-05: an undeclared topology in production must NOT activate
            // test settings or a published test key.
            throw new IllegalStateException(
                    "SANAD_CTX_TOPOLOGY_UNDECLARED_IN_PRODUCTION: snad.rls.deployment-topology is unset "
                            + "under the prod profile. Declare 'separated' (production) explicitly with a "
                            + "provisioned signing key — refusing to fall back to fixture/test settings.");
        }
        if (topology != null && !fixtureTopology) {
            throw new IllegalStateException(
                    "SANAD_CTX_TOPOLOGY_UNKNOWN: snad.rls.deployment-topology='" + topology
                            + "' (expected separated|migration-fixture) — refusing to start (fail-closed).");
        }

        if (rawKey != null) {
            if (DOCUMENTED_FIXTURE_KEY_HEX.equals(rawKey) && prodProfile) {
                throw new IllegalStateException(
                        "SANAD_CTX_FIXTURE_KEY_REJECTED: the documented fixture signing key must never "
                                + "run under the prod profile. Refusing to start.");
            }
            return new ResolvedKey(new TenantContextSigner(decodeHexKey(rawKey), false),
                    "explicit key (fixture topology)");
        }
        return new ResolvedKey(
                new TenantContextSigner(HexFormat.of().parseHex(DOCUMENTED_FIXTURE_KEY_HEX), true),
                "documented fixture key (fallback)");
    }

    // ── strictness governance (F-INT-06) ─────────────────────────────────

    private boolean resolveStrictness(String topology, boolean prodProfile) {
        boolean requested = environment.getProperty(
                "snad.rls.strict-missing-context", Boolean.class, true);
        if (requested) {
            return true;
        }

        boolean productionPath = TOPOLOGY_SEPARATED.equals(topology) || prodProfile;
        boolean governanceApproved = environment.getProperty(
                "snad.rls.strict-missing-context.governance-approved", Boolean.class, false);
        String approvedBy = normalize(environment.getProperty(
                "snad.rls.strict-missing-context.approved-by"));
        String changeRecord = normalize(environment.getProperty(
                "snad.rls.strict-missing-context.change-record"));

        if (productionPath) {
            if (!governanceApproved || approvedBy == null || changeRecord == null) {
                throw new IllegalStateException(
                        "SANAD_CTX_STRICT_DISABLE_UNAUTHORIZED: disabling snad.rls.strict-missing-context "
                                + "on the production path requires the full governance triple "
                                + "(governance-approved=true + approved-by + change-record). Required "
                                + "security controls cannot be disabled without an approved governance "
                                + "path — startup refused.");
            }
            log.warn("SANAD_CTX_STRICT_DISABLE_GOVERNED: strict-missing-context disabled on the "
                    + "production path — approved-by='{}', change-record='{}'. This setting is now part "
                    + "of the deployment audit record.", approvedBy, changeRecord);
        } else {
            log.warn("SANAD_CTX_STRICT_DISABLE_FIXTURE: strict-missing-context disabled on a disposable "
                    + "topology — never production evidence.");
        }
        return false;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean activeProfilesIncludeProd() {
        String[] active = environment.getActiveProfiles();
        return Arrays.stream(active).anyMatch(p -> p.equalsIgnoreCase("prod"));
    }

    private static byte[] decodeHexKey(String raw) {
        try {
            byte[] key = HexFormat.of().parseHex(raw);
            if (key.length < 32) {
                throw new IllegalArgumentException("decoded key is " + key.length + " bytes");
            }
            return key;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SANAD_CTX_KEY_INVALID: snad.rls.context-hmac-key must be hex-encoded and at least "
                            + "32 bytes (64 hex chars) — refusing to start. (Detail withheld; the key "
                            + "value is never logged.)", e);
        }
    }

    private record ResolvedKey(TenantContextSigner signer, String description) {
    }
}
