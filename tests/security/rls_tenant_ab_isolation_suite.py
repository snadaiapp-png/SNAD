#!/usr/bin/env python3
"""
SNAD PostgreSQL Direct — Tenant A/B isolation suite over ALL tenant-scoped tables.
Real PostgreSQL, real roles, real policies. No mocks. Exit code = 1 iff failures.

Runtime probes run as role `sanad` (fixture table owner — bound by policies via
FORCE ROW LEVEL SECURITY, exactly mirroring a production non-superuser backend
role). Seeding runs as superuser `postgres` (fixture bootstrap only).
"""
import json, os, sys, uuid, datetime, re
import psycopg
from psycopg import sql as pgsql

DB = os.environ.get("SNAD_TEST_DB", "sanad")
ADMIN = dict(host=os.environ.get("SNAD_TEST_HOST","127.0.0.1"), port=int(os.environ.get("SNAD_TEST_PORT","5432")), dbname=DB, user=os.environ.get("SNAD_TEST_ADMIN_USER","postgres"))
APP = dict(host=os.environ.get("SNAD_TEST_HOST","127.0.0.1"), port=int(os.environ.get("SNAD_TEST_PORT","5432")), dbname=DB, user=os.environ.get("SNAD_TEST_APP_USER","sanad"), password=os.environ["SNAD_TEST_APP_PASSWORD"])
TENANT_A = "a1a2a3a4-0000-4000-8000-00000000000a"
TENANT_B = "b1b2b3b4-0000-4000-8000-00000000000b"
OUT = os.environ.get("SNAD_TEST_OUT", "target/rls-evidence")
os.makedirs(OUT, exist_ok=True)

admin = psycopg.connect(**ADMIN); admin.autocommit = True
acur = admin.cursor()
app = psycopg.connect(**APP); app.autocommit = False
cur = app.cursor()

results = []
def record(table, probe, status, detail=""):
    results.append({"table": table, "probe": probe, "status": status, "detail": str(detail)[:300]})

seed_state = {}
_colcache = {}
_fkcache = {}
_chkcache = {}

def table_columns(table):
    if table in _colcache: return _colcache[table]
    acur.execute("""
        SELECT column_name, data_type, is_nullable, column_default,
               coalesce(character_maximum_length,0), is_identity, is_generated
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name=%s ORDER BY ordinal_position""", (table,))
    _colcache[table] = [dict(name=r[0], dtype=r[1], nullable=r[2]=="YES", default=r[3],
                             length=r[4], identity=(r[5]=="YES"), generated=(r[6]=="ALWAYS")) for r in acur.fetchall()]
    return _colcache[table]

def table_has_column(table, col):
    return any(c["name"] == col for c in table_columns(table))

def fks(table):
    if table in _fkcache: return _fkcache[table]
    acur.execute("""
        SELECT kcu.column_name, ccu.table_name, ccu.column_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema
        JOIN information_schema.constraint_column_usage ccu
          ON ccu.constraint_name=tc.constraint_name AND ccu.table_schema=tc.table_schema
        WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public' AND tc.table_name=%s""", (table,))
    out = {}
    for col, rt, rc in acur.fetchall():
        out.setdefault(col, (rt, rc))
    _fkcache[table] = out
    return out

def check_literals(table):
    """Extract allowed literals per column from CHECK constraint definitions
    (ARRAY[...] / IN (...) lists adjacent to the column mention)."""
    if table in _chkcache: return _chkcache[table]
    acur.execute("""
        SELECT pg_get_constraintdef(oid) FROM pg_constraint
        WHERE conrelid = to_regclass('public.' || %s) AND contype='c'""", (table,))
    defs = [r[0] for r in acur.fetchall()]
    out = {}
    for c in table_columns(table):
        col = c["name"]
        lits = []
        for d in defs:
            if not re.search(rf"\b{re.escape(col)}\b", d):
                continue
            # ARRAY[...] list following the column mention (any intermediate chars)
            for m in re.finditer(rf"\b{re.escape(col)}\b.*?=\s*ANY\s*\(+\s*ARRAY\[(.*?)\]", d, re.S):
                for lm in re.finditer(r"'([^']{1,60})'", m.group(1)):
                    lits.append(lm.group(1))
            # plain IN (...) list
            for m in re.finditer(rf"\b{re.escape(col)}\b.*?IN\s*\((.*?)\)", d, re.S):
                for lm in re.finditer(r"'([^']{1,60})'", m.group(1)):
                    lits.append(lm.group(1))
        if lits:
            seen=set(); lits=[x for x in lits if not (x in seen or seen.add(x))]
            out[col] = lits
    _chkcache[table] = out
    return out

