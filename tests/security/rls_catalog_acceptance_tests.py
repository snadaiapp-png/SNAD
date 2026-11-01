#!/usr/bin/env python3
"""Catalog/RPC/Transaction/Negative-control acceptance tests — standalone runner.
Writes into pgdirect_results.json (merged with per-table results)."""
import psycopg, json, uuid, sys
from rls_evidence import probe_trigger_execute, summarize_evidence

DSN = "host=127.0.0.1 port=5433 dbname=sanad user=sanad password=sanad_local_test"
TEN_A = str(uuid.UUID(int=0xA1)); TEN_B = str(uuid.UUID(int=0xB2))
RESULTS = '/home/z/my-project/evidence-rls/pgdirect_results.json'
data = json.load(open(RESULTS))
tests = []
data['tests'] = tests
def record(tid, name, status, detail=""):
    tests.append({"id": tid, "test": name, "status": status, "detail": str(detail)[:300]})
    print(f"  {tid} -> {status} | {str(detail)[:90]}", flush=True)
def q1(cur, sql, p=None):
    try: return cur.execute(sql, p or ()).fetchone()
    except psycopg.Error as e:
        try: cur.connection.rollback()
        except Exception: pass
        return ("ERROR", str(e).split('\n')[0][:160])

admin2 = psycopg.connect(DSN)
bypass = admin2.execute("SELECT rolname FROM pg_roles WHERE rolbypassrls AND rolname<>'postgres'").fetchall()
record("RLS-06a", "No non-postgres BYPASSRLS roles", "PASS" if not bypass else "FAIL", bypass)
pub = admin2.execute("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee IN ('PUBLIC','anon','authenticated') AND table_schema='public'").fetchone()[0]
record("RLS-06b", "Zero PUBLIC/anon/authenticated table grants", "PASS" if pub == 0 else "FAIL", f"grants={pub}")
sd = admin2.execute("""SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef
 AND (has_function_privilege('anon', p.oid,'EXECUTE') OR has_function_privilege('authenticated', p.oid,'EXECUTE'))""").fetchone()[0]
record("RPC-01a", "No anon/authenticated-callable SECURITY DEFINER functions", "PASS" if sd == 0 else "FAIL", f"count={sd}")
fo = admin2.execute(r"""
SELECT count(*) FROM pg_policies WHERE schemaname='public'
 AND (qual ~* $q$current_setting\('app\.tenant_id'[^)]*\)\s+IS(?!\s+NOT)\s+NULL$q$
      OR with_check ~* $q$current_setting\('app\.tenant_id'[^)]*\)\s+IS(?!\s+NOT)\s+NULL$q$)
""").fetchone()[0]
record("RLS-04a", "Zero NULL-allow tenant-context policies in catalog", "PASS" if fo == 0 else "FAIL", f"count={fo}")
unpinned = admin2.execute("""
SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public'
AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid='pg_proc'::regclass AND d.objid=p.oid AND d.deptype IN ('e','x'))
AND (p.proconfig IS NULL OR NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) cfg WHERE cfg LIKE 'search_path=%'))
ORDER BY 1""").fetchall()
unpinned_names = [r[0] for r in unpinned]
unpinned = len(unpinned_names)
record("FN-01", "All non-extension public functions have pinned search_path", "PASS" if unpinned == 0 else "FAIL", f"count={unpinned} {unpinned_names[:15]}")
fo_rls = admin2.execute("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') AND NOT c.relrowsecurity").fetchone()[0]
record("RLS-06c", "Tables without RLS remaining (must be platform/reference/T2 only)", "INFO", f"count={fo_rls}")

# RPC-01/02: anon/authenticated cannot execute the pre-auth trigger function
for role, tid in (('anon', 'RPC-01'), ('authenticated', 'RPC-02')):
    status, detail = probe_trigger_execute(lambda: psycopg.connect(DSN), role)
    record(tid, f"{role} cannot execute sync_login_tenant_index()", status, detail)

# RPC-03: trigger maintains login_tenant_index across INSERT/DELETE
try:
    cu = psycopg.connect(DSN); cur = cu.cursor()
    cur.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_A,))
    cur.execute("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (%s,'SynthA2','synth-a2-r','ACTIVE',now(),now()) ON CONFLICT DO NOTHING", (TEN_A,))
    uid = str(uuid.uuid4())   # fresh user each run so ON CONFLICT never skips the trigger
    email = f"synth-{uuid.uuid4().hex[:10]}@synth.local"
    username = f"su{uuid.uuid4().hex[:10]}"
    cur.execute("""INSERT INTO users (id, tenant_id, email, username, password_hash, status, created_at, updated_at)
                   VALUES (%s,%s,%s,%s,'x','ACTIVE',now(),now()) ON CONFLICT DO NOTHING""",
                (uid, TEN_A, email, username))
    cu.commit()
    idx = q1(cur, "SELECT count(*) FROM login_tenant_index WHERE tenant_id=%s AND identifier IN (%s, %s)", (TEN_A, email, username))
    trig_ok = bool(idx and not (isinstance(idx, tuple) and idx[0] == "ERROR") and idx[0] >= 1)
    cur.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_A,))   # re-establish context (prior commit cleared it — fail-closed held!)
    cur.execute("DELETE FROM users WHERE id=%s", (uid,)); cu.commit()
    idx2 = q1(cur, "SELECT count(*) FROM login_tenant_index WHERE tenant_id=%s AND identifier IN (%s, %s)", (TEN_A, email, username))
    trig_del = bool(idx2 and idx2[0] == 0)
    record("RPC-03", "users trigger maintains login_tenant_index (insert+delete)", "PASS" if (trig_ok and trig_del) else "FAIL", f"insert={trig_ok} delete={trig_del}")
