#!/usr/bin/env python3
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

EXPECTED_SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
DEF_ID = "11111111-1111-1111-1111-111111111111"
EMAIL = "admin@example.test"

class Handler(BaseHTTPRequestHandler):
    release_sha = EXPECTED_SHA
    catalog_mode = "enabled"
    backend_status_failures = 0
    backend_status_requests = 0

    def log_message(self, *_):
        pass

    def _read_json(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(n) if n else b"{}"
        try:
            return json.loads(raw or b"{}")
        except Exception:
            return {}

    def _send(self, code, body, content_type="application/json"):
        if content_type == "application/json":
            data = json.dumps(body).encode()
        else:
            data = str(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/system/release":
            return self._send(200, {
                "service": "SNAD Web",
                "contractVersion": "3",
                "commitSha": self.release_sha,
                "commitRef": "main",
                "environment": "production",
            })
        if path == "/api/system/backend-status":
            Handler.backend_status_requests += 1
            if Handler.backend_status_requests <= Handler.backend_status_failures:
                return self._send(200, {
                    "configured": True,
                    "reachable": False,
                    "statusCode": None,
                    "checkedAt": "2026-09-13T00:00:00Z",
                })
            return self._send(200, {
                "configured": True,
                "reachable": True,
                "statusCode": 200,
                "checkedAt": "2026-09-13T00:00:00Z",
            })
        if path == "/workflow":
            return self._send(200, "<html><body>workflow</body></html>", "text/html")
        if path == "/api/platform/api/v1/auth/me":
            return self._send(200, {
                "id": "99999999-9999-9999-9999-999999999999",
                "tenantId": "77777777-7777-7777-7777-777777777777",
                "email": EMAIL,
                "displayName": "Admin",
                "status": "ACTIVE",
                "lastLoginAt": None,
                "credentialRotationRequired": False,
                "memberships": [],
                "roleGrants": [],
                "capabilities": ["WORKFLOW.VIEW", "WORKFLOW.VALIDATE"],
            })
        if path == "/api/platform/api/v1/workflows/definitions":
            return self._send(200, [{
                "id": DEF_ID,
                "code": "Y2-PROD-CANARY-existing",
                "publicationState": "PUBLISHED",
                "engineGeneration": "Y2",
            }])
        if path == "/api/platform/api/v1/workflows/catalog/modules":
            if self.catalog_mode == "not-entitled":
                return self._send(403, {
                    "status": 403,
                    "error": "Forbidden",
                    "message": "WORKFLOW_MODULE_NOT_ENTITLED",
                })
            if self.catalog_mode == "wrong-forbidden":
                return self._send(403, {
                    "status": 403,
                    "error": "Forbidden",
                    "message": "OTHER_DENIAL",
                })
            return self._send(200, {"modules": []})
        if path == "/api/platform/api/v1/workflows/instances":
            return self._send(200, [])
        if path == "/api/platform/api/v1/workflows/monitoring/health":
            return self._send(200, {"status": "UP"})
        if path == "/api/platform/api/v1/workflows/notifications":
            return self._send(200, [])
        return self._send(404, {"status": 404, "path": path})

    def do_POST(self):
        path = urlparse(self.path).path
        body = self._read_json()
        if path == "/api/platform/api/v1/auth/login":
            if body.get("email") != EMAIL or not body.get("password") or not body.get("tenantId"):
                return self._send(401, {"status": 401})
            return self._send(200, {
                "accessToken": "test-token-abcdefghijklmnopqrstuvwxyz",
                "expiresAt": "2026-09-14T00:00:00Z",
                "user": {"email": EMAIL},
            })
        if path == f"/api/platform/api/v1/workflows/definitions/{DEF_ID}/validate":
            if self.catalog_mode == "not-entitled":
                return self._send(403, {
                    "status": 403,
                    "error": "Forbidden",
                    "message": "WORKFLOW_MODULE_NOT_ENTITLED",
                })
            return self._send(200, {"valid": True, "errors": []})
        if path == f"/api/platform/api/v1/workflows/definitions/{DEF_ID}/simulate":
            if self.catalog_mode == "not-entitled":
                return self._send(403, {
                    "status": 403,
                    "error": "Forbidden",
                    "message": "WORKFLOW_MODULE_NOT_ENTITLED",
                })
            return self._send(200, {"valid": True, "simulated": True, "visitedStepIds": [], "notes": []})
        return self._send(404, {"status": 404, "path": path})

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, required=True)
    p.add_argument("--release-sha", default=EXPECTED_SHA)
    p.add_argument("--catalog-mode", choices=["enabled", "not-entitled", "wrong-forbidden"], default="enabled")
    p.add_argument("--backend-status-failures", type=int, default=0)
    a = p.parse_args()
    Handler.release_sha = a.release_sha
    Handler.catalog_mode = a.catalog_mode
    Handler.backend_status_failures = max(0, a.backend_status_failures)
    Handler.backend_status_requests = 0
    ThreadingHTTPServer(("127.0.0.1", a.port), Handler).serve_forever()
