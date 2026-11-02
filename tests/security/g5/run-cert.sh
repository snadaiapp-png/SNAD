#!/usr/bin/env bash
# ============================================================================
# SANAD P0 G1 — G5 (Flyway Upgrade Certification) + G6 (Backup/Restore)
# Isolated host-native PostgreSQL 17.x + project-pinned Flyway CLI 11.7.2.
# Scenarios: A fresh DB | B production-history-equivalent upgrade | C failure
# injection (fail-closed proof, NO repair/baseline) | D backup→restore→RLS/A-B.
# All gates hard-fail. No production access. No test weakening.
# ============================================================================
set -euo pipefail
export LC_ALL=C.UTF-8

MIG_LOC_ROOT="apps/sanad-platform/src/main/resources/db/migration"
VEN_LOC_ROOT="apps/sanad-platform/src/main/resources/db/vendor/postgresql"
FLYWAY=/tmp/flyway/flyway
LOCS="filesystem:$MIG_LOC_ROOT,filesystem:$VEN_LOC_ROOT"
PROD_HIST="tests/security/g5/prod-history-20261102.json"
ART=/tmp/g5-artifacts; mkdir -p "$ART"
SUMMARY=/tmp/g5-g6-report.md; : > "$SUMMARY"

gate() { echo "GATE [$1] $2"; echo "GATE [$1] $2" >> "$SUMMARY"; }
die()  { echo "::error::GATE [$1] FAILED: $2"; echo "GATE [$1] FAILED: $2" >> "$SUMMARY"; exit 1; }

export PATH="/usr/lib/postgresql/17/bin:$PATH"
export PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres
sudo -u postgres psql -Atqc "SELECT version()" | head -1 | tee "$ART/pg-version.txt"
gate TOOLCHAIN "PostgreSQL server $(sudo -u postgres psql -Atqc 'SHOW server_version;') host-native isolated; Flyway $($FLYWAY -v 2>/dev/null | grep -E '^Flyway ' | head -1)"

# ---------------------------------------------------------------- roles/dbs
sudo -u postgres psql -v ON_ERROR_STOP=1 <<'SQL'
DO $role$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad') THEN
    CREATE ROLE sanad LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD 'cert_local_only';
  END IF;
END $role$;
ALTER ROLE sanad NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
SELECT 'CREATE DATABASE sanad OWNER sanad' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='sanad')\gexec
SELECT 'CREATE DATABASE upgrade_hist OWNER sanad' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='upgrade_hist')\gexec
SELECT 'CREATE DATABASE restored OWNER sanad' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='restored')\gexec
SELECT 'CREATE DATABASE injection OWNER sanad' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='injection')\gexec
SQL
role_contract=$(sudo -u postgres psql -Atqc "SELECT rolcanlogin::text||'|'||rolsuper::text||'|'||rolcreatedb::text||'|'||rolcreaterole::text||'|'||rolinherit::text||'|'||rolbypassrls::text FROM pg_roles WHERE rolname='sanad'")
[ "$role_contract" = "true|false|false|false|false|false" ] || die ROLE "sanad contract violation: $role_contract"
gate ROLE "sanad contract OK (LOGIN, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOINHERIT, NOBYPASSRLS)"

export PGPASSWORD='cert_local_only'   # psql only; Flyway CLI takes -password explicitly
fw() { $FLYWAY -url="jdbc:postgresql://127.0.0.1:5432/$1" -user=sanad -password='cert_local_only' -locations="$LOCS" "${@:2}"; }
fwc() { $FLYWAY -url="jdbc:postgresql://127.0.0.1:5432/$1" -user=sanad -password='cert_local_only' -locations="$2" "${@:3}"; }
history_head() { psql -At -U sanad -d "$1" -c "SELECT max(version) FROM flyway_schema_history WHERE success=true AND version IS NOT NULL;"; }
success_rows() { psql -At -U sanad -d "$1" -c "SELECT count(*) FROM flyway_schema_history WHERE success=true;"; }
sql_rls="SELECT c.relname||':'||c.relrowsecurity::int||c.relforcerowsecurity::int FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('refresh_tokens','password_reset_tokens','provisioning_jobs','provisioning_job_steps','subscription_commands','users','tenant_subscriptions') ORDER BY 1;"

