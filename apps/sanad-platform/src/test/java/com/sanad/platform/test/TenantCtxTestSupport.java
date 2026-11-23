package com.sanad.platform.test;

import com.sanad.platform.security.rls.TenantContextSigner;

/**
 * Test support for the SIGNED TENANT-CONTEXT BIND protocol
 * (V20261124_1/V20261124_2 — G1 P0-B, F-DB-07/RT-01 root-cause remediation).
 *
 * <p>On fixture-topology databases (every CI/local chain built from
 * {@code classpath:db/migration}), migration V20261124_2 auto-provisions the
 * DOCUMENTED fixture signing key below and turns enforcement on. Tests that
 * exercise tenant-visible paths must therefore sign their binds with the
 * SAME key — exactly mirroring what the production backend does with the
 * environment key. The fixture key is repository-published BY DESIGN: it is
 * only valid on disposable fixture databases holding no real data, and the
 * 'separated' production topology refuses fixture provisioning, requiring
 * the operator-provisioned key instead.</p>
 */
public final class TenantCtxTestSupport {

    /**
     * The documented disposable-fixture signing key (64 hex chars = 32
     * bytes). MUST stay identical to TENANT_CTX_FIXTURE_KEY_HEX in
     * tests/security/lib/tenant-ctx-bind.sh and to the auto-provisioned
     * value in V20261124_2.
     */
    public static final String FIXTURE_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private TenantCtxTestSupport() {
    }

    /** An ACTIVE signer bound to the documented fixture key. */
    public static TenantContextSigner fixtureSigner() {
        return TenantContextSigner.fromHexKey(FIXTURE_KEY_HEX);
    }
}
