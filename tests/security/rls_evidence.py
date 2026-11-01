"""Fail-closed classification for PostgreSQL security evidence.

These helpers classify observations; they never turn environment failures or
incomplete table coverage into security acceptance.
"""
from collections import Counter


def probe_trigger_execute(connect, role):
    """Prove denied EXECUTE as the intended role, not a trigger call error.

A trigger function cannot normally be invoked with SELECT even when EXECUTE
is granted. Therefore the ACL and role identity must also be verified.
"""
    if role not in ('anon', 'authenticated'):
        raise ValueError('Unsupported probe role')
    connection = None
    phase = 'setup'
    try:
        connection = connect()
        connection.execute('SET ROLE ' + role)
        if connection.execute('SELECT current_user').fetchone() != (role,):
            return 'BLOCKED', 'Probe role identity mismatch'
        function = connection.execute(
            "SELECT to_regprocedure('public.sync_login_tenant_index()')").fetchone()
        if not function or function[0] is None:
            return 'BLOCKED', 'Required trigger function missing'
        privilege = connection.execute(
            "SELECT has_function_privilege(current_user, "
            "'public.sync_login_tenant_index()', 'EXECUTE')").fetchone()
        if privilege != (False,):
            return 'FAIL', 'EXECUTE is granted or could not be proven revoked'
        phase = 'execute'
        connection.execute('SELECT public.sync_login_tenant_index()')
        return 'FAIL', 'Function invocation succeeded'
    except Exception as error:
        state = getattr(error, 'sqlstate', None)
        if phase == 'setup':
            return 'BLOCKED', f'Probe setup failed (SQLSTATE={state})'
        if state == '42501':
            return 'PASS', 'Role verified, EXECUTE revoked, invocation denied (42501)'
        return 'ERROR', f'Unexpected invocation error (SQLSTATE={state})'
    finally:
        if connection is not None:
            connection.close()


def summarize_evidence(tests, per_table):
    """Require nonempty, complete PASS coverage; retain every failed attempt."""
    counts = Counter(row.get('status', 'UNKNOWN') for row in per_table)
    failures = [t for t in tests if t.get('status') in ('FAIL', 'ERROR')]
    table_failures = [r for r in per_table if r.get('status') in ('FAIL', 'ERROR')]
    incomplete = [r for r in per_table if r.get('status') != 'PASS']
    blocked = [t for t in tests if t.get('status') not in ('PASS', 'INFO', 'FAIL', 'ERROR')]
    duplicate = len({r.get('table') for r in per_table}) != len(per_table)
    if failures or table_failures:
        overall = 'FAIL'
    elif not tests or not per_table or incomplete or blocked or duplicate:
        overall = 'BLOCKED'
    else:
        overall = 'PASS'
    return {
        'per_table_status': dict(counts),
        'per_table_total': len(per_table),
        'per_table_failed': len(table_failures),
        'per_table_incomplete': len(incomplete),
        'catalog_tests_failed': len(failures),
        'catalog_failures': [t.get('id') for t in failures],
        'catalog_tests_blocked': len(blocked),
        'duplicate_table_results': duplicate,
        'overall': overall,
    }


def write_denied(result):
    """Only zero affected rows or an explicit privilege denial qualify."""
    return (result == ('OK', 0) or
            (len(result) > 2 and result[0] == 'ERROR' and result[2] == '42501'))


def context_denied(result, *, malformed=False):
    """Invalid UUID is expected only for an explicitly malformed context."""
    if result == (0,):
        return True
    states = ('42501', '22P02') if malformed else ('42501',)
    return bool(isinstance(result, tuple) and len(result) > 2 and
                result[0] == 'ERROR' and result[2] in states)
