#!/usr/bin/env bash
# Only disposable host-native PG17 on an ephemeral GitHub runner.
set -euo pipefail
[[ "${G1_DISPOSABLE_PG17:-}" == 1 && "${GITHUB_ACTIONS:-}" == true ]] || { echo 'BLOCKED: disposable GitHub runner required'; exit 1; }
[[ "${PGHOST:-}" == 127.0.0.1 && "${PGPORT:-}" == 5432 ]] || { echo 'BLOCKED: nonlocal host/port'; exit 1; }
others="$(sudo -u postgres psql -X -At -d postgres -c "SELECT count(*) FROM pg_database WHERE NOT datistemplate AND datname <> 'postgres';")"
[[ "$others" == 0 ]] || { echo 'BLOCKED: pre-existing user databases'; exit 1; }
version="$(sudo -u postgres psql -X -At -d postgres -c 'SHOW server_version;')"
[[ "$version" == 17.* ]] || { echo "BLOCKED: expected PG17, got $version"; exit 1; }
echo "ISOLATED_POSTGRES_VERSION=$version"
audit_pw="$(openssl rand -hex 24)"
export FLYWAY_AUDIT_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/g1_auditor_cert?sslmode=disable'
export FLYWAY_AUDIT_USERNAME=sanad_release_auditor
export FLYWAY_AUDIT_PASSWORD="$audit_pw"
export PGSSLMODE=disable
umask 077
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d postgres >/dev/null <<SQL
CREATE ROLE sanad LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT;
CREATE ROLE sanad_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT;
CREATE ROLE sanad_release_auditor LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT PASSWORD '$audit_pw';
CREATE DATABASE g1_auditor_cert;
SQL
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d g1_auditor_cert >/dev/null <<'SQL'
CREATE TABLE public.flyway_schema_history (
  installed_rank INTEGER PRIMARY KEY, version TEXT, type TEXT NOT NULL,
  description TEXT, success BOOLEAN NOT NULL, checksum INTEGER
);
INSERT INTO public.flyway_schema_history VALUES
  (1,'15','JDBC','seed rbac roles and capabilities',true,NULL),
  (2,'20260702.1','SQL','create unified crm core',true,11),
  (3,'20260702.2','SQL','reconcile admin role and capabilities',true,12),
  (4,'20260702.3','SQL','complete crm imports custom fields',true,13),
  (5,'20261107.1','SQL','synthetic version head',true,14);
CREATE TABLE public.access_capabilities(code TEXT PRIMARY KEY, status TEXT NOT NULL);
INSERT INTO public.access_capabilities VALUES
  ('WORKFLOW.TASK_EXECUTE','ACTIVE'),
  ('WORKFLOW.MONITOR','ACTIVE'),
  ('WORKFLOW.INCIDENT_MANAGE','ACTIVE');
CREATE TABLE public.workflow_work_items(id INTEGER);
CREATE TABLE public.workflow_incidents(id INTEGER);
CREATE TABLE public.workflow_event_outbox(id INTEGER);
CREATE TABLE public.workflow_event_inbox(id INTEGER);
CREATE TABLE public.workflow_definitions(definition_family_id TEXT);
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE g1_auditor_cert TO sanad_release_auditor;
GRANT USAGE ON SCHEMA public TO sanad_release_auditor;
GRANT SELECT ON public.flyway_schema_history,public.access_capabilities TO sanad_release_auditor;
SQL

bash scripts/production/verify-flyway.sh > /tmp/g1-real-positive.log 2>&1 || { tail -40 /tmp/g1-real-positive.log; exit 1; }
grep -q 'FLYWAY_AUDITOR_IDENTITY_AND_ACL=PASS' /tmp/g1-real-positive.log
grep -q 'PENDING MIGRATIONS: 0' /tmp/g1-real-positive.log
echo 'PG17_REAL_AUDITOR_READ_ONLY=PASS'

if PGPASSWORD="$audit_pw" psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U sanad_release_auditor -d g1_auditor_cert -c 'UPDATE public.flyway_schema_history SET checksum=1 WHERE installed_rank=5;' > /tmp/g1-write-denial.log 2>&1; then
  echo 'FAIL: auditor mutated Flyway'; exit 1
fi
echo 'PG17_REAL_AUDITOR_WRITE_DENIED=PASS'

sql() { sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d g1_auditor_cert -c "$1" >/dev/null; }
sql 'GRANT UPDATE ON public.flyway_schema_history TO sanad_release_auditor;'
if bash scripts/production/verify-flyway.sh >/tmp/g1-auditor-broad.log 2>&1; then echo 'FAIL: auditor UPDATE accepted'; exit 1; fi
grep -q 'minimum read-only contract' /tmp/g1-auditor-broad.log
sql 'REVOKE UPDATE ON public.flyway_schema_history FROM sanad_release_auditor;'
echo 'PG17_BROAD_AUDITOR_ACL_DENIED=PASS'

sql 'GRANT SELECT ON public.flyway_schema_history TO sanad;'
if bash scripts/production/verify-flyway.sh >/tmp/g1-runtime-broad.log 2>&1; then echo 'FAIL: runtime history SELECT accepted'; exit 1; fi
grep -q 'Runtime sanad has unauthorized Flyway' /tmp/g1-runtime-broad.log
sql 'REVOKE SELECT ON public.flyway_schema_history FROM sanad;'
echo 'PG17_RUNTIME_HISTORY_PRIVILEGE_DENIED=PASS'

sql "UPDATE public.flyway_schema_history SET version='20261106.9' WHERE installed_rank=5;"
if bash scripts/production/verify-flyway.sh >/tmp/g1-schema-drift.log 2>&1; then echo 'FAIL: pending migrations accepted'; exit 1; fi
grep -q 'Pending migrations detected' /tmp/g1-schema-drift.log
echo 'PG17_PENDING_SCHEMA_DENIED=PASS'
echo 'PG17_FLYWAY_AUDITOR_ACCEPTANCE=PASS (isolated synthetic data only)'
