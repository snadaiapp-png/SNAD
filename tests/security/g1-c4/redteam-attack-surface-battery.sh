#!/usr/bin/env bash
# ============================================================================
# SNAD G1/C4 (PR-2) — RED TEAM attack-surface confirmation battery.
#
# Live SQL probes against the DISPOSABLE separated-topology separation_cert
# database certified by tests/security/migrator-separation/run-separation-cert.sh.
# Every probe connects as the restricted runtime role `sanad`; SQLSTATEs and
# row counts are recorded per probe; fail-closed expectations.
#
# Isolation guards (fail closed):
#   * RT_ISOLATED_CLUSTER=1 required (never production);
#   * database must be `separation_cert` on a loopback cluster whose only
#     user databases are postgres + separation_cert;
#   * cluster-wide role changes are refused when the runtime/migrator roles
#     are absent (i.e. run AFTER the separation certification).
#
# Outcome classes:
#   DENIED(sqlstate)        — attack correctly refused by PostgreSQL;
#   CONFIRMED               — exposure/design-trust behaviour confirmed live
#                             (recorded as a finding, never as a pass);
#   ZERO_EFFECT / value     — fail-closed invisibility.
#
# Environment:
#   RT_PGHOST (127.0.0.1)  RT_PGPORT (55432)
#   RT_ADMIN_PSQL (psql command with superuser access)
#   RT_ARTIFACTS (fresh evidence directory; must be empty or nonexistent)
#
# This battery is an attack-surface CONFIRMATION exercise on an isolated
# cluster. It is NOT a penetration test of production, NOT a production
# authorization, and NOT a security PASS decision.
# ============================================================================
set -uo pipefail
export LC_ALL=C.UTF-8

RT_PGHOST="${RT_PGHOST:-127.0.0.1}"
RT_PGPORT="${RT_PGPORT:-55432}"
RT_ADMIN_PSQL="${RT_ADMIN_PSQL:-psql -h ${RT_PGHOST} -p ${RT_PGPORT} -U postgres}"
RT_DB="${RT_DB:-separation_cert}"
ART="${RT_ARTIFACTS:-/tmp/redteam-artifacts}"
umask 077

[ "${RT_ISOLATED_CLUSTER:-0}" = "1" ] || { echo "::error::RT_ISOLATED_CLUSTER=1 required" >&2; exit 1; }
case "$RT_PGHOST" in
  127.0.0.1|localhost|::1) ;;
  *) echo "::error::refusing non-loopback RT_PGHOST=$RT_PGHOST" >&2; exit 1 ;;
esac
[ "$RT_DB" = "separation_cert" ] || { echo "::error::non-certification DB rejected" >&2; exit 1; }
command -v flock >/dev/null 2>&1 || { echo "::error::flock is required" >&2; exit 1; }
exec 9< "${BASH_SOURCE[0]}" || exit 1
flock -n 9 || { echo "::error::another battery is running from this checkout" >&2; exit 1; }
[ ! -L "$ART" ] || { echo "::error::evidence path is a symlink" >&2; exit 1; }
if [ -e "$ART" ]; then
  [ -d "$ART" ] && [ -z "$(ls -A "$ART")" ] || { echo "::error::evidence dir not empty; choose fresh RT_ARTIFACTS" >&2; exit 1; }
else
  mkdir -m 700 -p "$ART" || exit 1
fi

