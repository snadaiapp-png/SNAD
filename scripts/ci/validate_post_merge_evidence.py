#!/usr/bin/env python3
"""
SNAD Post-Merge Evidence Validator — Fail-Closed Independent Gate

Reads the actual JSON evidence files produced by the post-merge verification
workflow and enforces invariants that the workflow's bash layer cannot enforce
on its own:

  * SHA & run-id embedded in the manifest match the actual workflow run
  * backend-smoke-metadata.json.result == "PASS"
  * backend-health.json.status == "UP"
  * frontend-smoke-metadata.json.result == "PASS"
  * secret-scan-report.json.result == "PASS"  (and findings == 0, errors == 0)
  * verification-manifest.json.result == "PASS"
  * No critical check skipped or cancelled

Exit 0  = all evidence valid, gate may close.
Exit 1  = at least one invariant violated; gate stays OPEN.
Exit 2  = CLI usage error.

This validator MUST be the only authority that flips the final gate to PASS.
It is intentionally a separate file so that no future edit to the workflow's
bash can silently weaken the invariants.
"""
import argparse
import json
import sys
from pathlib import Path

# Checks whose "skipped" or "cancelled" outcome must keep the gate OPEN.
CRITICAL_CHECK_KEYS = {
    "frontend_deps",
    "backend_deps",
    "frontend_lint",
    "frontend_typecheck",
    "frontend_tests",
    "frontend_build",
    "sds_compliance",
    "logo_governance",
    "brand_name",
    "performance_budget",
    "backend_compile",
    "backend_tests",
    "workflow_security",
    "secret_scan",
    "smoke_backend",
    "smoke_frontend",
}


class EvidenceError(Exception):
    """Single invariant violation."""


def _load_json(path: Path, label: str) -> dict:
    if not path.exists():
        raise EvidenceError(f"MISSING_FILE: {label} not found at {path}")
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as e:
        raise EvidenceError(f"READ_ERROR: {label}: {e}")
    if not text.strip():
        raise EvidenceError(f"EMPTY_FILE: {label} is empty: {path}")
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        raise EvidenceError(f"INVALID_JSON: {label}: {e}")


def _require(value, expected, label: str):
    if value != expected:
        raise EvidenceError(
            f"VALUE_MISMATCH: {label} expected={expected!r} actual={value!r}"
        )


def _require_one_of(value, allowed, label: str):
    if value not in allowed:
        raise EvidenceError(
            f"VALUE_MISMATCH: {label} expected one of {allowed!r} actual={value!r}"
        )


