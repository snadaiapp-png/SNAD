package com.sanad.platform.security.rls;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints the HMAC-SHA256 message authentication code that
 * {@code public.app_bind_tenant_ctx(uuid, text)} (V20261124_1) verifies
 * before binding a transaction-scoped tenant context.
 *
 * <h3>Contract (must stay byte-identical to the database verifier)</h3>
 *
 * <p>{@code mac = lowercase_hex(HMAC-SHA256(key, tenant_uuid_text || ':' ||
 * txid_current_text || ':' || pg_backend_pid_text))} — the canonical message
 * is the UTF-8 bytes of {@code "<uuid>:<txid>:<pid>"}, the output is the
 * lowercase hex encoding of the 32-byte digest. The database side recomputes
 * the same digest from the caller's OWN {@code txid_current()} and
 * {@code pg_backend_pid()} (see {@code app_tenant_ctx_slot()}), so a captured
 * or replayed mac from another session/transaction fails the equality check,
 * and a hostile runtime session cannot forge a mac without the signing key.</p>
 *
 * <h3>Key custody (F-DB-07 design, enforced here)</h3>
 *
 * <ul>
 *   <li>The key lives ONLY in the backend environment
 *       ({@code SANAD_TENANT_CTX_HMAC_KEY}) and the migrator-owned,
 *       zero-grant table {@code platform_tenant_ctx_keys}. It NEVER appears
 *       in the repository, in migration SQL, in logs, or in error output.</li>
 *   <li>Production (topology {@code separated}) MUST provision an explicit
 *       key; the documented fixture key is rejected unconditionally (same
 *       policy as {@code CrmEncryptionKeyValidator}).</li>
 *   <li>Disposable fixture topologies may fall back to the documented
 *       fixture key so the certification chain stays replayable — explicitly
 *       NOT production evidence.</li>
 * </ul>
 *
 * <p>This class is immutable and stateless; a single instance is installed
 * process-wide by {@link TenantRlsSecurityConfig} and consumed through
 * {@link TenantRlsRuntimePolicy}.</p>
 */
public final class TenantContextSigner {

    private final byte[] key;
    private final boolean fixtureFallback;

    /**
     * @param key             HMAC signing key material (>= 32 bytes per the
     *                        {@code platform_tenant_ctx_keys.secret} CHECK).
     * @param fixtureFallback true when the key was resolved to the documented
     *                        fixture key by fallback (disposable topologies
     *                        only) — surfaced for audit logging, never for
     *                        behavior changes.
     */
    public TenantContextSigner(byte[] key, boolean fixtureFallback) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException(
                    "Tenant-context HMAC key must be at least 32 bytes (matches "
                            + "platform_tenant_ctx_keys.secret CHECK) — got "
                            + (key == null ? "null" : key.length + " bytes"));
        }
        this.key = key.clone();
        this.fixtureFallback = fixtureFallback;
    }

    /**
     * Mint the mac for the given tenant and the caller's current
     * transaction slot as reported by {@code app_tenant_ctx_slot()}.
     *
     * @param tenant the tenant UUID to bind (from the AUTHENTICATED principal).
     * @param txid   {@code txid_current()::text} of the CURRENT transaction.
     * @param pid    {@code pg_backend_pid()} of the CURRENT connection.
     * @return lowercase hex mac, 64 characters.
     */
    public String macHex(UUID tenant, String txid, int pid) {
        if (tenant == null || txid == null || txid.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant-context mac requires a tenant and the current txid");
        }
        String canonical = tenant.toString() + ":" + txid + ":" + pid;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Tenant-context HMAC computation failed — binding refused (fail-closed)", e);
        }
    }

    /** Whether this signer runs on the documented fixture-key fallback (disposable topologies only). */
    public boolean isFixtureFallback() {
        return fixtureFallback;
    }
}