admin() { $RT_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$2" -Atc "$3"; }
# Disposable-cluster preflight: only postgres + separation_cert may exist.
admin postgres postgres "SELECT 1" >/dev/null 2>&1 || { echo "::error::admin psql unavailable" >&2; exit 1; }
other_dbs=$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datistemplate=false AND datname NOT IN ('postgres','separation_cert')")
[ "$other_dbs" = "0" ] || { echo "::error::cluster contains $other_dbs other user DB(s); refusing" >&2; exit 1; }
owner=$(admin postgres "$RT_DB" "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=current_database()")
[ "$owner" = "sanad_migrator" ] || { echo "::error::certification DB is not migrator-owned" >&2; exit 1; }
roles=$(admin postgres postgres "SELECT count(*) FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator')")
[ "$roles" = "2" ] || { echo "::error::separation-certification roles absent; run the S0-S9 certification first" >&2; exit 1; }
head_ok=$(admin postgres "$RT_DB" "SELECT count(*) FROM public.flyway_schema_history WHERE version='20261115.1' AND success")
[ "$head_ok" = "1" ] || { echo "::error::Flyway head 20261115.1 not found" >&2; exit 1; }

TSV="$ART/redteam-matrix.tsv"; : > "$TSV"
LOG="$ART/redteam-run.log"
TA=22222222-2222-2222-2222-222222222222
TB=33333333-3333-3333-3333-333333333333

# Ephemeral per-run runtime password (hex, never logged).
RT_PW="$(openssl rand -hex 24)"
[[ "$RT_PW" =~ ^[0-9a-fA-F]{48}$ ]] || { echo "::error::password generation failed" >&2; exit 1; }
admin_secret() { printf '%s\n' "$1" | $RT_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d postgres -At >/dev/null; }
admin_secret "ALTER ROLE sanad PASSWORD '$RT_PW';"

rt() { # rt <sql> [guc]
  local guc_args=()
  if [ $# -ge 2 ] && [ -n "$2" ]; then guc_args=(PGOPTIONS="-c app.tenant_id=$2"); fi
  out=$(env -u PGOPTIONS "${guc_args[@]}" PGPASSWORD="$RT_PW" psql -X -h "$RT_PGHOST" -p "$RT_PGPORT" -U sanad -d "$RT_DB" \
    -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$1" 2>"$ART/last-err.txt")
  rc=$?
  err=$(cat "$ART/last-err.txt")
  state=$(printf '%s\n' "$err" | sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' | head -1)
}
record() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$TSV"; printf '  [%s] %s -> %s\n' "$1" "$2" "$3"; }
expect_denied() { rt "$2" "${3:-}"
  if [ $rc -ne 0 ] && [ "$state" = "$4" ]; then record "$1" "DENIED($4)" "DENIED($state)"; else record "$1" "DENIED($4)" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); fi; }
expect_confirmed() { rt "$2" "${3:-}"
  if [ $rc -eq 0 ]; then record "$1" "CONFIRMED" "CONFIRMED out=$out"; else record "$1" "CONFIRMED" "FAIL(rc=$rc,state=$state)"; FAILURES=$((FAILURES+1)); fi; }
FAILURES=0

echo "== RED TEAM BATTERY — $(date -u +%Y-%m-%dT%H:%M:%SZ) — runtime role sanad on $RT_DB ==" | tee "$LOG"

echo "-- A. RLS trust boundary and bypass attempts --" | tee -a "$LOG"
rt "SET app.tenant_id='$TB'; SELECT count(*) FROM public.users WHERE tenant_id='$TB'" ""
[ $rc -eq 0 ] && record RT-01_GUC_NOT_AUTHENTICATED "DESIGN-TRUST(visibility)" "CONFIRMED out=$out" || { record RT-01 "DESIGN-TRUST" "FAIL rc=$rc"; FAILURES=$((FAILURES+1)); }
expect_denied RT-02_RLS_TENANT_SWAP "UPDATE public.users SET tenant_id='$TB' WHERE tenant_id='$TA'" "$TA" 42501
rt "WITH changed AS (UPDATE public.users SET display_name='RT' WHERE tenant_id='$TB' RETURNING 1) SELECT count(*) FROM changed" "$TA"
[ $rc -eq 0 ] && [ "$out" = "0" ] && record RT-03_RLS_CROSS_UPDATE "ZERO_EFFECT" "ZERO_EFFECT" || { record RT-03 "ZERO_EFFECT" "FAIL(rc=$rc,out=$out)"; FAILURES=$((FAILURES+1)); }
rt "SELECT count(DISTINCT tenant_id) FROM public.login_tenant_index" ""
[ $rc -eq 0 ] && record RT-04_PREAUTH_ENUM "EXPOSURE-DOCUMENTED" "CONFIRMED distinct_tenants=$out" || { record RT-04 "EXPOSURE" "FAIL"; FAILURES=$((FAILURES+1)); }
rt "SELECT count(*) FROM public.auth_token_tenant_index" ""
[ $rc -eq 0 ] && record RT-05_PREAUTH_TOKEN_INDEX "EXPOSURE-DOCUMENTED" "CONFIRMED rows=$out" || { record RT-05 "EXPOSURE" "FAIL"; FAILURES=$((FAILURES+1)); }
expect_denied RT-06_PREAUTH_TOKEN_READONLY "INSERT INTO public.workflow_external_action_token_index (token_hash,action_id,tenant_id,created_at) VALUES ('deadbeef','88888888-8888-8888-8888-888888888888','$TA',now())" "" 42501

echo "-- B. Control-plane write surface (RLS-off tables) --" | tee -a "$LOG"
expect_confirmed RT-07_TENANTS_INSERT "INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('44444444-4444-4444-4444-444444444444','RT-PROBE','rt-probe','ACTIVE',now(),now())" ""
expect_confirmed RT-08_TENANTS_UPDATE "UPDATE public.tenants SET status='SUSPENDED', suspension_reason='RT-PROBE' WHERE id='44444444-4444-4444-4444-444444444444'" ""
expect_confirmed RT-09_TENANTS_DELETE "DELETE FROM public.tenants WHERE id='44444444-4444-4444-4444-444444444444'" ""
expect_confirmed RT-10_QUOTA_INSERT_OWN "INSERT INTO public.tenant_quota (tenant_id,dimension,limit_value,reset_at) VALUES ('$TA','API_RPM',1,now()+interval '30 days')" "$TA"
expect_denied RT-10B_QUOTA_INSERT_FOREIGN "INSERT INTO public.tenant_quota (tenant_id,dimension,limit_value,reset_at) VALUES ('$TB','API_RPM',1,now()+interval '30 days')" "$TA" 42501
rt "WITH deleted AS (DELETE FROM public.tenant_quota WHERE tenant_id='$TA' AND dimension='API_RPM' RETURNING 1) SELECT count(*) FROM deleted" "$TA"
[ $rc -eq 0 ] && [ "$out" = "1" ] && record RT-11_QUOTA_DELETE_OWN "DELETE_1" "DELETE_1 (tenant-scoped)" || { record RT-11 "DELETE_1" "FAIL(rc=$rc,out=$out)"; FAILURES=$((FAILURES+1)); }
expect_confirmed RT-12_CAPS_INSERT "INSERT INTO public.access_capabilities (id,code,name,created_at,updated_at) VALUES ('55555555-5555-5555-5555-555555555555','RT.FAKE','RT probe',now(),now())" ""
expect_confirmed RT-13_CAPS_UPDATE "UPDATE public.access_capabilities SET status='INACTIVE' WHERE code='RT.FAKE'" ""
expect_confirmed RT-14_CAPS_DELETE "DELETE FROM public.access_capabilities WHERE code='RT.FAKE'" ""
rt "SELECT count(*) FROM public.modules" ""
[ $rc -eq 0 ] && record RT-15_GLOBAL_REGISTRY_READ "DESIGN(global)" "CONFIRMED modules=$out" || { record RT-15 "DESIGN" "FAIL"; FAILURES=$((FAILURES+1)); }

echo "-- C. Privilege escalation --" | tee -a "$LOG"
expect_denied RT-16_CREATE_ROLE "CREATE ROLE rt_escalate" "" 42501
expect_denied RT-17_GRANT_MIGRATOR "GRANT sanad_migrator TO sanad" "" 42501
expect_denied RT-18_SET_ROLE_MIGRATOR "SET ROLE sanad_migrator" "" 42501
expect_denied RT-19_ALTER_DATABASE "ALTER DATABASE $RT_DB SET sanad.deployment_topology='fixture'" "" 42501
expect_denied RT-20_CREATE_FUNCTION "CREATE FUNCTION public.rt_f() RETURNS void LANGUAGE sql AS \$\$ SELECT 1 \$\$" "" 42501
rt "CREATE TEMP TABLE hr_employees (tenant_id uuid, hire_date date); INSERT INTO pg_temp.hr_employees VALUES ('$TA','2099-12-31'::date); SELECT public.hr_default_migration_as_of_date('$TA')::text" "$TA"
[ $rc -eq 0 ] && [ "$out" != "2099-12-31" ] && record RT-21_TEMP_SHADOW_NEGATIVE "NOT-2099-12-31" "PASS out=$out (pg_temp shadow ignored)" || { record RT-21 "NOT-2099-12-31" "FAIL/SIGHTED(rc=$rc,out=$out)"; FAILURES=$((FAILURES+1)); }
rt "DROP TABLE pg_temp.hr_employees" "" || true

echo "-- D. Audit ledger tampering --" | tee -a "$LOG"
expect_denied RT-22_AUDIT_UPDATE "UPDATE public.platform_audit_logs SET reason='FORGED'" "$TA" 42501
expect_denied RT-23_AUDIT_DELETE "DELETE FROM public.platform_audit_logs" "$TA" 42501
expect_denied RT-24_AUDIT_TRUNCATE "TRUNCATE TABLE public.platform_audit_logs" "$TA" 42501
expect_denied RT-25_AUDIT_ADD_TRIGGER "CREATE TRIGGER rt_t BEFORE UPDATE ON public.platform_audit_logs FOR EACH ROW EXECUTE FUNCTION public.sync_login_tenant_index()" "$TA" 42501
admin postgres "$RT_DB" "DELETE FROM public.platform_audit_logs WHERE action='RT_FORGED_PROBE'" >/dev/null
expect_confirmed RT-26_AUDIT_FORGED_INSERT "INSERT INTO public.platform_audit_logs (id,actor_tenant_id,action,resource_type,resource_id,result,created_at) VALUES ('77777777-7777-7777-7777-777777777777','$TA','RT_FORGED_PROBE','redteam','rt','SUCCESS',now())" "$TA"
admin postgres "$RT_DB" "SELECT count(*) FROM public.platform_audit_logs WHERE id='77777777-7777-7777-7777-777777777777' AND action='RT_FORGED_PROBE'" | grep -q 1 \
  && record RT-26B_ADMIN_VERIFIED "present=1" "present=1" || { record RT-26B "present=1" "MISSING"; FAILURES=$((FAILURES+1)); }
rt "SET ROLE sanad_callback_replay_cleanup; SELECT count(*) FROM public.platform_audit_logs" ""
[ $rc -ne 0 ] && [ "$state" = "42501" ] && record RT-27_CLEANUP_ROLE_SELECT "DENIED(42501)" "DENIED($state)" || { record RT-27 "DENIED(42501)" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); }
rt "SET ROLE sanad_callback_replay_cleanup; UPDATE public.platform_audit_logs SET reason='X'" ""
[ $rc -ne 0 ] && [ "$state" = "42501" ] && record RT-28_CLEANUP_ROLE_UPDATE "DENIED(42501)" "DENIED($state)" || { record RT-28 "DENIED(42501)" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); }

echo "-- E. Fake-success / migration-gate forgery --" | tee -a "$LOG"
expect_denied RT-29_FLYWAY_HISTORY_INSERT "INSERT INTO public.flyway_schema_history (installed_rank,version,description,type,script,success) VALUES (9999,'20990101.1','rt','SQL','rt.sql',true)" "" 42501
expect_denied RT-30_FLYWAY_HISTORY_UPDATE "UPDATE public.flyway_schema_history SET success=true WHERE version='20261113.1'" "" 42501
rt "SET sanad.deployment_topology='fixture'; SELECT current_setting('sanad.deployment_topology')" ""
[ $rc -eq 0 ] && record RT-31_SESSION_TOPOLOGY_OVERRIDE "DESIGN(catalog-authoritative)" "CONFIRMED session=$out" || { record RT-31 "DESIGN" "FAIL"; FAILURES=$((FAILURES+1)); }

echo "-- F. Definer function invocation --" | tee -a "$LOG"
expect_denied RT-32_DEFINER_FOREIGN_TENANT_REPORT "SELECT public.hr_reconcile_tenant_report('$TB')" "$TA" 42501
expect_denied RT-33_DEFINER_FOREIGN_TENANT_PRECHECK "SELECT public.hr_precheck_tenant('$TB')" "$TA" 42501

echo "-- G. Separation invariants --" | tee -a "$LOG"
rt "SELECT pg_has_role('sanad','sanad_migrator','MEMBER')" ""
[ $rc -eq 0 ] && [ "$out" = "f" ] && record RT-34_NO_MIGRATOR_MEMBERSHIP "false" "false" || { record RT-34 "false" "FAIL out=$out"; FAILURES=$((FAILURES+1)); }
srv=$(admin postgres "$RT_DB" "SELECT count(*) FROM pg_roles WHERE rolname='service_role'")
record RT-35_SERVICE_ROLE_LOCAL "absent-locally" "service_role_count=$srv"

echo | tee -a "$LOG"
echo "REDTEAM_PROBES=$(wc -l < "$TSV") FAILURES=$FAILURES" | tee -a "$LOG"
[ $FAILURES -eq 0 ] && echo "REDTEAM_BATTERY=COMPLETE (all probes executed with expected outcomes)" | tee -a "$LOG" \
  || echo "REDTEAM_BATTERY=WITH_FAILURES" | tee -a "$LOG"
# The forged audit row (RT-26) is intentionally left in place on the disposable
# database as tamper-attempt evidence; the cluster is destroyed with it.
exit $FAILURES