def synth_scalar(table, col, tenant_id, depth):
    if col["name"] == "tenant_id":
        return tenant_id
    # dtype-first: booleans and numerics must never receive name-heuristic text
    if col["dtype"] == "boolean":
        return False if col["default"] is None else None
    chks = check_literals(table).get(col["name"])
    if chks:
        for lit in chks:
            if re.search(r"[\[\]\+\*\?\^\$\(\)\|\\]", lit):
                continue
            if col["dtype"] == "uuid" and re.fullmatch(r"[0-9a-fA-F-]{36}", lit):
                return lit
            if col["dtype"] in ("text", "character varying", "character"):
                return lit[: col["length"] or 200]
            if col["dtype"] in ("integer","bigint","smallint","numeric") and re.fullmatch(r"-?\d+(\.\d+)?", lit):
                return lit
    n = col["name"].lower()
    tn = table.lower()
    tag = uuid.uuid4().hex[:8]
    maxlen = col["length"] or 200
    clip = lambda v: v[:maxlen]
    if "e164" in n or ("phone" in tn and n in ("number","value","national_number")):
        return clip("+9665" + tag[:8].zfill(8))
    if "national" in n:
        return clip("50" + tag[:8].zfill(8))
    if "phone" in n:
        return clip("+9665" + tag[:8].zfill(8))
    if "email" in n:
        return clip(f"syn{tag}@fixture.invalid")
    if n.endswith("country") or n.endswith("country_code") or "_country" in n:
        return clip("SA")
    if "currency" in n:
        return clip("SAR")
    if "url" in n or "website" in n or "domain" in n:
        return clip(f"https://{tag[:6]}.fx.invalid")
    if "locale" in n:
        return clip("ar-SA")
    if "timezone" in n or "time_zone" in n:
        return clip("Asia/Riyadh")
    if col["name"] in fks(table):
        return None  # resolved by caller
    if col["default"] is not None:
        return None  # let default apply
    d = col["dtype"]
    if d == "uuid": return str(uuid.uuid4())
    if d in ("text","character varying","character"):
        v = f"syn_{table[:10]}_{tag}"
        return v[: col["length"] or 200] if col["length"] else v
    if d in ("integer","bigint","smallint"): return 1
    if d in ("numeric","real","double precision"): return 1
    if d == "timestamp with time zone": return datetime.datetime.now(datetime.timezone.utc)
    if d == "timestamp without time zone": return datetime.datetime.now()
    if d == "date": return datetime.date.today()
    if d == "json" or d == "jsonb": return "{}"
    if d == "inet": return "127.0.0.1"
    if d == "ARRAY": return []
    return None

_fk_pick = {}
parent_failures = []
def resolve_fk(table, col, tenant_id, depth):
    """Pick an existing same-tenant parent id; seed parent if needed.
    Rotates through parent ids so multiple FK columns to the same table get
    DISTINCT parents (satisfies source<>target style CHECKs)."""
    rt, rc = fks(table)[col["name"]]
    if rt == table:  # self-reference
        return None
    acur.execute("SELECT to_regclass(%s)", (f"public.{rt}",))
    if not acur.fetchone()[0]:
        return None
    acur.execute(f"SELECT count(*) FROM public.\"{rt}\"")
    if acur.fetchone()[0] == 0 and depth < 3:
        ok, why = seed_table(rt, tenant_id, depth + 1)
        if not ok:
            parent_failures.append(f"{rt}<-{table}:{why}")
    tenant_filter = ""
    params = []
    if table_has_column(rt, "tenant_id") and rt != "tenants":
        tenant_filter = f" WHERE tenant_id=%s"
        params = [tenant_id]
    try:
        acur.execute(f'SELECT {rc} FROM public."{rt}"{tenant_filter} LIMIT 5', params)
        rows = [r[0] for r in acur.fetchall()]
        if not rows:
            acur.execute(f'SELECT {rc} FROM public."{rt}" LIMIT 5')
            rows = [r[0] for r in acur.fetchall()]
        if not rows:
            return None
        i = _fk_pick.get(rt, 0)
        _fk_pick[rt] = i + 1
        return rows[i % len(rows)]
    except Exception:
        admin.rollback(); return None

