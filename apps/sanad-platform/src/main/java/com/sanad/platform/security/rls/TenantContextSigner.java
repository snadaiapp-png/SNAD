package com.sanad.platform.security.rls;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs the SIGNED TENANT-CONTEXT BIND protocol (V20261124_1/V20261124_2,
 * G1 P0-B — F-DB-07 / RT-01 root-cause remediation).
 *
 * <h3>Why this exists</h3>
 *
 * <p>After the enforcement migration, PostgreSQL RLS policies no longer trust
 * the raw {@code app.tenant_id} GUC: any session holding runtime credentials
 * could forge it (live-proven by RT-01). Policies instead read
 * {@code app_tenant_ctx_text()}, which returns the tenant ONLY when the
 * transaction successfully called {@code app_bind_tenant_ctx(tenant, mac)}
 * with {@code mac = HMAC-SHA256(key, tenant || ':' || txid || ':' || pid)}.
 * The mac binds the context to the caller's own transaction id and backend
 * pid — a hostile session cannot compute it for a foreign tenant without the
 * signing key, and cannot replay a captured mac (different txid/pid).</p>
 *
 * <h3>Key custody</h3>
 *
 * <p>The signing key lives ONLY in the backend environment
 * ({@code SANAD_TENANT_CTX_HMAC_KEY}, 64 hex chars = 32 bytes) and in the
 * migrator-owned, zero-grant {@code platform_tenant_ctx_keys} table
 * provisioned by the operator. It NEVER appears in the repository, in
 * migration SQL, or in logs. Rotation: the operator inserts a new ACTIVE key
 * (verification tries all ACTIVE keys, latest first), the fleet switches
 * {@code SANAD_TENANT_CTX_HMAC_KEY}, then the old key is retired.</p>
 *
 * <h3>Legacy mode (compatibility window)</h3>
 *
 * <p>When the key is absent, the signer is INACTIVE and the tenant-context
 * writers fall back to the legacy raw-GUC writes. This keeps the backend
 * bootable between migration V20261124_1 (objects exist, no enforcement) and
 * the operator provisioning step. After V20261124_2 enforcement, an
 * unbound session sees zero tenant rows — the deployment runbook therefore
 * REQUIRES the key env before that migration's change window. The signer
 * never logs key material.</p>
 */
@Component
public class TenantContextSigner {

    private static final Logger log = LoggerFactory.getLogger(TenantContextSigner.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public TenantContextSigner(
            @Value("${SANAD_TENANT_CTX_HMAC_KEY:}") String keyHex) {
        this.secret = parseKey(keyHex);
        if (this.secret == null) {
            log.warn("TENANT_CTX_SIGNER_INACTIVE: SANAD_TENANT_CTX_HMAC_KEY is not set — "
                    + "the backend runs in legacy raw-GUC mode. This mode yields ZERO tenant rows "
                    + "once migration V20261124_2 (tenant-ctx policy enforcement) is applied; "
                    + "provision the key before that change window.");
        } else {
            log.info("TENANT_CTX_SIGNER_ACTIVE: signed tenant-context binding enabled "
                    + "(32-byte key; key material never logged)");
        }
    }

    /**
     * @return true when the signer holds a valid key and the bind protocol
     *         must be used by every tenant-context writer.
     */
    public boolean isActive() {
        return secret != null;
    }

    /**
     * Compute the bind mac for a tenant in the CURRENT transaction.
     *
     * @param tenant the authenticated tenant id (from the principal, never
     *        client input)
     * @param txid the caller's transaction id as returned by
     *        {@code app_tenant_ctx_slot()} in the SAME transaction
     * @param pid the caller's backend pid as returned by the same slot call
     * @return lowercase hex mac
     * @throws IllegalStateException when the signer is inactive (callers must
     *         check {@link #isActive()} first)
     */
    public String macFor(UUID tenant, String txid, int pid) {
        if (secret == null) {
            throw new IllegalStateException(
                    "TenantContextSigner is inactive: SANAD_TENANT_CTX_HMAC_KEY is not configured");
        }
        if (tenant == null || txid == null || txid.isBlank()) {
            throw new IllegalArgumentException("tenant and txid are required for the bind mac");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] out = mac.doFinal(
                    (tenant.toString() + ":" + txid + ":" + pid).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // JVM-level misconfiguration; fail closed.
            throw new IllegalStateException("TENANT_CTX_MAC_COMPUTATION_FAILED", e);
        }
    }

    /**
     * Disabled signer for tests and legacy-mode tooling: {@link #isActive()}
     * is false, every mac request is refused.
     */
    public static TenantContextSigner disabled() {
        return new TenantContextSigner("");
    }

    /**
     * Active signer from a hex key — used by tests against the documented
     * fixture key and by provisioning tooling.
     */
    public static TenantContextSigner fromHexKey(String keyHex) {
        TenantContextSigner signer = new TenantContextSigner(keyHex == null ? "" : keyHex);
        if (!signer.isActive()) {
            throw new IllegalArgumentException(
                    "fromHexKey requires a 64-hex-character (32-byte) key");
        }
        return signer;
    }

    private static byte[] parseKey(String keyHex) {
        if (keyHex == null || keyHex.isBlank()) {
            return null;
        }
        String normalized = keyHex.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            // Fail closed: a malformed key is a configuration error and must
            // never silently degrade to a shorter secret. Refuse startup.
            throw new IllegalStateException(
                    "TENANT_CTX_SIGNER_KEY_INVALID: SANAD_TENANT_CTX_HMAC_KEY must be exactly "
                            + "64 hex characters (32 bytes)");
        }
        return HexFormat.of().parseHex(normalized);
    }
}
