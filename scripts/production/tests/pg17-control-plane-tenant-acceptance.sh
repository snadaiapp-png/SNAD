#!/usr/bin/env bash
# Real PostgreSQL 17 acceptance for scripts/production/verify-control-plane-tenant.sh
# (production release incident 43189957654).
#
# Proves, against a real psql client and a real PostgreSQL 17 server with a
# synthetic replica of the production fail-closed RLS policy:
#   1. the defect signature: a single-session `--command="SET app.tenant_id =
#      ...; SELECT COUNT(*) ..."` really prints "SET" before the count row
#      (this is what `[ "SET 1" -ge 1 ]` choked on in run 43189957654);
#   2. the fixed verifier passes against a healthy synthetic database;
#   3. missing or wrong tenant GUC → the V20261029_1 policy default-denies
#      (count 0), so the in-SQL SET is load-bearing, never decorative;
#   4. INACTIVE tenant / INACTIVE ADMIN are real count verdicts, not parse
#      artifacts;
#   5. connection failure and SQL failure (permission denied under
#      ON_ERROR_STOP) fail closed with psql stderr surfaced.
#
# Only a disposable host-native PG17 on an ephemeral GitHub runner; synthetic
# data only; the sole network call the verifier may make is served by a stub
# curl fixture (the Render env-vars listing). No production, Supabase, Render
# or Vercel access.
set -euo pipefail
[[ "${G1_DISPOSABLE_PG17:-}" == 1 && "${GITHUB_ACTIONS:-}" == true ]] || { echo 'BLOCKED: disposable GitHub runner required'; exit 1; }
[[ "${PGHOST:-}" == 127.0.0.1 && "${PGPORT:-}" == 5432 ]] || { echo 'BLOCKED: nonlocal host/port'; exit 1; }
others="$(sudo -u postgres psql -X -At -d postgres -c "SELECT count(*) FROM pg_database WHERE NOT datistemplate AND datname <> 'postgres';")"
[[ "$others" == 0 ]] || { echo 'BLOCKED: pre-existing user databases'; exit 1; }
version="$(sudo -u postgres psql -X -At -d postgres -c 'SHOW server_version;')"
[[ "$version" == 17.* ]] || { echo "BLOCKED: expected PG17, got $version"; exit 1; }
echo "ISOLATED_POSTGRES_VERSION=$version"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT_UNDER_TEST="$REPO_ROOT/scripts/production/verify-control-plane-tenant.sh"
[ -f "$SCRIPT_UNDER_TEST" ] || { echo 'BLOCKED: verifier not found'; exit 1; }
bash -n "$SCRIPT_UNDER_TEST"

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

CP_TENANT='11111111-1111-1111-1111-111111111111'
OTHER_TENANT='22222222-2222-2222-2222-222222222222'
DB_NAME='g1_control_plane_cert'
DB_PW="$(openssl rand -hex 24)"

# ---------------------------------------------------------------------------
# Synthetic database: tenants is platform-reference data (readable by the
# runtime identity, exactly like production — it is NOT in the V20261029_1
# tenant-owned table list); roles replicates the production table with the
# verbatim V20261029_1 fail-closed policy.
# ---------------------------------------------------------------------------
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d postgres >/dev/null <<SQL
CREATE ROLE sanad LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT PASSWORD '$DB_PW';
CREATE DATABASE $DB_NAME;
SQL

sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null <<'SQL'
CREATE TABLE public.tenants (id uuid PRIMARY KEY, status text NOT NULL);
CREATE TABLE public.roles (id uuid PRIMARY KEY, tenant_id uuid NOT NULL, code text NOT NULL, status text NOT NULL);
ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON public.roles FOR ALL
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE g1_control_plane_cert TO sanad;
GRANT USAGE ON SCHEMA public TO sanad;
GRANT SELECT ON public.tenants, public.roles TO sanad;
SQL

sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null <<SQL
INSERT INTO public.tenants (id, status) VALUES
  ('$CP_TENANT', 'ACTIVE'),
  ('$OTHER_TENANT', 'ACTIVE');
INSERT INTO public.roles (id, tenant_id, code, status) VALUES
  ('33333333-3333-3333-3333-333333333331', '$CP_TENANT', 'ADMIN', 'ACTIVE'),
  ('33333333-3333-3333-3333-333333333332', '$CP_TENANT', 'USER', 'ACTIVE'),
  ('33333333-3333-3333-3333-333333333333', '$OTHER_TENANT', 'ADMIN', 'ACTIVE');