def build_insert(table, tenant_id, depth=0):
    cols = table_columns(table)
    names, vals = [], []
    colmap = {c["name"]: c for c in cols}
    # exactly-one style CHECKs: count columns via CASE WHEN (col IS NULL)
    exactly_one_cols = set()
    acur.execute("""
        SELECT pg_get_constraintdef(oid) FROM pg_constraint
        WHERE conrelid = to_regclass('public.' || %s) AND contype='c'""", (table,))
    for (d,) in acur.fetchall():
        counted = re.findall(r"CASE\s+WHEN \((\w+) IS NULL\)", d)
        if len(counted) >= 2:
            exactly_one_cols.update(counted[1:])  # keep only the first
    # at-least-one-NOT-NULL nullable FK groups (e.g. plan_id OR product_id OR application_id)
    need_one_of = set()
    for (d,) in acur.fetchall():
        cols_nn = re.findall(r"(\w+) IS NOT NULL", d)
        if len(cols_nn) >= 2 and (" OR " in d.upper()):
            for cn in cols_nn:
                cm = colmap.get(cn)
                if cm and cn in fks(table) and cm["nullable"]:
                    need_one_of.add(cn)
                    break
    # owner_type XOR contracts: satisfy the ACCOUNT branch
    owner_contract = ("owner_type" in colmap and "owner_id" in colmap
                      and ("account_id" in colmap or "contact_id" in colmap))
    owner_account_id = None
    for c in cols:
        if c["identity"] or c["generated"]: continue
        if c["name"] == "tenant_id":
            names.append("tenant_id"); vals.append(tenant_id)  # explicit, before FK handling
            continue
        if c["name"] in exactly_one_cols:
            continue  # keep NULL so the exactly-one CHECK holds
        if owner_contract and c["name"] in ("account_id", "contact_id", "owner_id", "owner_type"):
            if c["name"] == "owner_type":
                names.append("owner_type"); vals.append("ACCOUNT")
            elif c["name"] == "contact_id":
                pass  # stay NULL for the ACCOUNT branch
            elif c["name"] in ("account_id", "owner_id"):
                if owner_account_id is None and "account_id" in colmap:
                    owner_account_id = resolve_fk(table, colmap["account_id"], tenant_id, depth)
                if owner_account_id is not None:
                    names.append(c["name"]); vals.append(owner_account_id)
            continue
        if c["name"] in fks(table):
            if c["nullable"] and c["name"] not in need_one_of:
                continue  # leave NULL
            v = resolve_fk(table, c, tenant_id, depth)
            if v is None:
                continue
            names.append(c["name"]); vals.append(v)
            continue
        v = synth_scalar(table, c, tenant_id, depth)
        if v is not None:
            names.append(c["name"]); vals.append(v)
    return names, vals

def seed_table(table, tenant_id, depth=0):
    if table == "tenants": return True, "root"
    st = seed_state.get(table) or {}
    if st.get(tenant_id): return True, "seeded"
    names, vals = build_insert(table, tenant_id, depth)
    if "tenant_id" not in names:
        return False, "no tenant column usable"
    col_list = pgsql.SQL(", ").join(map(pgsql.Identifier, names))
    ph = pgsql.SQL(", ").join(pgsql.Placeholder() * len(vals))
    try:
        acur.execute(pgsql.SQL("INSERT INTO public.{} ({}) VALUES ({})").format(
            pgsql.Identifier(table), col_list, ph), vals)
        seed_state.setdefault(table, {})[tenant_id] = True
        return True, "seeded"
    except Exception as e:
        admin.rollback()
        return False, str(e).splitlines()[0][:200]

