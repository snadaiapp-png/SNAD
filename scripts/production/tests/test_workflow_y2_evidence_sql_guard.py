#!/usr/bin/env python3
import importlib.util
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
ENGINE_PATH = REPO_ROOT / "scripts/production/workflow_y2_evidence.py"


def load_engine():
    spec = importlib.util.spec_from_file_location("workflow_y2_evidence", ENGINE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class WorkflowY2EvidenceSqlGuardTest(unittest.TestCase):
    def test_select_followed_by_mutating_statement_is_rejected(self):
        engine = load_engine()
        with self.assertRaises(ValueError):
            engine.assert_read_only_sql(
                "SELECT 1; UPDATE workflow_work_items SET status='COMPLETED';"
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
