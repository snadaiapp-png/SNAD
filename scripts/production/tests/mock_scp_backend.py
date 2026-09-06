#!/usr/bin/env python3
"""Mock SCP backend for the production smoke-script contract tests.

Serves every route that scripts/production/verify-scp-contract-smoke.sh
touches, with an access-check/v2 response controlled by the ACCESS_MODE
environment variable:

  unauthorized — authenticated=false, capabilities={}  (the smoke blind spot)
  missing_cap  — authenticated=true but one required read capability false
  authorized   — authenticated=true with all ten mandatory read capabilities

Read-only: binds 127.0.0.1 on an ephemeral port, serves nothing else.
"""

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ACCESS_MODE = os.environ.get("ACCESS_MODE", "authorized")

READ_CAPS = [
    "subscription.read", "catalog.read", "application.read", "plan.read",
    "pricing.read", "entitlement.read", "usage.read", "billing.read",
    "provisioning.read", "audit.read",
]


def access_check_payload():
    if ACCESS_MODE == "unauthorized":
        return {"authenticated": False, "capabilities": {}}
    if ACCESS_MODE == "missing_cap":
        caps = {code: True for code in READ_CAPS}
        caps["audit.read"] = False
        return {"authenticated": True, "capabilities": caps}
    return {"authenticated": True, "capabilities": {code: True for code in READ_CAPS}}


def page(content):
    return {
        "content": content,
        "page": 0,
        "size": 1,
        "totalElements": len(content),
        "totalPages": 1,
    }


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):  # silence request logging
        pass

    def _json(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path.startswith("/api/v1/auth/login"):
            self._json({"accessToken": "mock-smoke-token", "refreshToken": None})
            return
        self._json({"error": "not found"}, 404)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/v1/executive/access-check/v2":
            self._json(access_check_payload())
        elif path == "/api/v1/executive/overview":
            self._json({"totalTenants": 3, "generatedAt": "2026-09-07T00:00:00Z"})
        elif path == "/api/v1/executive/applications":
            self._json([{"id": "a1", "name": "App"}])
        elif path == "/api/v1/executive/tenants/v2":
            self._json(page([{"id": "t1"}]))
        elif path == "/api/v1/executive/subscriptions/v2":
            self._json(page([{"id": "s1"}]))
        elif path == "/api/v1/executive/provisioning/jobs":
            self._json([{"id": "j1"}])
        elif path == "/api/v1/executive/audit/v2":
            self._json(page([{"id": "e1"}]))
        else:
            self._json({"error": "not found"}, 404)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler)
    server.serve_forever()