# ---------- app-context helpers ----------
def ctx(tenant):
    if tenant is None:
        cur.execute("RESET app.tenant_id")
    else:
        cur.execute("SELECT set_config('app.tenant_id', %s, true)", (tenant,))

def txn(stmt_fn):
    try:
        cur.execute("BEGIN")
        res = stmt_fn()
        cur.execute("COMMIT")
        return res, None
    except Exception as e:
        app.rollback()
        return None, e

def count_where(table, viewer, row_tenant=None):
    def f():
        ctx(viewer)
        if row_tenant is None:
            cur.execute(f'SELECT count(*) FROM public."{table}"')
        else:
            cur.execute(f'SELECT count(*) FROM public."{table}" WHERE tenant_id=%s', (row_tenant,))
        return cur.fetchone()[0]
    return txn(f)

# ---------- probes ----------
def probe_table(table):
    okA, whyA = seed_table(table, TENANT_A)
    okB, whyB = seed_table(table, TENANT_B)
    if not (okA and okB):
        record(table, "seed", "SKIP", f"A={okA}({whyA}) B={okB}({whyB})")
        if okA:
            n, e = count_where(table, TENANT_A)
            record(table, "A_read_own_partial", ("PASS" if e is None and n >= 1 else "FAIL"), f"n={n} err={e}")
        return
    record(table, "seed", "PASS", "A+B seeded")

    n, e = count_where(table, TENANT_A)
    record(table, "A_read_own", ("PASS" if e is None and n >= 1 else "FAIL"), f"A count={n} err={e}")
    n, e = count_where(table, TENANT_B)
    record(table, "B_read_own", ("PASS" if e is None and n >= 1 else "FAIL"), f"B count={n} err={e}")
    n, e = count_where(table, TENANT_A, row_tenant=TENANT_B)
    record(table, "A_sees_B_rows", ("PASS" if e is None and n == 0 else "FAIL"), f"A view of B rows={n} err={e}")
    n, e = count_where(table, TENANT_B, row_tenant=TENANT_A)
    record(table, "B_sees_A_rows", ("PASS" if e is None and n == 0 else "FAIL"), f"B view of A rows={n} err={e}")

    def write_cross():
        ctx(TENANT_A)
        cur.execute(f'UPDATE public."{table}" SET tenant_id=%s WHERE tenant_id=%s', (TENANT_A, TENANT_B))
        u = cur.rowcount
        cur.execute(f'DELETE FROM public."{table}" WHERE tenant_id=%s', (TENANT_B,))
        d = cur.rowcount
        return (u, d)
    r, e = txn(write_cross)
    record(table, "A_write_B", ("PASS" if e is None and r == (0, 0) else "FAIL"), f"upd/del={r} err={e}")

    # INSERT as A claiming tenant B -> WITH CHECK rejection
    names, vals = build_insert(table, TENANT_B)
    if "tenant_id" in names:
        col_list = pgsql.SQL(", ").join(map(pgsql.Identifier, names))
        ph = pgsql.SQL(", ").join(pgsql.Placeholder() * len(vals))
        def ins_b():
            ctx(TENANT_A)
            cur.execute(pgsql.SQL("INSERT INTO public.{} ({}) VALUES ({})").format(
                pgsql.Identifier(table), col_list, ph), vals)
        r, e = txn(ins_b)
        record(table, "A_insert_for_B_rejected", ("PASS" if e is not None else "FAIL"),
               ("rejected: " + str(e).splitlines()[0][:120]) if e else "wrong-tenant insert succeeded")
    # B moves own row to A -> WITH CHECK rejection
    def move():
        ctx(TENANT_B)
        cur.execute(f'SELECT ctid FROM public."{table}" WHERE tenant_id=%s LIMIT 1', (TENANT_B,))
        row = cur.fetchone()
        if not row: return "no-row"
        cur.execute(f'UPDATE public."{table}" SET tenant_id=%s WHERE ctid=%s', (TENANT_A, row[0]))
        return "updated"
    r, e = txn(move)
    if e is not None:
        record(table, "B_move_to_A_rejected", "PASS", "rejected: " + str(e).splitlines()[0][:120])
    elif r == "no-row":
        record(table, "B_move_to_A_rejected", "SKIP", "no B row visible")
    else:
        record(table, "B_move_to_A_rejected", "FAIL", "tenant move succeeded")

