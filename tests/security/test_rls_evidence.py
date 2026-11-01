"""Tests of evidence classification, not substitutes for PostgreSQL isolation tests."""
import ast
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent


def legacy_denied():
    tree = ast.parse((ROOT / 'rls_postgres_direct_suite.py').read_text())
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'denied')
    ns = {}
    exec(compile(ast.Module(body=[node], type_ignores=[]), '<denied>', 'exec'), ns)
    return ns['denied']


class DenialEvidenceTest(unittest.TestCase):
    def test_unrelated_database_errors_are_not_isolation_proof(self):
        denied = legacy_denied()
        for state in ('42P01', '42883', '25P02', '08006', None):
            with self.subTest(sqlstate=state):
                self.assertFalse(denied(('ERROR', 'probe failed', state)))

    def test_rows_are_visible_and_empty_result_is_denied(self):
        denied = legacy_denied()
        self.assertFalse(denied((1,)))
        self.assertTrue(denied((0,)))
        self.assertTrue(denied(None))


class EvidencePolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = ROOT / 'rls_evidence.py'
        cls.available = path.exists()
        if cls.available:
            spec = importlib.util.spec_from_file_location('rls_evidence', path)
            cls.policy = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(cls.policy)

    def policy_module(self):
        self.assertTrue(self.available, 'Shared fail-closed evidence policy is missing')
        return self.policy

    def test_incomplete_empty_and_failed_coverage_cannot_pass(self):
        p = self.policy_module()
        cases = [([], 'BLOCKED'), ([{'table':'a','status':'SYNTH-UNABLE'}], 'BLOCKED'),
                 ([{'table':'a','status':'DOMAIN-GUARD-LIMITED'}], 'BLOCKED'),
                 ([{'table':'a','status':'FAIL'}], 'FAIL'),
                 ([{'table':'a','status':'PASS'}], 'PASS')]
        for rows, expected in cases:
            with self.subTest(rows=rows):
                self.assertEqual(p.summarize_evidence([{'status':'PASS'}], rows)['overall'], expected)
        self.assertEqual(p.summarize_evidence([], [{'table':'a','status':'PASS'}])['overall'], 'BLOCKED')
        self.assertEqual(p.summarize_evidence([{'status':'ERROR'}], [{'table':'a','status':'PASS'}])['overall'], 'FAIL')
        self.assertEqual(p.summarize_evidence([{'status':'BLOCKED'}], [{'table':'a','status':'PASS'}])['overall'], 'BLOCKED')

    def test_write_and_context_denials_reject_unrelated_errors(self):
        p = self.policy_module()
        for state in ('42P01', '25P02', '08006', None):
            with self.subTest(state=state):
                self.assertFalse(p.write_denied(('ERROR', 'error', state)))
                self.assertFalse(p.context_denied(('ERROR', 'error', state)))
        self.assertTrue(p.write_denied(('OK', 0)))
        self.assertFalse(p.write_denied(('OK', 1)))
        self.assertTrue(p.write_denied(('ERROR', 'denied', '42501')))
        self.assertFalse(p.context_denied(('ERROR', 'invalid uuid', '22P02')))
        self.assertTrue(p.context_denied(('ERROR', 'invalid uuid', '22P02'), malformed=True))
        self.assertTrue(p.context_denied((0,)))
        self.assertFalse(p.context_denied((1,)))

    def test_duplicate_table_failure_is_not_hidden_by_later_success(self):
        p = self.policy_module()
        rows = [{'table':'a','status':'FAIL'}, {'table':'a','status':'PASS'}]
        self.assertEqual(p.summarize_evidence([{'status':'PASS'}], rows)['overall'], 'FAIL')

    def test_rpc_requires_identity_acl_and_specific_permission_denial(self):
        p = self.policy_module()
        class DbError(Exception):
            def __init__(self, state):
                super().__init__('synthetic database error')
                self.sqlstate = state
        class Connection:
            def __init__(self, stage=None, state=None, allowed=False, identity='anon', exists=True):
                self.stage, self.state, self.allowed, self.identity, self.exists = stage, state, allowed, identity, exists
                self.closed = False
            def execute(self, sql, params=None):
                if sql.startswith('SET ROLE'):
                    if self.stage == 'role': raise DbError(self.state)
                    self.result = None
                elif sql == 'SELECT current_user': self.result = (self.identity,)
                elif 'to_regprocedure' in sql: self.result = ('public.sync_login_tenant_index()' if self.exists else None,)
                elif 'has_function_privilege' in sql: self.result = (self.allowed,)
                elif sql == 'SELECT public.sync_login_tenant_index()':
                    if self.stage == 'call': raise DbError(self.state)
                    self.result = (None,)
                else: raise AssertionError('Unexpected query: '+sql)
                return self
            def fetchone(self): return self.result
            def close(self): self.closed = True
        cases = [
            (dict(stage='role', state='42501'), 'BLOCKED'),
            (dict(stage='role', state='42704'), 'BLOCKED'),
            (dict(identity='sanad'), 'BLOCKED'),
            (dict(exists=False), 'BLOCKED'),
            (dict(allowed=True, stage='call', state='0A000'), 'FAIL'),
            (dict(stage='call', state='0A000'), 'ERROR'),
            (dict(stage='call', state='42883'), 'ERROR'),
            (dict(stage='call', state='42501'), 'PASS'),
            (dict(), 'FAIL'),
        ]
        for kwargs, expected in cases:
            with self.subTest(kwargs=kwargs):
                c = Connection(**kwargs)
                status, _ = p.probe_trigger_execute(lambda: c, 'anon')
                self.assertEqual(status, expected)
                self.assertTrue(c.closed)
        def unavailable(): raise DbError('08006')
        self.assertEqual(p.probe_trigger_execute(unavailable, 'anon')[0], 'BLOCKED')


if __name__ == '__main__':
    unittest.main()
