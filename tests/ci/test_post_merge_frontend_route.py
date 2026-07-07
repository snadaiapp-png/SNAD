#!/usr/bin/env python3
"""Regression test: post-merge verification frontend route contract.

This test exists because PRs #351–#355 kept increasing the frontend smoke
timeout (60s → 180s → 300s → 600s) chasing a STARTUP_TIMEOUT that was never
actually a startup problem. The real bug was that the readiness probe polled
`/auth/login` — a route that does NOT exist in the Next.js App Router — so
every probe returned HTTP 404 regardless of how long we waited.

This test guards against that class of regression by parsing the workflow
YAML directly and asserting the contract required by the PM Final Closure
Execution Order (P0-2):
  * The frontend smoke URL is the root route (http://127.0.0.1:3001/)
  * No /auth/login string appears in the smoke-frontend step
  * The same FRONTEND_SMOKE_URL variable is used for readiness, final fetch,
    and the validator call
  * The timeout does not exceed 180 seconds (60 iterations × 3s sleep)
  * Frontend smoke metadata is included in the frontend smoke artifact upload
  * Backend smoke metadata is included in the backend smoke artifact upload
"""
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "post-merge-verification.yml"


class TestPostMergeFrontendRoute(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.assertTrue(WORKFLOW.exists(), f"workflow missing: {WORKFLOW}")
        cls.text = WORKFLOW.read_text(encoding="utf-8")

    # --- 1. No legacy /auth/login URL anywhere in the smoke-frontend step ---
    def test_no_legacy_auth_login_route_in_smoke_frontend(self):
        # Find the smoke-frontend step block
        start = self.text.find("Smoke test — Frontend")
        self.assertGreater(start, 0, "smoke-frontend step not found")
        # Step ends at the next top-level "- name:" (cheaper than full YAML parse)
        next_step = self.text.find("\n      - name:", start + 1)
        block = self.text[start:next_step] if next_step > 0 else self.text[start:]
        self.assertNotIn(
            "/auth/login",
            block,
            "Legacy /auth/login route must not appear in smoke-frontend step — "
            "it does not exist in the App Router and causes perpetual STARTUP_TIMEOUT.",
        )

    # --- 2. Frontend smoke URL is the root route ---
    def test_frontend_smoke_url_is_root(self):
        self.assertIn(
            'FRONTEND_SMOKE_URL="http://127.0.0.1:3001/"',
            self.text,
            "FRONTEND_SMOKE_URL must be defined once as the root route "
            "http://127.0.0.1:3001/",
        )

    # --- 3. Single variable used in readiness, fetch, and validator ---
    def test_same_variable_used_everywhere(self):
        # Count occurrences of $FRONTEND_SMOKE_URL (shell expansion) — there
        # must be at least three: readiness probe, final fetch, validator --url
        occurrences = self.text.count('"$FRONTEND_SMOKE_URL"')
        self.assertGreaterEqual(
            occurrences,
            3,
            f"FRONTEND_SMOKE_URL must be used in readiness, final fetch, and "
            f"validator (>=3 expansions); found {occurrences}",
        )

    # --- 4. Timeout must not exceed 180 seconds ---
    def test_timeout_does_not_exceed_180s(self):
        # Match: for i in $(seq 1 N)  → N iterations × 3s sleep = max wait
        m = re.search(r"for i in \$\(seq 1 (\d+)\)", self.text)
        self.assertIsNotNone(m, "frontend readiness loop not found")
        iterations = int(m.group(1))
        max_wait_seconds = iterations * 3
        self.assertLessEqual(
            max_wait_seconds,
            180,
            f"Frontend smoke timeout must not exceed 180s; "
            f"current: {iterations} iterations × 3s = {max_wait_seconds}s. "
            "Do NOT paper over a real bug by extending the timeout.",
        )
        # The error message must also say 180s, not 600s.
        self.assertIn(
            "within 180s (STARTUP_TIMEOUT)",
            self.text,
            "Failure message must say '180s (STARTUP_TIMEOUT)', not 600s or 300s.",
        )

    # --- 5. Process-liveness check still present ---
    def test_process_liveness_check_present(self):
        self.assertIn(
            "kill -0 \"$FRONTEND_PID\"",
            self.text,
            "Process-liveness check (kill -0 $FRONTEND_PID) must remain — "
            "PROCESS_EXITED must be caught distinctly from STARTUP_TIMEOUT.",
        )
        self.assertIn(
            "PROCESS_EXITED",
            self.text,
            "PROCESS_EXITED error label must remain in workflow.",
        )

    # --- 6. Smoke metadata included in artifact uploads ---
    def test_frontend_metadata_in_artifact(self):
        # Find the frontend smoke evidence upload block
        idx = self.text.find("Upload frontend smoke evidence")
        self.assertGreater(idx, 0, "frontend smoke evidence upload step not found")
        block_end = self.text.find("\n      - name:", idx + 1)
        block = self.text[idx:block_end] if block_end > 0 else self.text[idx:]
        self.assertIn(
            "frontend-smoke-metadata.json",
            block,
            "frontend-smoke-metadata.json MUST be in the frontend smoke evidence "
            "artifact upload — required by Final Closure Order §7.",
        )
        self.assertIn(
            "if-no-files-found: error",
            block,
            "Frontend smoke artifact must use if-no-files-found: error (not warn).",
        )

    def test_backend_metadata_in_artifact(self):
        idx = self.text.find("Upload backend smoke evidence")
        self.assertGreater(idx, 0, "backend smoke evidence upload step not found")
        block_end = self.text.find("\n      - name:", idx + 1)
        block = self.text[idx:block_end] if block_end > 0 else self.text[idx:]
        self.assertIn(
            "backend-smoke-metadata.json",
            block,
            "backend-smoke-metadata.json MUST be in the backend smoke evidence "
            "artifact upload — required by Final Closure Order §7.",
        )
        self.assertIn(
            "if-no-files-found: error",
            block,
            "Backend smoke artifact must use if-no-files-found: error (not warn).",
        )

    # --- 7. Final gate must invoke the independent evidence validator ---
    def test_final_gate_invokes_evidence_validator(self):
        idx = self.text.find("Final gate")
        self.assertGreater(idx, 0, "Final gate step not found")
        block = self.text[idx:]
        self.assertIn(
            "validate_post_merge_evidence.py",
            block,
            "Final gate must invoke scripts/ci/validate_post_merge_evidence.py — "
            "the independent evidence validator is the only authority that may "
            "flip the gate to PASS.",
        )
        self.assertIn(
            "--expected-sha",
            block,
            "Final gate must pass --expected-sha to the evidence validator.",
        )
        self.assertIn(
            "--expected-run-id",
            block,
            "Final gate must pass --expected-run-id to the evidence validator.",
        )

    # --- 8. Negative control: a workflow that uses /auth/login must FAIL this test ---
    def test_negative_control_legacy_route_rejected(self):
        # Synthesize a poisoned workflow text and re-run the route check.
        poisoned = self.text.replace(
            'FRONTEND_SMOKE_URL="http://127.0.0.1:3001/"',
            'FRONTEND_SMOKE_URL="http://127.0.0.1:3001/auth/login"',
        )
        start = poisoned.find("Smoke test — Frontend")
        next_step = poisoned.find("\n      - name:", start + 1)
        block = poisoned[start:next_step]
        self.assertIn(
            "/auth/login",
            block,
            "Negative control sanity check: poisoned workflow must contain /auth/login",
        )
        # If a future maintainer reintroduces /auth/login, the route test above
        # (test_no_legacy_auth_login_route_in_smoke_frontend) will fail.


if __name__ == "__main__":
    unittest.main()