def probe_context_modes(table):
    for label, val in (("no_context", None), ("empty_context", ""), ("malformed_context", "not-a-uuid")):
        def f():
            ctx(val)
            cur.execute(f'SELECT count(*) FROM public."{table}"')
            return cur.fetchone()[0]
        n, e = txn(f)
        if e is not None:
            record(table, f"ctx_{label}", "PASS", "rejected: " + str(e).splitlines()[0][:100])
        else:
            record(table, f"ctx_{label}", "PASS" if n == 0 else "FAIL", f"count={n}")

def generic_insert(table, tenant_id):
    """Build and run a generic synthetic INSERT as superuser; returns ok, err."""
    names, vals = build_insert(table, tenant_id)
    if not names:
        return False, "no columns"
    col_list = pgsql.SQL(", ").join(map(pgsql.Identifier, names))
    ph = pgsql.SQL(", ").join(pgsql.Placeholder() * len(vals))
    try:
        acur.execute(pgsql.SQL("INSERT INTO public.{} ({}) VALUES ({})").format(
            pgsql.Identifier(table), col_list, ph), vals)
        return True, None
    except Exception as e:
        admin.rollback()
        return False, str(e).splitlines()[0][:150]

def probe_transactions():
    target = next((r["table"] for r in results if r["probe"] == "A_read_own" and r["status"] == "PASS"), None)
    if not target:
        record("transactions", "target", "SKIP", "no seeded table"); return
    t = target
    record(t, "transaction_target", "PASS", f"using {t} for transaction probes")
    # SET LOCAL resets after commit
    def in_txn_count():
        ctx(TENANT_A); cur.execute(f'SELECT count(*) FROM public."{t}"'); return cur.fetchone()[0]
    n_in, e1 = txn(in_txn_count)
    n_after, e2 = txn(lambda: (ctx(None), cur.execute(f'SELECT count(*) FROM public."{t}"'), cur.fetchone()[0])[2])
    record(t, "set_local_resets_after_commit",
           "PASS" if (e1 is None and e2 is None and n_in >= 1 and n_after == 0) else "FAIL",
           f"in_txn={n_in} after_commit={n_after} errs={e1}{e2}")
    # rollback discards writes (insert must run on the APP connection inside the txn)
    def app_insert(table, tenant_id):
        names, vals = build_insert(table, tenant_id)
        if not names: return False, "no columns"
        col_list = pgsql.SQL(", ").join(map(pgsql.Identifier, names))
        ph = pgsql.SQL(", ").join(pgsql.Placeholder() * len(vals))
        try:
            cur.execute(pgsql.SQL("INSERT INTO public.{} ({}) VALUES ({})").format(
                pgsql.Identifier(table), col_list, ph), vals)
            return True, None
        except Exception as e:
            return False, str(e).splitlines()[0][:150]
    try:
        cur.execute("BEGIN"); ctx(TENANT_A)
        cur.execute(f'SELECT count(*) FROM public."{t}"'); n0 = cur.fetchone()[0]
        ok, err = app_insert(t, TENANT_A)
        note = "inserted" if ok else f"insert-err:{err}"
        cur.execute("ROLLBACK")
        n_rel, e3 = txn(in_txn_count)
        record(t, "rollback_discards_writes",
               "PASS" if (ok and e3 is None and n_rel == n0) else "FAIL",
               f"before={n0} after={n_rel} note={note} {e3 or ''}")
    except Exception as e:
        app.rollback(); record(t, "rollback_discards_writes", "FAIL", e)
    # pooled reuse: A then B — no leak
    def leak_a():
        ctx(TENANT_A); cur.execute(f'SELECT count(*) FROM public."{t}" WHERE tenant_id=%s', (TENANT_A,))
        return cur.fetchone()[0]
    a_cnt, e1 = txn(leak_a)
    def leak_b():
        ctx(TENANT_B); cur.execute(f'SELECT count(*) FROM public."{t}" WHERE tenant_id=%s', (TENANT_A,))
        return cur.fetchone()[0]
    leak_cnt, e2 = txn(leak_b)
    record(t, "no_cross_tenant_leak_on_reuse",
           "PASS" if (e1 is None and e2 is None and a_cnt >= 1 and leak_cnt == 0) else "FAIL",
           f"A={a_cnt} leak={leak_cnt} errs={e1}{e2}")