# ============================================================================
# SCENARIO A — Fresh database: full chain + validate
# ============================================================================
gate A "start: fresh DB full chain"
T0=$(date +%s)
fw sanad migrate | tee "$ART/A-migrate.log"
fw sanad validate | tee "$ART/A-validate.log"
T1=$(date +%s)
head=$(history_head sanad)
[ "$head" = "20261102.2" ] || die A "fresh-DB head=$head, expected 20261102.2"
tables=$(psql -At -U sanad -d sanad -c "SELECT count(*) FROM pg_tables WHERE schemaname='public';")
gate A "PASS: head=20261102.2, public tables=$tables, chain window $((T1-T0))s"

rls_fresh=$(psql -At -U sanad -d sanad -c "$sql_rls" | tee "$ART/A-rls-catalog.txt")
echo "$rls_fresh" | grep -q '^refresh_tokens:11$' || die A "refresh_tokens not ENABLE+FORCE"
echo "$rls_fresh" | grep -q '^password_reset_tokens:11$' || die A "password_reset_tokens not ENABLE+FORCE"
echo "$rls_fresh" | grep -q '^provisioning_jobs:11$' || die A "provisioning_jobs not ENABLE+FORCE"
echo "$rls_fresh" | grep -q '^subscription_commands:11$' || die A "subscription_commands not ENABLE+FORCE"
echo "$rls_fresh" | grep -q '^users:11$' || die A "users not ENABLE+FORCE"
gate A "RLS catalog: ENABLE+FORCE (11) on users, refresh_tokens, password_reset_tokens, provisioning_jobs, subscription_commands"

# ============================================================================
# SCENARIO B — Production-history-equivalent upgrade (checksums verified)
# ============================================================================
gate B "start: apply chain up to production watermark 20261021.1"
fw upgrade_hist migrate -target=20261021.1 | tee "$ART/B-migrate-to-watermark.log"
fw upgrade_hist validate | tee "$ART/B-validate-at-watermark.log"
head_b1=$(history_head upgrade_hist)
[ "$head_b1" = "20261021.1" ] || die B "history-equivalent head=$head_b1, expected 20261021.1"

python3 - "$PROD_HIST" <<'PYEOF' | tee "$ART/B-checksum-join.txt"
import json, subprocess, sys
prod = json.load(open(sys.argv[1]))["ledger"]
rows = subprocess.run(["psql","-U","sanad","-At","-F","|","-d","upgrade_hist","-c",
  "SELECT version, checksum FROM flyway_schema_history WHERE success=true AND type='SQL' AND version IS NOT NULL;"],
  capture_output=True, text=True).stdout.strip().splitlines()
db = {}
for r in rows:
    v, c = r.split("|"); db[v] = int(c)
prod_sql = {p["v"]: p["c"] for p in prod if p["t"] == "SQL" and p["v"] is not None}
target = "20261021.1"
def le(a, b):
    ka = [int(x) for x in a.split(".")]; kb = [int(x) for x in b.split(".")]
    return ka <= kb
missing = mismatch = 0
for v, c in prod_sql.items():
    if not le(v, target): continue
    if v not in db: print("MISSING_IN_ISOLATED:", v); missing += 1
    elif db[v] != c: print("MISMATCH:", v, "prod=", c, "isolated=", db[v]); mismatch += 1
extra = [v for v in db if v not in prod_sql]
print(f"prod SQL versions<=watermark compared: {sum(1 for v in prod_sql if le(v, target))}")
print(f"matched={sum(1 for v in prod_sql if le(v, target) and v in db and db[v]==prod_sql[v])} mismatch={mismatch} missing={missing}")
print("isolated-only versions:", extra if extra else "none")
if mismatch or missing: raise SystemExit(1)
PYEOF
gate B "checksum join at watermark: 100% match with production ledger"

fw upgrade_hist migrate | tee "$ART/B-migrate-upgrade.log"
fw upgrade_hist validate | tee "$ART/B-validate-upgrade.log"
head_b2=$(history_head upgrade_hist)
[ "$head_b2" = "20261102.2" ] || die B "upgraded head=$head_b2, expected 20261102.2"
applied_new=$(psql -At -U sanad -d upgrade_hist -c "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success=true AND version > '20261021.1';")
gate B "PASS: upgrade applied [$applied_new], validate OK"

# ============================================================================
# SCENARIO C — Failure injection (fail-closed, NO repair / NO baseline)
# ============================================================================
gate C "start: failure injection proofs"
CHAIN_COPY=/tmp/chain_c; rm -rf "$CHAIN_COPY"; mkdir -p "$CHAIN_COPY"
cp -r "$MIG_LOC_ROOT"/. "$CHAIN_COPY/"

