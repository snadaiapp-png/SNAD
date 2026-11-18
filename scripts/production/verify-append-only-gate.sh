#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# G1/C4 S10 — PROTECTED DEPLOYMENT ENFORCEMENT: append-only post-migration
# security gate.
#
# This gate fail-closes the protected deployment path unless migration
# V20261115_1's SECURITY EFFECT is actually present in the target database:
#   * the database-level topology is 'separated' (authoritative catalog
#     source pg_db_role_setting — session-level overrides cannot satisfy it),
#   * the runtime role sanad is restricted and owns nothing on the ledgers,
#   * Flyway history records 20261115.1 as successful — necessary, NEVER
#     sufficient on its own,
#   * the EFFECTIVE catalog privileges are append-only (SELECT/INSERT only)
#     on public.platform_audit_logs AND public.subscription_commands,
#   * no member role of sanad holds UPDATE/DELETE/TRUNCATE on either ledger
#     (inherited-privilege bypass detection).
#
# Identity model mirrors scripts/production/verify-flyway.sh: the gate runs
# as the separately governed READ-ONLY audit identity sanad_release_auditor
# (bare or Supabase-pooler-suffixed). Runtime, migrator and admin identities
# are refused. Never regrant anything to satisfy this gate.
#
# Exit codes: 0 = PASS (deployment may proceed); 1 = FAIL (deployment MUST
# stop). No output value of this script is a production authorization.
# ============================================================================

: "${AUDIT_DATABASE_URL:?AUDIT_DATABASE_URL is required (read-only release audit identity, RUNBOOK section 2b)}"
: "${AUDIT_DATABASE_USERNAME:?AUDIT_DATABASE_USERNAME is required (read-only release audit identity)}"
: "${AUDIT_DATABASE_PASSWORD:?AUDIT_DATABASE_PASSWORD is required (read-only release audit identity)}"

# Identity pinning (fail closed): exactly the dedicated read-only audit role.
if [ "${AUDIT_DATABASE_USERNAME}" != "sanad_release_auditor" ] && \
   [ "${AUDIT_DATABASE_USERNAME}" != "sanad_release_auditor.tkbrvupemreqabwzdpyq" ]; then
  echo "::error::APPEND_ONLY_GATE: AUDIT_DATABASE_USERNAME must be the dedicated read-only audit identity sanad_release_auditor, bare or Supabase-pooler-suffixed; runtime/migrator/admin identities are forbidden (got '${AUDIT_DATABASE_USERNAME}')" >&2
  exit 1
fi