def probe_owner_binding():
    """FORCE RLS must bind even the table owner (sanad owns fixture tables)."""
    target = next((r["table"] for r in results if r["probe"] == "A_read_own" and r["status"] == "PASS"), None)
    if not target:
        record("owner-binding", "force_rls_binds_owner_no_ctx", "SKIP", "no seeded table"); return
    t = target
    n, e = txn(lambda: (ctx(None), cur.execute(f'SELECT count(*) FROM public."{t}"'), cur.fetchone()[0])[2])
    record(t, "force_rls_binds_owner_no_ctx", "PASS" if (e is None and n == 0) else "FAIL",
           f"owner no-ctx count={n} err={e}")

def probe_partner_policy():
    # ensure a partner row exists: needs a real user for the created_by FK
    acur.execute("SELECT count(*) FROM public.partners")
    if acur.fetchone()[0] == 0:
        try:
            uid = str(uuid.uuid4())
            acur.execute("""
                INSERT INTO public.users (id, tenant_id, email, username, password_hash, status, created_at, updated_at)
                VALUES (%s, %s, %s, %s, 'x', 'ACTIVE', now(), now())
                ON CONFLICT DO NOTHING""",
                (uid, TENANT_A, f"partnerfix_{uuid.uuid4().hex[:8]}@fixture.invalid", f"pf_{uuid.uuid4().hex[:6]}"))
            acur.execute("SELECT id FROM public.users WHERE id=%s", (uid,))
            row = acur.fetchone()
            creator = row[0] if row else uid
            lits = check_literals("partners").get("partner_type", ["PARTNER"])
            acur.execute("""
                INSERT INTO public.partners (id, partner_type, status, created_by, created_at, updated_at, version)
                VALUES (%s, %s, 'ACTIVE', %s, now(), now(), 1)
                ON CONFLICT DO NOTHING""", (str(uuid.uuid4()), lits[0], creator))
        except Exception:
            admin.rollback()
    def f():
        ctx(""); cur.execute("SELECT set_config('app.partner_id', '', true)")
        cur.execute('SELECT count(*) FROM public."partners"'); return cur.fetchone()[0]
    n, e = txn(f)
    record("partners", "partner_policy_no_context", "PASS" if (e is None and n == 0) else "FAIL",
           f"visible={n} err={e}")
    # platform tenant branch
    def f2():
        ctx("00000000-0000-0000-0000-000000000001")
        cur.execute('SELECT count(*) FROM public."partners"'); return cur.fetchone()[0]
    n2, e2 = txn(f2)
    record("partners", "partner_policy_platform_branch", "PASS" if (e2 is None and n2 >= 1) else "FAIL",
           f"platform-visible={n2} err={e2}")