SQL

# ---------------------------------------------------------------------------
# Stub curl — the verifier's only network call (read-only Render env-vars
# listing) is served locally; the DATABASE_URL points at the disposable PG17.
# ---------------------------------------------------------------------------
STUB_BIN="$WORK/bin"
mkdir -p "$STUB_BIN"
cat > "$STUB_BIN/curl" << EOF
#!/usr/bin/env bash
set -euo pipefail
printf '[{"key":"DATABASE_URL","value":"%s"},{"key":"DATABASE_PASSWORD","value":"%s"}]\n' \
  "\${STUB_DB_URL:-postgresql://127.0.0.1:5432/$DB_NAME}" \
  "\${STUB_DB_PASSWORD:?STUB_DB_PASSWORD is required}"
EOF
chmod +x "$STUB_BIN/curl"

export PATH="$STUB_BIN:$PATH"
export PGSSLMODE=disable
export STUB_DB_PASSWORD="$DB_PW"

run_verifier() {
  RENDER_API_KEY="stub-render-key" \
  RENDER_SERVICE_ID="stub-srv-0001" \
  DATABASE_USERNAME="sanad" \
  CONTROL_PLANE_TENANT_ID="$CP_TENANT" \
  bash "$SCRIPT_UNDER_TEST"
}

psql_as_sanad() {
  PGPASSWORD="$DB_PW" psql -X -h 127.0.0.1 -p 5432 -U sanad -d "$DB_NAME" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align "$@"
}

# ---------------------------------------------------------------------------
# 1. The defect signature on REAL psql + PG17: single-session SET+SELECT
#    prints the SET command tag before the count row.
# ---------------------------------------------------------------------------
psql_as_sanad --command="SET app.tenant_id = '$CP_TENANT'; SELECT COUNT(*) FROM roles WHERE tenant_id = '$CP_TENANT' AND code = 'ADMIN' AND status = 'ACTIVE';" \
  > "$WORK/defect-shape.out"
if [ "$(wc -l < "$WORK/defect-shape.out")" -eq 2 ] \
   && [ "$(sed -n 1p "$WORK/defect-shape.out")" = "SET" ] \
   && [ "$(sed -n 2p "$WORK/defect-shape.out")" = "1" ]; then
  echo 'PG17_DEFECT_SIGNATURE_REPRODUCED=PASS (real psql prints "SET" before the count row)'
else
  echo 'FAIL: real psql single-session output was not the expected SET+count shape:'
  cat -A "$WORK/defect-shape.out"
  exit 1
fi

# ---------------------------------------------------------------------------
# 2. Missing / empty / wrong tenant context default-denies on the REAL policy
#    (this is why the in-SQL SET must stay in the same session).
# ---------------------------------------------------------------------------
no_guc="$(psql_as_sanad --command="SELECT COUNT(*) FROM roles WHERE tenant_id = '$CP_TENANT' AND code = 'ADMIN' AND status = 'ACTIVE';")"
[ "$no_guc" = "0" ] || { echo "FAIL: missing GUC returned $no_guc, expected 0"; exit 1; }
echo 'PG17_RLS_DEFAULT_DENY_WITHOUT_GUC=PASS (count 0)'

empty_guc="$(psql_as_sanad --command="SET app.tenant_id = ''; SELECT COUNT(*) FROM roles WHERE tenant_id = '$CP_TENANT' AND code = 'ADMIN' AND status = 'ACTIVE';" | sed -n 2p)"
[ "$empty_guc" = "0" ] || { echo "FAIL: empty GUC returned $empty_guc, expected 0"; exit 1; }
echo 'PG17_RLS_DEFAULT_DENY_EMPTY_GUC=PASS (count 0)'

wrong_guc="$(psql_as_sanad --command="SET app.tenant_id = '$OTHER_TENANT'; SELECT COUNT(*) FROM roles WHERE tenant_id = '$CP_TENANT' AND code = 'ADMIN' AND status = 'ACTIVE';" | sed -n 2p)"
[ "$wrong_guc" = "0" ] || { echo "FAIL: wrong-tenant GUC returned $wrong_guc, expected 0"; exit 1; }
echo 'PG17_RLS_CROSS_TENANT_DENY=PASS (count 0)'

# ---------------------------------------------------------------------------
# 3. The fixed verifier passes end-to-end against the healthy synthetic DB.
# ---------------------------------------------------------------------------
if run_verifier > "$WORK/pass.out" 2>&1 \
   && grep -q "Control Plane tenant verified: ACTIVE tenant with ADMIN role exists." "$WORK/pass.out"; then
  echo 'PG17_VERIFIER_HEALTHY_PASS=PASS'