# libpq URI, not CLI credentials: forbid embedded user/password and
# libpq overrides that could switch authenticated identities or inject
# session-level settings.
AUDIT_URI="${AUDIT_DATABASE_URL#jdbc:}"
if [[ "$AUDIT_URI" != postgresql://* || "$AUDIT_URI" == *"@"* || "$AUDIT_URI" =~ [\?\&](user|password|options|service|host|dbname)= ]]; then
  echo "::error::APPEND_ONLY_GATE: invalid audit database URL: require postgresql:// URI without embedded credentials, options or identity overrides" >&2
  exit 1
fi

run_sql() {
  PGPASSWORD="$AUDIT_DATABASE_PASSWORD" PGSSLMODE="${PGSSLMODE:-require}" psql \
    --dbname="$AUDIT_URI" --username="$AUDIT_DATABASE_USERNAME" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
    --field-separator=$'\t' --command="$1"
}

echo "S10 append-only gate: connecting as read-only audit identity user=$AUDIT_DATABASE_USERNAME"

# --- G1: connected identity contract ---------------------------------------
connected="$(run_sql "SELECT current_user, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname=current_user;")"
if [[ "$connected" != "$(printf "sanad_release_auditor\tf\tf\tf\tf")" ]]; then
  echo "::error::APPEND_ONLY_GATE: audit connection role or privilege contract invalid" >&2
  exit 1
fi
audit_readonly="$(run_sql "SELECT has_table_privilege(current_user, 'public.flyway_schema_history', 'SELECT'), has_table_privilege(current_user, 'public.flyway_schema_history', 'INSERT'), has_table_privilege(current_user, 'public.flyway_schema_history', 'UPDATE'), has_table_privilege(current_user, 'public.flyway_schema_history', 'DELETE'), has_schema_privilege(current_user, 'public', 'CREATE');")"
if [[ "$audit_readonly" != "$(printf "t\tf\tf\tf\tf")" ]]; then
  echo "::error::APPEND_ONLY_GATE: audit role is not read-only (history SELECT expected, writes/CREATE forbidden)" >&2
  exit 1
fi
echo "S10_G1_AUDIT_IDENTITY=PASS (dedicated read-only auditor)"

# --- G2: database-level topology from the authoritative catalog -------------
# pg_db_role_setting (setrole=0) records ALTER DATABASE ... SET values.
# A session-level override (PGOPTIONS / SET) never appears here, so only the
# durable database configuration can satisfy this check.
db_topology="$(run_sql "SELECT CASE WHEN s.setconfig IS NULL THEN 'MISSING' ELSE coalesce((SELECT cfg FROM unnest(s.setconfig) cfg WHERE cfg LIKE 'sanad.deployment_topology=%' LIMIT 1), 'MISSING') END FROM pg_db_role_setting s WHERE s.setdatabase = (SELECT oid FROM pg_database WHERE datname = current_database()) AND s.setrole = 0;")"
if [[ "$db_topology" != "sanad.deployment_topology=separated" ]]; then
  echo "::error::APPEND_ONLY_GATE: DATABASE_LEVEL_TOPOLOGY_NOT_SEPARATED actual='$db_topology' (expected sanad.deployment_topology=separated at the database level; missing/empty/fixture/unknown topologies fail closed)" >&2
  exit 1
fi
echo "S10_G2_DATABASE_TOPOLOGY=PASS (separated, durable database-level setting)"

# --- G3: session-vs-catalog consistency (session-level override detection) --
session_topology="$(run_sql "SELECT coalesce(current_setting('sanad.deployment_topology', true), 'MISSING');")"
if [[ "$session_topology" != "separated" ]]; then
  echo "::error::APPEND_ONLY_GATE: SESSION_LEVEL_TOPOLOGY_OVERRIDE_DETECTED session='$session_topology' catalog='separated' (the gate must observe the unmodified database default; ambient PGOPTIONS/SET overrides are prohibited)" >&2
  exit 1
fi
echo "S10_G3_SESSION_CONSISTENCY=PASS (session value equals durable database default)"

# --- G4: runtime identity contract (catalog) --------------------------------
runtime_contract="$(run_sql "SELECT rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolinherit FROM pg_roles WHERE rolname='sanad';")"
if [[ "$runtime_contract" != "$(printf "f\tf\tf\tf\tf")" ]]; then
  echo "::error::APPEND_ONLY_GATE: runtime role sanad violates the restricted contract (superuser/bypassrls/createdb/createrole/inherit must all be false)" >&2
  exit 1
fi
migrator_membership="$(run_sql "SELECT count(*) FROM pg_auth_members m JOIN pg_roles gr ON gr.oid=m.roleid JOIN pg_roles mr ON mr.oid=m.member WHERE mr.rolname='sanad' AND gr.rolname='sanad_migrator';")"
if [[ "$migrator_membership" != "0" ]]; then
  echo "::error::APPEND_ONLY_GATE: runtime sanad must not be a member of sanad_migrator" >&2
  exit 1
fi
ledger_owners="$(run_sql "SELECT count(DISTINCT pg_get_userbyid(c.relowner)) || '|' || coalesce(string_agg(DISTINCT pg_get_userbyid(c.relowner), ',' ORDER BY pg_get_userbyid(c.relowner)), '') FROM pg_class c WHERE c.oid IN ('public.platform_audit_logs'::regclass, 'public.subscription_commands'::regclass);")"
if [[ "$ledger_owners" != "1|sanad_migrator" ]]; then
  echo "::error::APPEND_ONLY_GATE: append-only ledgers must be owned by sanad_migrator only, got: ${ledger_owners#*|}" >&2
  exit 1
fi
echo "S10_G4_RUNTIME_CONTRACT=PASS (restricted sanad; migrator separation; migrator-owned ledgers)"

# --- G5: Flyway history — necessary, never sufficient ------------------------
flyway_rows="$(run_sql "SELECT count(*) FROM flyway_schema_history WHERE version='20261115.1' AND type='SQL' AND success;")"
if [[ "$flyway_rows" != "1" ]]; then
  echo "::error::APPEND_ONLY_GATE: FLYWAY_20261115_1_NOT_RECORDED rows=$flyway_rows (Flyway history must show V20261115_1 applied successfully)" >&2
  exit 1
fi
echo "S10_G5_FLYWAY_RECORDED=PASS (20261115.1 present — necessary condition only; effective privileges below are authoritative)"

# --- G6: EFFECTIVE append-only privileges (direct) ---------------------------
for ledger in public.platform_audit_logs public.subscription_commands; do
  acl="$(run_sql "SELECT has_table_privilege('sanad','${ledger}','SELECT')::text||'|'||has_table_privilege('sanad','${ledger}','INSERT')::text||'|'||has_table_privilege('sanad','${ledger}','UPDATE')::text||'|'||has_table_privilege('sanad','${ledger}','DELETE')::text||'|'||has_table_privilege('sanad','${ledger}','TRUNCATE')::text;")"
  if [[ "$acl" != "true|true|false|false|false" ]]; then
    echo "::error::APPEND_ONLY_GATE: EFFECTIVE_ACL_NOT_APPEND_ONLY table=${ledger} actual='${acl}' (expected SELECT/INSERT=t, UPDATE/DELETE/TRUNCATE=f)" >&2
    exit 1
  fi
  echo "S10_G6_EFFECTIVE_ACL=PASS table=${ledger} acl=${acl}"
done

# --- G7: inherited-privilege bypass (member roles of sanad) ------------------
# Grants to any role sanad belongs to are reachable via SET ROLE even under
# NOINHERIT/INHERIT FALSE; the gate rejects them all.
inherited_bypass="$(run_sql "SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.roleid JOIN pg_roles u ON u.oid=m.member WHERE u.rolname='sanad' AND r.rolname<>'sanad' AND (has_table_privilege(r.rolname,'public.subscription_commands','UPDATE') OR has_table_privilege(r.rolname,'public.subscription_commands','DELETE') OR has_table_privilege(r.rolname,'public.subscription_commands','TRUNCATE') OR has_table_privilege(r.rolname,'public.platform_audit_logs','UPDATE') OR has_table_privilege(r.rolname,'public.platform_audit_logs','DELETE') OR has_table_privilege(r.rolname,'public.platform_audit_logs','TRUNCATE'));")"
if [[ "$inherited_bypass" != "0" ]]; then
  echo "::error::APPEND_ONLY_GATE: INHERITED_PRIVILEGE_BYPASS member_roles_with_ledger_write=$inherited_bypass (no role sanad belongs to may hold UPDATE/DELETE/TRUNCATE on an append-only ledger)" >&2
  exit 1
fi
echo "S10_G7_NO_INHERITED_BYPASS=PASS (0 member roles with ledger write privileges)"

echo "POST_MIGRATION_APPEND_ONLY_GATE=PASS (V20261115_1 security effect verified: topology, identity, effective and inherited ACLs; Flyway success alone was not trusted)"