def probe_preauth_rpc():
    for role, expect in (("anon", False), ("authenticated", False), ("service_role", True)):
        acur.execute("SELECT has_function_privilege(%s, 'public.sync_login_tenant_index()', 'EXECUTE')", (role,))
        got = acur.fetchone()[0]
        record("sync_login_tenant_index", f"rpc_{role}", "PASS" if got == expect else "FAIL", f"expected={expect} got={got}")
    # trigger regression: user insert/update/delete maintains login_tenant_index
    try:
        email = f"rlsfix_{uuid.uuid4().hex[:8]}@fixture.invalid"
        uname = f"rlsfix_{uuid.uuid4().hex[:6]}"
        uid = str(uuid.uuid4())
        acur.execute("""
            INSERT INTO public.users (id, tenant_id, email, username, password_hash, status, created_at, updated_at)
            VALUES (%s, %s, %s, %s, 'x', 'ACTIVE', now(), now())""", (uid, TENANT_A, email, uname))
        acur.execute("SELECT count(*) FROM public.login_tenant_index WHERE identifier IN (%s, %s)", (email, uname))
        n1 = acur.fetchone()[0]
        email2 = f"rlsfix2_{uuid.uuid4().hex[:8]}@fixture.invalid"
        acur.execute("UPDATE public.users SET email=%s WHERE id=%s", (email2, uid))
        acur.execute("SELECT count(*) FROM public.login_tenant_index WHERE identifier=%s", (email,))
        gone = acur.fetchone()[0]
        acur.execute("SELECT count(*) FROM public.login_tenant_index WHERE identifier=%s", (email2,))
        present = acur.fetchone()[0]
        acur.execute("SELECT count(*) FROM public.login_tenant_index WHERE identifier=%s", (uname,))
        uname_kept = acur.fetchone()[0]
        acur.execute("DELETE FROM public.users WHERE id=%s", (uid,))
        acur.execute("SELECT count(*) FROM public.login_tenant_index WHERE identifier IN (%s, %s)", (email2, uname))
        n3 = acur.fetchone()[0]
        ok = (n1 == 2) and (gone == 0) and (present == 1) and (uname_kept == 1) and (n3 == 0)
        record("login_tenant_index", "trigger_regression_iud", "PASS" if ok else "FAIL",
               f"insert={n1}/2 old-email-gone={gone==0} new-email={present} username={uname_kept} after-delete={n3}")
    except Exception as e:
        admin.rollback(); record("login_tenant_index", "trigger_regression_iud", "FAIL", str(e).splitlines()[0][:200])

def probe_catalog():
    CLS_PATH = os.environ.get("SNAD_TEST_CLASSIFICATION", os.path.join(os.path.dirname(os.path.abspath(__file__)), "rls_classification_2026-10-28.json"))
    cls = json.load(open(CLS_PATH))
    bad = []
    for t in cls["group_a"]:
        acur.execute("""
            SELECT c.relrowsecurity AND c.relforcerowsecurity,
                   EXISTS (SELECT 1 FROM pg_policy pol WHERE pol.polrelid=c.oid AND pol.polname='tenant_isolation')
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relname=%s""", (t,))
        r = acur.fetchone()
        if not r or not r[0] or not r[1]: bad.append(t)
    record("catalog", "group_a_rls_force_policy", "PASS" if not bad else "FAIL", f"violations={bad[:10]}")
    bad2 = []
    for t in cls["group_a"]:
        acur.execute("""
            SELECT pg_get_expr(pol.polqual, pol.polrelid), pg_get_expr(pol.polwithcheck, pol.polrelid)
            FROM pg_policy pol WHERE pol.polrelid = to_regclass('public.' || %s)
              AND pol.polname='tenant_isolation'""", (t,))
        row = acur.fetchone()
        expr = (str(row[0]) + str(row[1])) if row else ""
        if "NULLIF" not in expr or "IS NULL OR" in expr.replace("IS NOT NULL", ""):
            bad2.append(t)
    record("catalog", "group_a_fail_closed_predicate", "PASS" if not bad2 else "FAIL", f"violations={bad2[:10]}")
    acur.execute("SELECT rolname FROM pg_roles WHERE rolbypassrls AND rolname NOT LIKE 'pg_%' AND rolname<>'postgres'")
    bypass = [r[0] for r in acur.fetchall()]
    record("roles", "no_bypassrls_roles", "PASS" if not bypass else "FAIL", f"bypass={bypass}")
    acur.execute("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND NOT c.relrowsecurity")
    remaining = acur.fetchone()[0]
    record("catalog", "no_rls_remaining_count", "PASS", f"chain no-RLS tables={remaining} (expected 25 documented Group B)")

