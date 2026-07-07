#!/usr/bin/env python3
"""Generate a truthful post-merge verification manifest from actual step outcomes."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate SNAD post-merge manifest")
    parser.add_argument("--output", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--trigger", required=True)
    parser.add_argument("--actor", required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--check", action="append", default=[])
    args = parser.parse_args()

    checks: dict[str, dict[str, str]] = {}
    for item in args.check:
        if "=" not in item:
            raise SystemExit(f"Invalid check value: {item}")
        name, outcome = item.split("=", 1)
        checks[name] = {"status": outcome.upper(), "evidence": f"step outcome: {outcome}"}

    passed = [name for name, value in checks.items() if value["status"] == "SUCCESS"]
    failed = [name for name, value in checks.items() if value["status"] == "FAILURE"]
    skipped = [name for name, value in checks.items() if value["status"] in {"SKIPPED", "CANCELLED"}]
    result = "PASS" if not failed and not skipped and checks else "FAIL"

    manifest = {
        "verificationType": "post-merge-main",
        "exactMainSha": args.sha,
        "workflowRunId": args.run_id,
        "triggerType": args.trigger,
        "startedAtUtc": args.started_at,
        "completedAtUtc": datetime.now(timezone.utc).isoformat(),
        "actor": args.actor,
        "result": result,
        "failedGate": failed[0] if failed else (skipped[0] if skipped else None),
        "checks": checks,
        "passedChecks": passed,
        "failedChecks": failed,
        "skippedChecks": skipped,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