else
  echo 'FAIL: verifier did not pass the healthy synthetic database:'
  sed -n '1,40p' "$WORK/pass.out"
  exit 1
fi

# ---------------------------------------------------------------------------
# 4. INACTIVE ADMIN is a real count verdict.
# ---------------------------------------------------------------------------
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null \
  -c "UPDATE public.roles SET status='INACTIVE' WHERE tenant_id='$CP_TENANT' AND code='ADMIN';"
if run_verifier > "$WORK/inactive-admin.out" 2>&1; then
  echo 'FAIL: verifier passed with an INACTIVE ADMIN'
  exit 1
fi
grep -q "No ACTIVE ADMIN role for Control Plane tenant" "$WORK/inactive-admin.out" || {
  echo 'FAIL: INACTIVE ADMIN did not produce the ADMIN verdict:'
  sed -n '1,40p' "$WORK/inactive-admin.out"
  exit 1
}
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null \
  -c "UPDATE public.roles SET status='ACTIVE' WHERE tenant_id='$CP_TENANT' AND code='ADMIN';"
echo 'PG17_VERIFIER_INACTIVE_ADMIN_FAIL=PASS'

# ---------------------------------------------------------------------------
# 5. INACTIVE tenant is a real count verdict.
# ---------------------------------------------------------------------------
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null \
  -c "UPDATE public.tenants SET status='INACTIVE' WHERE id='$CP_TENANT';"
if run_verifier > "$WORK/inactive-tenant.out" 2>&1; then
  echo 'FAIL: verifier passed with an INACTIVE tenant'
  exit 1
fi
grep -q "Control Plane tenant not found or not ACTIVE" "$WORK/inactive-tenant.out" || {
  echo 'FAIL: INACTIVE tenant did not produce the tenant verdict:'
  sed -n '1,40p' "$WORK/inactive-tenant.out"
  exit 1
}
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null \
  -c "UPDATE public.tenants SET status='ACTIVE' WHERE id='$CP_TENANT';"
echo 'PG17_VERIFIER_INACTIVE_TENANT_FAIL=PASS'

# ---------------------------------------------------------------------------
# 6. Connection failure (closed port) fails closed with stderr surfaced.
# ---------------------------------------------------------------------------
export STUB_DB_URL="postgresql://127.0.0.1:5433/$DB_NAME"
if run_verifier > "$WORK/connfail.out" 2>&1; then
  echo 'FAIL: verifier passed despite connection failure'
  exit 1
fi
unset STUB_DB_URL
grep -q "psql failed while verifying the Control Plane tenant exists" "$WORK/connfail.out" || {
  echo 'FAIL: connection failure did not produce the psql-failure verdict:'
  sed -n '1,40p' "$WORK/connfail.out"
  exit 1
}
grep -qi "connection refused\|failed to connect\|could not connect" "$WORK/connfail.out" || {
  echo 'FAIL: connection failure stderr was not surfaced:'
  sed -n '1,40p' "$WORK/connfail.out"
  exit 1
}
echo 'PG17_VERIFIER_CONNECTION_FAILURE_FAIL=PASS'

# ---------------------------------------------------------------------------
# 7. SQL failure (permission denied under ON_ERROR_STOP) fails closed.
# ---------------------------------------------------------------------------
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null \
  -c "REVOKE SELECT ON public.roles FROM sanad;"
if run_verifier > "$WORK/sqlfail.out" 2>&1; then
  echo 'FAIL: verifier passed despite permission denied on roles'
  exit 1
fi
grep -q "psql failed while verifying the ACTIVE ADMIN role exists" "$WORK/sqlfail.out" || {
  echo 'FAIL: permission denial did not produce the psql-failure verdict:'
  sed -n '1,40p' "$WORK/sqlfail.out"
  exit 1
}
grep -q "permission denied for table roles" "$WORK/sqlfail.out" || {
  echo 'FAIL: permission denial stderr was not surfaced:'
  sed -n '1,40p' "$WORK/sqlfail.out"
  exit 1
}
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null \
  -c "GRANT SELECT ON public.roles TO sanad;"
echo 'PG17_VERIFIER_SQL_FAILURE_FAIL=PASS'

echo 'PG17_CONTROL_PLANE_TENANT_ACCEPTANCE=PASS (isolated synthetic data only)'
