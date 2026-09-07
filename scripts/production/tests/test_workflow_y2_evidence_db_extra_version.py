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


class WorkflowY2DbExtraVersionGateTest(unittest.TestCase):
    def test_successful_db_version_missing_from_repository_fails_gate(self):
        engine = load_engine()
        snapshot = {
            "mainSha": "deadbeef",
            "renderEnv": [
                {"key": key, "present": True}
                for key in engine.EXPECTED_ENV_KEYS
            ],
            "repositoryVersions": ["1", *engine.REQUIRED_Y2_VERSIONS],
            "dbHistory": [
                {
                    "installedRank": rank,
                    "version": version,
                    "type": "SQL",
                    "success": True,
                    "checksum": rank,
                }
                for rank, version in enumerate(
                    ["1", *engine.REQUIRED_Y2_VERSIONS], start=1
                )
            ] + [
                {
                    "installedRank": 999,
                    "version": "20260905.1",
                    "type": "SQL",
                    "success": True,
                    "checksum": 999,
                }
            ],
            "schema": {
                "tables": {
                    table: {
                        "exists": True,
                        "tenantId": True,
                        "rls": True,
                        "forceRls": False,
                        "tenantPolicy": True,
                    }
                    for table in engine.Y2_TABLES
                },
                "columns": list(engine.REQUIRED_COLUMNS),
            },
            "capabilities": [
                {"code": code, "status": "ACTIVE"}
                for code in engine.Y2_CAPABILITIES
            ],
            "adminBindings": {
                "activeTenants": 1,
                "activeTenantsWithAdmin": 1,
                "activeTenantsWithCompleteY2AdminBinding": 1,
                "incompleteBindings": 0,
            },
        }

        result = engine.evaluate_snapshot(snapshot)

        self.assertEqual(["20260905.1"], result["database"]["dbSuccessNotInRepo"])
        self.assertEqual("FAIL", result["database"]["status"])
        self.assertEqual("FAIL", result["result"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