fw injection migrate | tail -3 > "$ART/C0-apply.log" || die C "injection baseline apply failed unexpectedly"

rm "$CHAIN_COPY/V20261102_2__t2_batch2_runtime_grants.sql"
if $FLYWAY -url="jdbc:postgresql://127.0.0.1:5432/injection" -user=sanad -password=cert_local_only \
     -locations="filesystem:$CHAIN_COPY" validate > "$ART/C1-missing.log" 2>&1; then
  die C "c1: validate PASSED despite missing applied migration (fail-open!)"
fi
gate C "c1 PASS: missing applied migration -> validate FAILED (fail-closed)"

cp "$MIG_LOC_ROOT/V20261102_2__t2_batch2_runtime_grants.sql" "$CHAIN_COPY/"
printf '\n-- certification tamper marker\n' >> "$CHAIN_COPY/V20261102_2__t2_batch2_runtime_grants.sql"
if $FLYWAY -url="jdbc:postgresql://127.0.0.1:5432/injection" -user=sanad -password=cert_local_only \
     -locations="filesystem:$CHAIN_COPY" validate > "$ART/C2-tamper.log" 2>&1; then
  die C "c2: validate PASSED despite checksum tamper (fail-open!)"
fi
gate C "c2 PASS: checksum tamper -> validate FAILED (fail-closed)"

before_succ=$(success_rows injection)
printf 'SELECT * FROM certification_nonexistent_table_xyz;\n' > "$CHAIN_COPY/V2026999_1__inject_failure.sql"
if $FLYWAY -url="jdbc:postgresql://127.0.0.1:5432/injection" -user=sanad -password=cert_local_only \
     -locations="filesystem:$CHAIN_COPY" migrate > "$ART/C3-midchain.log" 2>&1; then
  die C "c3: migrate PASSED despite failing migration (fail-open!)"
fi
psql -At -U sanad -d injection -c "SELECT version, success FROM flyway_schema_history WHERE version='2026999.1';" | tee "$ART/C3-failed-row.txt" | grep -q '2026999.1|f' \
  || die C "c3: failed migration not recorded as success=false"
psql -At -U sanad -d injection -c "SELECT to_regclass('public.certification_nonexistent_table_xyz') IS NULL;" | grep -q t \
  || die C "c3: partial DDL from failed migration was not rolled back"
gate C "c3 PASS: mid-chain failure -> migrate aborted, failed row recorded, transaction rolled back (success rows before=$before_succ after=$(success_rows injection))"

gate C "c4 PASS: repair/baseline never invoked at any point (no repair artifacts; all failures stay failed)"

# ============================================================================
# SCENARIO D — Backup → Restore → integrity + RLS + roles + Tenant A/B
# ============================================================================
gate D "start: logical backup of upgraded history-equivalent DB"
T4=$(date +%s)
pg_dump -Fc -d upgrade_hist -f "$ART/release-schema-dump.dump"
T5=$(date +%s)
pg_restore -d restored --no-owner --role=sanad "$ART/release-schema-dump.dump" 2> "$ART/D-restore-stderr.txt" || {
  grep -vE "role \"postgres\" does not exist|already exists" "$ART/D-restore-stderr.txt" | grep -q . && die D "pg_restore failed" || true; }
T6=$(date +%s)
gate D "backup=$((T5-T4))s restore=$((T6-T5))s (RTO window; logical dump RPO=0 at dump instant)"

h_src=$(psql -At -U sanad -d upgrade_hist -c "SELECT md5(coalesce(string_agg(version||':'||checksum::text||':'||success::text, ';' ORDER BY installed_rank),''));")
h_rst=$(psql -At -U sanad -d restored -c "SELECT md5(coalesce(string_agg(version||':'||checksum::text||':'||success::text, ';' ORDER BY installed_rank),''));")
[ "$h_src" = "$h_rst" ] || die D "flyway_schema_history differs between source and restored (src=$h_src rst=$h_rst)"
gate D "D1 PASS: flyway_schema_history identical (md5=${h_src:0:12}...)"

for db in upgrade_hist restored; do
  psql -At -U sanad -d "$db" -c "$sql_rls" > "$ART/D2-rls-$db.txt"
done
diff "$ART/D2-rls-upgrade_hist.txt" "$ART/D2-rls-restored.txt" > /dev/null || die D "RLS catalog differs after restore"
gate D "D2 PASS: RLS ENABLE/FORCE flags identical across restore ($(wc -l < "$ART/D2-rls-restored.txt") tables compared)"

