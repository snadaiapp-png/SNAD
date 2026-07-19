from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
if not (REPO_ROOT / "scripts").is_dir():
    REPO_ROOT = Path(__file__).resolve().parent
MODULE_PATH = REPO_ROOT / "scripts" / "ci" / "check-production-readiness.py"
SPEC = importlib.util.spec_from_file_location("check_production_readiness", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
probe = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = probe
SPEC.loader.exec_module(probe)


def response(status: int, value: object) -> tuple[int, bytes, dict[str, str]]:
    if isinstance(value, bytes):
        body = value
    else:
        body = json.dumps(value).encode("utf-8")
    return status, body, {}


def test_readiness_requires_redacted_target_host(monkeypatch):
    responses = iter(
        [
            response(200, '<html lang="ar" dir="rtl">SNAD سند</html>'.encode()),
            response(200, {"configured": True, "reachable": True, "statusCode": 200}),
            response(401, {"error": "unauthorized"}),
            response(200, {"status": "UP"}),
        ]
    )
    monkeypatch.setenv("SNAD_BACKEND_EXPECTED_HOST", "backend.example.test")
    monkeypatch.delenv("SNAD_BACKEND_HEALTH_URL", raising=False)
    monkeypatch.setattr(probe, "request", lambda _url, _timeout: next(responses))

    checks = probe.run_once("https://production.example.test", 1.0)

    assert [check.name for check in checks] == [
        "production-ui",
        "frontend-backend-integration",
        "bff-authentication-chain",
        "backend-host-policy",
        "backend-actuator-health",
    ]
    assert all(check.passed for check in checks)


def test_readiness_rejects_exposed_target_host(monkeypatch):
    responses = iter(
        [
            response(200, '<html lang="ar" dir="rtl">SNAD سند</html>'.encode()),
            response(
                200,
                {
                    "configured": True,
                    "reachable": True,
                    "statusCode": 200,
                    "targetHost": "internal.example.test",
                },
            ),
        ]
    )
    monkeypatch.setattr(probe, "request", lambda _url, _timeout: next(responses))

    checks = probe.run_once("https://production.example.test", 1.0)

    assert checks[-1].name == "frontend-backend-integration"
    assert checks[-1].passed is False
    assert "targetHostExposed=True" in checks[-1].actual