def validate(args: argparse.Namespace) -> list:
    """Return list of error strings (empty == PASS)."""
    errors: list = []

    def check(label: str, fn):
        try:
            fn()
        except EvidenceError as e:
            errors.append(f"[{label}] {e}")

    # 1. Manifest
    def _manifest():
        m = _load_json(Path(args.manifest), "verification-manifest")
        _require(m.get("result"), "PASS", "manifest.result")
        _require(m.get("exactMainSha"), args.expected_sha, "manifest.exactMainSha")
        _require(
            str(m.get("workflowRunId")),
            str(args.expected_run_id),
            "manifest.workflowRunId",
        )
        # No critical check may be skipped or cancelled
        checks = m.get("checks", {}) or {}
        for key in CRITICAL_CHECK_KEYS:
            entry = checks.get(key)
            if entry is None:
                raise EvidenceError(
                    f"MISSING_CHECK: critical check '{key}' is absent from manifest"
                )
            status = (entry.get("status") or "").upper() if isinstance(entry, dict) else ""
            if status in ("SKIPPED", "CANCELLED"):
                raise EvidenceError(
                    f"SKIPPED_CRITICAL_CHECK: '{key}' status={status}"
                )
            if status != "SUCCESS":
                raise EvidenceError(
                    f"FAILED_CRITICAL_CHECK: '{key}' status={status}"
                )
        # Cross-check the failedChecks / skippedChecks summary arrays
        if m.get("failedChecks"):
            raise EvidenceError(
                f"FAILED_CHECKS_PRESENT: manifest.failedChecks={m['failedChecks']}"
            )
        if m.get("skippedChecks"):
            raise EvidenceError(
                f"SKIPPED_CHECKS_PRESENT: manifest.skippedChecks={m['skippedChecks']}"
            )

    check("manifest", _manifest)

    # 2. Secret scan report
    def _secret():
        s = _load_json(Path(args.secret_report), "secret-scan-report")
        _require(s.get("result"), "PASS", "secret.result")
        findings = s.get("findingsCount")
        if findings is None or int(findings) != 0:
            raise EvidenceError(
                f"SECRET_FINDINGS_NONZERO: findingsCount={findings!r}"
            )
        scan_errors = s.get("scanErrors") or []
        if scan_errors:
            raise EvidenceError(
                f"SECRET_SCAN_ERRORS_PRESENT: count={len(scan_errors)}"
            )
        # SHA must match the run
        _require(
            s.get("commitSha"),
            args.expected_sha,
            "secret.commitSha",
        )

    check("secret-scan", _secret)

    # 3. Backend smoke metadata
    def _backend_meta():
        b = _load_json(Path(args.backend_metadata), "backend-smoke-metadata")
        _require(b.get("result"), "PASS", "backend-metadata.result")
        _require(
            b.get("failureType") is None or b.get("failureType") == "None" or b.get("failureType") == "",
            True,
            "backend-metadata.failureType must be null",
        )
        # Sanity: HTTP 200 + healthUrl non-empty
        if int(b.get("httpStatus", 0)) != 200:
            raise EvidenceError(
                f"BACKEND_HTTP_NOT_200: httpStatus={b.get('httpStatus')!r}"
            )

    check("backend-metadata", _backend_meta)

    # 4. Backend health JSON
    def _backend_health():
        h = _load_json(Path(args.backend_health), "backend-health")
        _require(h.get("status"), "UP", "backend-health.status")

    check("backend-health", _backend_health)

    # 5. Frontend smoke metadata
    def _frontend_meta():
        f = _load_json(Path(args.frontend_metadata), "frontend-smoke-metadata")
        _require(f.get("result"), "PASS", "frontend-metadata.result")
        if int(f.get("httpStatus", 0)) not in (200, 302, 307):
            raise EvidenceError(
                f"FRONTEND_HTTP_UNEXPECTED: httpStatus={f.get('httpStatus')!r}"
            )
        if not f.get("brandNamePresent"):
            raise EvidenceError("FRONTEND_BRAND_MISSING: brandNamePresent=False")
        # The URL must be the canonical root route — never the legacy /auth/login.
        url = f.get("url", "")
        if not url or "/auth/login" in url:
            raise EvidenceError(
                f"FRONTEND_URL_LEGACY: url={url!r} — must be root route, not /auth/login"
            )

    check("frontend-metadata", _frontend_meta)

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="SNAD Post-Merge Evidence Validator (independent fail-closed gate)"
    )
    parser.add_argument("--manifest", required=True, help="verification-manifest.json")
    parser.add_argument("--secret-report", required=True, help="secret-scan-report.json")
    parser.add_argument("--backend-metadata", required=True, help="backend-smoke-metadata.json")
    parser.add_argument("--backend-health", required=True, help="backend-health.json")
    parser.add_argument("--frontend-metadata", required=True, help="frontend-smoke-metadata.json")
    parser.add_argument("--expected-sha", required=True, help="github.sha")
    parser.add_argument("--expected-run-id", required=True, help="github.run_id")
    args = parser.parse_args()

    print("=== SNAD Post-Merge Evidence Validator ===")
    print(f"  expected SHA    : {args.expected_sha}")
    print(f"  expected run-id : {args.expected_run_id}")
    print(f"  manifest        : {args.manifest}")
    print(f"  secret-report   : {args.secret_report}")
    print(f"  backend-meta    : {args.backend_metadata}")
    print(f"  backend-health  : {args.backend_health}")
    print(f"  frontend-meta   : {args.frontend_metadata}")
    print()

    errors = validate(args)

    if errors:
        print(f"RESULT: FAIL  ({len(errors)} invariant violation(s))")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)

    print("RESULT: PASS — all evidence invariants satisfied.")
    sys.exit(0)


if __name__ == "__main__":
    main()