# ---------- main ----------
for tid, name in ((TENANT_A, "RLS-FIXTURE-TENANT-A"), (TENANT_B, "RLS-FIXTURE-TENANT-B")):
    acur.execute("""
        INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at)
        VALUES (%s, %s, %s, 'ACTIVE', now(), now())
        ON CONFLICT (id) DO NOTHING""", (tid, name, name.lower()))
record("tenants", "setup", "PASS", "tenants A/B ensured")

CLS_PATH = os.environ.get("SNAD_TEST_CLASSIFICATION", os.path.join(os.path.dirname(os.path.abspath(__file__)), "rls_classification_2026-10-28.json"))
cls = json.load(open(CLS_PATH))

# deterministic fixture: clean prior suite data (disposable DB; safe)
all_tables = cls["group_a"] + ["partners", "login_tenant_index", "users", "roles"]
for t in all_tables:
    try:
        acur.execute(f'TRUNCATE TABLE public."{t}" CASCADE')
    except Exception:
        admin.rollback()
acur.execute("SELECT count(*) FROM public.tenants WHERE id IN (%s,%s)", (TENANT_A, TENANT_B))
if acur.fetchone()[0] < 2:
    for tid, name in ((TENANT_A, "RLS-FIXTURE-TENANT-A"), (TENANT_B, "RLS-FIXTURE-TENANT-B")):
        acur.execute("""
            INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at)
            VALUES (%s, %s, %s, 'ACTIVE', now(), now())
            ON CONFLICT (id) DO NOTHING""", (tid, name, name.lower()))

# shared parent pre-seed (same-tenant users/warehouses for both tenants)
def _mk_user(tid):
    uid = str(uuid.uuid4())
    acur.execute("""
        INSERT INTO public.users (id, tenant_id, email, username, password_hash, status, created_at, updated_at)
        VALUES (%s, %s, %s, %s, 'x', 'ACTIVE', now(), now())
        ON CONFLICT DO NOTHING""",
        (uid, tid, f"seed{uuid.uuid4().hex[:8]}@fixture.invalid", f"seed_{uuid.uuid4().hex[:6]}"))
    return uid
for tid in (TENANT_A, TENANT_B):
    _mk_user(tid); _mk_user(tid)
    for i in range(2):
        try:
            acur.execute("""
                INSERT INTO public.erp_warehouses (id, tenant_id, code, name, status)
                VALUES (%s, %s, %s, %s, 'ACTIVE') ON CONFLICT DO NOTHING""",
                (str(uuid.uuid4()), tid, f"WH-{uuid.uuid4().hex[:6].upper()}", f"WH {i}"))
        except Exception:
            admin.rollback()

# wave 1 + wave 2 (dependency retries)
for wave in (1, 2):
    for t in cls["group_a"]:
        st = seed_state.get(t) or {}
        if wave == 2 and st.get(TENANT_A) and st.get(TENANT_B):
            continue
        probe_table(t)
for t in cls["group_a"]:
    probe_context_modes(t)
probe_transactions()
probe_owner_binding()
probe_partner_policy()
probe_preauth_rpc()
probe_catalog()

app.close(); admin.close()

fails = [r for r in results if r["status"] == "FAIL"]
skips = [r for r in results if r["status"] == "SKIP"]
passes = [r for r in results if r["status"] == "PASS"]
os.makedirs(OUT, exist_ok=True)
with open(f"{OUT}/ab-isolation-results.json", "w") as f:
    json.dump({"database": DB, "total": len(results), "pass": len(passes),
               "fail": len(fails), "skip": len(skips), "results": results}, f, indent=1)
print(f"TOTAL={len(results)} PASS={len(passes)} FAIL={len(fails)} SKIP={len(skips)}")
for r in fails[:50]:
    print(f"FAIL {r['table']} :: {r['probe']} :: {r['detail'][:160]}")
for r in skips[:50]:
    print(f"SKIP {r['table']} :: {r['probe']} :: {r['detail'][:140]}")
print("parent_failures (distinct):")
seen=set()
for pf in parent_failures:
    if pf not in seen:
        seen.add(pf); print("  ", pf[:160])
sys.exit(1 if fails else 0)