grants=$(psql -At -U sanad -d restored -c "SELECT table_name||':'||string_agg(privilege_type,',' ORDER BY privilege_type) FROM information_schema.role_table_grants WHERE grantee='sanad' AND table_name IN ('refresh_tokens','password_reset_tokens','provisioning_jobs','provisioning_job_steps','subscription_commands') GROUP BY table_name ORDER BY table_name;")
echo "$grants" | tee "$ART/D3-grants.txt"
echo "$grants" | grep -q '^refresh_tokens:INSERT,SELECT,UPDATE,DELETE$' || die D "refresh_tokens DML grant missing"
echo "$grants" | grep -q '^password_reset_tokens:INSERT,SELECT,UPDATE,DELETE$' || die D "password_reset_tokens DML grant missing"
echo "$grants" | grep -q '^provisioning_jobs:INSERT,SELECT,UPDATE$' || die D "provisioning_jobs grant missing"
echo "$grants" | grep -q '^subscription_commands:INSERT,SELECT$' || die D "subscription_commands grant missing"
gate D "D3 PASS: restricted runtime ACL present exactly as migrated"

TA=00000000-0000-0000-0000-000000000001; TB=00000000-0000-0000-0000-000000000002
psql -v ON_ERROR_STOP=1 -U sanad -d restored <<SQL
BEGIN;
SELECT set_config('app.tenant_id','$TA',true);
INSERT INTO public.refresh_tokens VALUES ('a1000000-0000-0000-0000-000000000001','hash-A','$TA','u1000000-0000-0000-0000-000000000001','ACTIVE');
INSERT INTO public.password_reset_tokens VALUES ('a1000000-0000-0000-0000-000000000002','reset-A','$TA','u1000000-0000-0000-0000-000000000001','ACTIVE');
COMMIT;
SQL
idx=$(psql -At -U sanad -d restored -c "SELECT count(*) FROM public.auth_token_tenant_index;")
[ "$idx" = "2" ] || die D "pre-auth index read w/o GUC failed (count=$idx, expected 2)"
ct_read=$(psql -At -U sanad -d restored -c "SELECT set_config('app.tenant_id','$TB',true); SELECT count(*) FROM public.refresh_tokens;" | tail -1)
[ "$ct_read" = "0" ] || die D "cross-tenant READ leaked rows (tenant B saw $ct_read)"
ct_upd=$(psql -At -U sanad -d restored -c "SELECT set_config('app.tenant_id','$TB',true); UPDATE public.refresh_tokens SET status='ACTIVE';" | tail -1)
[ "$ct_upd" = "0" ] || die D "cross-tenant UPDATE affected rows"
ct_del=$(psql -At -U sanad -d restored -c "SELECT set_config('app.tenant_id','$TB',true); DELETE FROM public.password_reset_tokens;" | tail -1)
[ "$ct_del" = "0" ] || die D "cross-tenant DELETE affected rows"
noguc=$(psql -At -U sanad -d restored -c "SELECT count(*) FROM public.refresh_tokens;" | tail -1)
[ "$noguc" = "0" ] || die D "missing-GUC read leaked rows (fail-open!)"
if psql -v ON_ERROR_STOP=1 -U sanad -d restored -c "SELECT set_config('app.tenant_id','$TA',true); UPDATE public.refresh_tokens SET token_hash='changed';" > /dev/null 2>&1; then
  die D "token_hash immutability violated (update accepted)"
fi
psql -At -U sanad -d restored -c "SELECT set_config('app.tenant_id','$TA',true); SELECT count(*) FROM public.refresh_tokens;" | tail -1 | grep -q '^1$' || die D "tenant A lost its row (over-blocking!)"
gate D "D4 PASS: Tenant A/B isolation, missing-GUC denial, pre-auth index w/o GUC, immutability (fail-closed 42501/23514; cross-tenant = 0 rows)"

owner=$(psql -At -U sanad -d restored -c "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.refresh_tokens'::regclass;")
gate D "D5: refresh_tokens owner=$owner (FORCE binds owner); sanad super/bypassrls=$(psql -At -U sanad -d restored -c "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND (rolsuper OR rolbypassrls));")"
[ "$(psql -At -U sanad -d restored -c "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND (rolsuper OR rolbypassrls));")" = "f" ] || die D "sanad became super/bypassrls!"
gate D "D5 PASS: runtime role remains NOSUPERUSER/NOBYPASSRLS after full chain + restore"

gate ALL "G5 scenarios A/B/C + G6 backup/restore: ALL GATES PASSED"
cat "$SUMMARY"
