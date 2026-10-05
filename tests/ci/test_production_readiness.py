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


def response(
    status: int,
    value: object,
    headers: dict[str, str] | None = None,
) -> tuple[int, bytes, dict[str, str]]:
    if isinstance(value, bytes):
        body = value
    else:
        body = json.dumps(value).encode("utf-8")
    return status, body, headers or {}


ROUTE_OK = b"<html><body>SNAD HRM route shell</body></html>"


def test_readiness_requires_redacted_target_host(monkeypatch):
    responses = iter(
        [
            response(200, '<html lang="ar" dir="rtl">SNAD سند</html>'.encode()),
            response(200, ROUTE_OK),
            response(200, ROUTE_OK),
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
        "hrm-g3-goals-route",
        "hrm-g3-reviews-route",
        "frontend-backend-integration",
        "bff-authentication-chain",
        "backend-host-policy",
        "backend-actuator-health",
    ]
    assert all(check.passed for check in checks)


def test_readiness_fails_closed_on_g3_goals_http_404(monkeypatch):
    responses = iter(
        [
            response(200, '<html lang="ar" dir="rtl">SNAD سند</html>'.encode()),
            response(404, b"<html><body><h1>404</h1><p>Page not found</p></body></html>"),
            response(200, ROUTE_OK),
        ]
    )
    monkeypatch.setattr(probe, "request", lambda _url, _timeout: next(responses))

    checks = probe.run_once("https://production.example.test", 1.0)

    assert [check.name for check in checks] == [
        "production-ui",
        "hrm-g3-goals-route",
        "hrm-g3-reviews-route",
    ]
    goals_check = checks[1]
    assert goals_check.url == "https://production.example.test/hr/performance/goals"
    assert goals_check.status_code == 404
    assert goals_check.passed is False
    assert checks[2].passed is True


def test_readiness_fails_closed_on_g3_reviews_soft_404(monkeypatch):
    responses = iter(
        [
            response(200, '<html lang="ar" dir="rtl">SNAD سند</html>'.encode()),
            response(200, ROUTE_OK),
            response(
                200,
                b"<html><body><h1>404</h1><p>The page you are looking for does not exist or has been moved.</p></body></html>",
            ),
        ]
    )
    monkeypatch.setattr(probe, "request", lambda _url, _timeout: next(responses))

    checks = probe.run_once("https://production.example.test", 1.0)

    assert [check.name for check in checks] == [
        "production-ui",
        "hrm-g3-goals-route",
        "hrm-g3-reviews-route",
    ]
    assert checks[-1].url == "https://production.example.test/hr/performance/reviews"
    assert checks[-1].status_code == 200
    assert checks[-1].passed is False
    assert "does not exist or has been moved" in checks[-1].actual




def test_readiness_accepts_next_flight_not_found_strings_when_route_identity_matches(monkeypatch):
    embedded_not_found = (
        b"<html><body>SNAD HRM route shell"
        b"<script>Page not found; does not exist or has been moved</script>"
        b"</body></html>"
    )
    responses = iter(
        [
            response(
                200,
                embedded_not_found,
                {"x-matched-path": "/hr/performance/goals"},
            ),
            response(
                200,
                embedded_not_found,
                {"x-matched-path": "/hr/performance/reviews"},
            ),
        ]
    )
    monkeypatch.setattr(probe, "request", lambda _url, _timeout: next(responses))

    checks = probe.check_critical_frontend_routes(
        "https://production.example.test", 1.0
    )

    assert all(check.passed for check in checks)
    assert "xMatchedPath=/hr/performance/goals" in checks[0].actual
    assert "soft404Markers=['page not found', 'does not exist or has been moved']" in checks[0].actual


def test_readiness_fails_closed_when_vercel_matches_not_found_route(monkeypatch):
    responses = iter(
        [
            response(
                200,
                ROUTE_OK,
                {"x-matched-path": "/404"},
            ),
            response(
                200,
                ROUTE_OK,
                {"x-matched-path": "/hr/performance/reviews"},
            ),
        ]
    )
    monkeypatch.setattr(probe, "request", lambda _url, _timeout: next(responses))

    checks = probe.check_critical_frontend_routes(
        "https://production.example.test", 1.0
    )

    assert checks[0].passed is False
    assert "xMatchedPath=/404" in checks[0].actual
    assert checks[1].passed is True


def test_readiness_rejects_exposed_target_host(monkeypatch):
    responses = iter(
        [
            response(200, '<html lang="ar" dir="rtl">SNAD سند</html>'.encode()),
            response(200, ROUTE_OK),
            response(200, ROUTE_OK),
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


def test_request_adds_vercel_trusted_oidc_header(monkeypatch):
    captured = {}

    class FakeResponse:
        status = 200
        headers = {}

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def read(self):
            return b"ok"

    def fake_urlopen(req, timeout):
        captured["header"] = req.get_header("X-vercel-trusted-oidc-idp-token")
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setenv("VERCEL_TRUSTED_OIDC_TOKEN", "ephemeral-github-oidc")
    monkeypatch.setattr(probe.urllib.request, "urlopen", fake_urlopen)

    status, body, _headers = probe.request("https://protected.example.test", 3.0)

    assert status == 200
    assert body == b"ok"
    assert captured["header"] == "ephemeral-github-oidc"
    assert captured["timeout"] == 3.0