except Exception as e:
    record("RPC-03", "users trigger maintains login_tenant_index", "ERROR", str(e)[:150])

# TX-01..04: transaction locality / connection reuse
try:
    c = psycopg.connect(DSN); cur = c.cursor()
    cur.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_A,))
    cur.execute("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (%s,'A2x','synth-a2x','ACTIVE',now(),now()) ON CONFLICT DO NOTHING", (TEN_A,))
    c.commit()
    r = q1(cur, "SELECT current_setting('app.tenant_id', true)")
    g = r[0] if r else None
    record("TX-01", "GUC does not persist after commit (SET LOCAL scope)", "PASS" if g in ('', None) else "FAIL", f"after_commit={g!r}")
    cur.execute("BEGIN")
    cur.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_A,))
    c.rollback()
    r = q1(cur, "SELECT current_setting('app.tenant_id', true)")
    g = r[0] if r else None
    record("TX-02", "GUC does not persist after rollback", "PASS" if g in ('', None) else "FAIL", f"after_rollback={g!r}")
    cur.execute("BEGIN")
    cur.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_A,))
    ra = q1(cur, "SELECT count(*) FROM hr_departments WHERE tenant_id=%s", (TEN_A,))
    cur.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_B,))
    rva = q1(cur, "SELECT count(*) FROM hr_departments WHERE tenant_id=%s", (TEN_A,))
    cur.execute("ROLLBACK")
    record("TX-03", "In-transaction context switch is immediate (last wins)", "PASS" if (ra and ra[0] == 1 and rva and rva[0] == 0) else "FAIL", f"A={ra} thenA={rva}")
    cb = psycopg.connect(DSN); curb = cb.cursor()
    curb.execute("SELECT set_config('app.tenant_id', %s, true)", (TEN_B,))
    curb.execute("UPDATE hr_departments SET name='hijack-attempt' WHERE tenant_id=%s", (TEN_A,))
    record("TX-04", "B cannot update A rows on a FORCE-RLS table", "PASS" if curb.rowcount == 0 else "FAIL", f"rowcount={curb.rowcount}")
except Exception as e:
    record("TX-01", "transaction locality suite", "ERROR", str(e)[:150])

# NEG-01: the suite DETECTS a fail-open policy (detector sensitivity proof)
try:
    cadmin = psycopg.connect("host=127.0.0.1 port=5433 dbname=sanad user=postgres")
    cur = cadmin.cursor()
    cur.execute("DROP TABLE IF EXISTS zz_failopen_probe")
    cur.execute("CREATE TABLE zz_failopen_probe (id uuid primary key default gen_random_uuid(), tenant_id uuid, note text)")
    cur.execute("INSERT INTO zz_failopen_probe (tenant_id, note) VALUES (%s,'secret-A')", (TEN_A,))
    cur.execute("GRANT SELECT ON zz_failopen_probe TO sanad")
    cur.execute("""CREATE POLICY fail_open_probe ON zz_failopen_probe FOR ALL TO sanad
                   USING (current_setting('app.tenant_id', true) IS NULL OR tenant_id::text = current_setting('app.tenant_id', true))""")
    cadmin.commit()
    cs = psycopg.connect(DSN); curs = cs.cursor()
    r = q1(curs, "SELECT count(*) FROM zz_failopen_probe")   # GUC never set
    leak = bool(r and r[0] == 1)
    record("NEG-01", "Negative control: fail-open policy DETECTED (missing ctx leaks row)", "PASS" if leak else "FAIL", f"leak={leak}")
    cs.rollback(); cs.close()   # release AccessShareLock before DROP
    cur.execute("DROP TABLE IF EXISTS zz_failopen_probe"); cadmin.commit()
except Exception as e:
    record("NEG-01", "negative control", "ERROR", str(e)[:150])

# verdict
pt = data.get('per_table', [])
data['summary'] = summarize_evidence(tests, pt)
status_counts = data['summary']['per_table_status']
fails = [t for t in tests if t['status'] in ('FAIL', 'ERROR', 'BLOCKED')]
json.dump(data, open(RESULTS, 'w'), indent=1, default=str)
print("\n==== SUMMARY ====")
print("per-table:", dict(status_counts))
print("catalog tests failed:", len(fails), [t['id'] for t in fails])
print("OVERALL:", data['summary']['overall'])
sys.exit(0 if data['summary']['overall'] == 'PASS' else 1)
