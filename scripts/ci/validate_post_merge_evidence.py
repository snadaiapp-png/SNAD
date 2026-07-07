#!/usr/bin/env python3
"""Fail-closed validator for SNAD post-merge verification evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


def load_object(path_value: str, label: str, errors: list[str]) -> dict[str, Any]:
    path = Path(path_value)
    if not path.is_file():
        errors.append(f"{label}: missing file: {path}")
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"{label}: invalid JSON: {exc}")
        return {}
    if not isinstance(data, dict):
        errors.append(f"{label}: root value must be an object")
        return {}
    return data


def text(value: Any) -> str:
    return "" if value is None else str(value)


def validate(args: argparse.Namespace) -> list[str]:
    errors: list[str] = []
    manifest = load_object(args.manifest, "manifest", errors)
    scan_report = load_object(args.scan_report, "scan report", errors)
    backend_meta = load_object(args.backend_metadata, "backend metadata", errors)
    backend_health = load_object(args.backend_health, "backend health", errors)
    frontend_meta = load_object(args.frontend_metadata, "frontend metadata", errors)

    if manifest:
        if manifest.get("result") != "PASS":
            errors.append("manifest: result must be PASS")
        if manifest.get("exactMainSha") != args.expected_sha:
            errors.append("manifest: exactMainSha mismatch")
        if text(manifest.get("workflowRunId")) != args.expected_run_id:
            errors.append("manifest: workflowRunId mismatch")
        if manifest.get("failedGate") not in (None, ""):
            errors.append("manifest: failedGate must be null")
        if manifest.get("failedChecks") not in ([], None):
            errors.append("manifest: failedChecks must be empty")
        if manifest.get("skippedChecks") not in ([], None):
            errors.append("manifest: skippedChecks must be empty")

    if scan_report:
        if scan_report.get("result") != "PASS":
            errors.append("scan report: result must be PASS")
        if scan_report.get("findingsCount") != 0:
            errors.append("scan report: findingsCount must be 0")
        if scan_report.get("scanErrors") not in ([], None):
            errors.append("scan report: scanErrors must be empty")
        commit_sha = scan_report.get("commitSha")
        if commit_sha not in (None, "", args.expected_sha):
            errors.append("scan report: commitSha mismatch")
        run_id = text(scan_report.get("workflowRunId"))
        if run_id not in ("", args.expected_run_id):
            errors.append("scan report: workflowRunId mismatch")

    if backend_meta:
        if backend_meta.get("result") != "PASS":
            errors.append("backend metadata: result must be PASS")
        if backend_meta.get("httpStatus") != 200:
            errors.append("backend metadata: httpStatus must be 200")
        if backend_meta.get("healthStatus") != "UP":
            errors.append("backend metadata: healthStatus must be UP")
        if backend_meta.get("processStarted") is not True:
            errors.append("backend metadata: processStarted must be true")

    if backend_health and backend_health.get("status") != "UP":
        errors.append("backend health: status must be UP")

    if frontend_meta:
        if frontend_meta.get("result") != "PASS":
            errors.append("frontend metadata: result must be PASS")
        if frontend_meta.get("httpStatus") not in (200, 302, 307):
            errors.append("frontend metadata: unexpected httpStatus")
        if frontend_meta.get("brandNamePresent") is not True:
            errors.append("frontend metadata: brandNamePresent must be true")
        if frontend_meta.get("processStarted") is not True:
            errors.append("frontend metadata: processStarted must be true")
        if frontend_meta.get("url") != "http://127.0.0.1:3001/":
            errors.append("frontend metadata: URL must be the canonical root route")

    return errors


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate SNAD post-merge evidence")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--scan-report", required=True)
    parser.add_argument("--backend-metadata", required=True)
    parser.add_argument("--backend-health", required=True)
    parser.add_argument("--frontend-metadata", required=True)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--expected-run-id", required=True)
    args = parser.parse_args()

    errors = validate(args)
    if errors:
        print("Post-merge evidence: FAIL", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        raise SystemExit(1)

    print("Post-merge evidence: PASS")
    raise SystemExit(0)


if __name__ == "__main__":
    main()
