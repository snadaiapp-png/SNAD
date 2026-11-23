#!/usr/bin/env bash
# ============================================================================
# tests/security/lib/tenant-ctx-bind.sh — shared helper for the SIGNED
# TENANT-CONTEXT BIND protocol (V20261124_1/V20261124_2, G1 P0-B F-DB-07).
#
# After the enforcement migration, a session that did NOT successfully call
# public.app_bind_tenant_ctx() sees ZERO tenant rows (fail closed). Security
# probes that exercise LEGITIMATE tenant contexts must therefore reproduce
# the backend protocol exactly, in the SAME transaction as the probe:
#
#   mac = HMAC-SHA256(key, tenant || ':' || txid_current() || ':' || pg_backend_pid())
#   SELECT public.app_bind_tenant_ctx(tenant, mac) FROM public.app_tenant_ctx_slot() s;
#
# The helper emits ONE statement that derives the slot and computes the mac
# DB-side (pgcrypto is installed by the chain), so the bind and the probe
# share a single implicit transaction when prefixed to the probe SQL inside
# one psql -c argument. The signing key is NEVER emitted into logs; in
# disposable fixture contexts the documented fixture key is used by design.
#
# Sourced by run-separation-cert.sh and redteam-attack-surface-battery.sh.
# ============================================================================
# Guard against double-sourcing.
[ -n "${TENANT_CTX_BIND_LIB_SOURCED:-}" ] && return 0
TENANT_CTX_BIND_LIB_SOURCED=1

# The documented DISPOSABLE-FIXTURE signing key (repository-published BY
# DESIGN; valid only on fixture-topology clusters holding no real data —
# V20261124_2 auto-provisions this exact value for 'migration-fixture'/unset
# topologies). 'separated' certification runs override it via SEP_CTX_KEY /
# RT_CTX_KEY (ephemeral, provisioned by the S1 sequence, never logged).
TENANT_CTX_FIXTURE_KEY_HEX="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

# tenant_ctx_key_hex — resolve the ACTIVE signing key for this run:
#   1. $1 (explicit override), else
#   2. TENANT_CTX_KEY_HEX (exported by the caller), else
#   3. the documented fixture key.
tenant_ctx_key_hex() {
    if [ -n "${1:-}" ]; then printf '%s\n' "$1"
    elif [ -n "${TENANT_CTX_KEY_HEX:-}" ]; then printf '%s\n' "$TENANT_CTX_KEY_HEX"
    else printf '%s\n' "$TENANT_CTX_FIXTURE_KEY_HEX"; fi
}

# tenant_ctx_bind_sql <tenant-uuid> [key_hex] — the bind statement. Runs the
# slot call and the HMAC verification in ONE statement (one transaction).
# The key material appears only in the probe's own SQL text (session-private
# under default privileges; fixture-only on disposable clusters).
tenant_ctx_bind_sql() {
    local tenant="$1"
    local key_hex
    key_hex="$(tenant_ctx_key_hex "${2:-}")" || return 1
    # DO-block wrapper: the bind executes within the same transaction but
    # produces NO result rows, so probe outputs stay exact single values.
    printf "DO \$ctx_bind\$ DECLARE _r boolean; BEGIN SELECT public.app_bind_tenant_ctx('%s'::uuid, encode(hmac(('%s:'||s.ctx_txid||':'||s.ctx_pid::text)::bytea, decode('%s','hex'),'sha256'),'hex')) INTO _r FROM public.app_tenant_ctx_slot() s; END \$ctx_bind\$;" \
        "$tenant" "$tenant" "$key_hex"
}

# tenant_ctx_prefix_sql <sql> <tenant-uuid> [key_hex] — prepend the bind to a
# probe so both run as ONE psql -c argument (single implicit transaction).
tenant_ctx_prefix_sql() {
    local probe_sql="$1" tenant="$2" key_hex="${3:-}"
    printf '%s %s' "$(tenant_ctx_bind_sql "$tenant" "$key_hex")" "$probe_sql"
}
