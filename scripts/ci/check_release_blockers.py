#!/usr/bin/env python3
"""SANAD release blocker state checker.

This script reads blocker issue states from GitHub when a token is available.
It is intentionally fail-closed only for formal release approval mode. In normal
PR mode it emits a report so teams can see unresolved blockers without blocking
non-release remediation work.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass(frozen=True)
class Blocker:
    issue: int
    name: str
    category: str


BLOCKERS = [
    Blocker(197, "AWS/OIDC live evidence for OWASP gate", "security"),
    Blocker(200, "Production BFF/backend path", "production"),
    Blocker(292, "Final security assessment", "security"),
    Blocker(293, "Backup and restore validation", "resilience"),
    Blocker(294, "Monitoring, alerting and incident response", "operations"),
    Blocker(295, "Commercial infrastructure readiness", "infrastructure"),
    Blocker(296, "Fail-closed commercial workflow", "release"),
    Blocker(297, "Email delivery evidence", "quality"),
    Blocker(298, "Independent human approvals", "governance"),
    Blocker(324, "Stage 09/10 controller", "stage-09-10"),
    Blocker(325, "CRM baseline and architecture", "crm"),
    Blocker(326, "CRM runtime", "crm"),
    Blocker(327, "CRM experience quality operations", "crm"),
    Blocker(328, "AI Gateway and policy contract", "ai"),
    Blocker(329, "AI CRM intelligence", "ai"),
    Blocker(330, "AI safety evaluation operations", "ai"),
    Blocker(331, "Integrated final acceptance", "stage-09-10"),
]


def github_get_issue(repo: str, issue: int, token: str) -> dict:
    url = f"https://api.github.com/repos/{repo}/issues/{issue}"
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    repo = os.getenv("GITHUB_REPOSITORY", "snadaiapp-png/SNAD")
    token = os.getenv("GITHUB_TOKEN") or os.getenv("GH_TOKEN")
    formal_release = os.getenv("SANAD_FORMAL_RELEASE_GO", "").lower() == "true"

    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "repository": repo,
        "formalReleaseMode": formal_release,
        "blockers": [],
        "summary": {"open": 0, "closed": 0, "unknown": 0},
        "decision": "NO-GO",
    }

    if not token:
        report["summary"]["unknown"] = len(BLOCKERS)
        report["blockers"] = [
            {"issue": item.issue, "name": item.name, "category": item.category, "state": "unknown", "reason": "missing GitHub token"}
            for item in BLOCKERS
        ]
        print(json.dumps(report, indent=2, sort_keys=True))
        if formal_release:
            print("RELEASE_BLOCKER_FAIL: cannot verify blockers in formal release mode without GitHub token")
            return 1
        return 0

    for item in BLOCKERS:
        try:
            data = github_get_issue(repo, item.issue, token)
            state = data.get("state", "unknown")
            title = data.get("title", item.name)
            url = data.get("html_url", "")
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
            state = "unknown"
            title = item.name
            url = ""
            report["blockers"].append(
                {"issue": item.issue, "name": item.name, "category": item.category, "state": state, "reason": str(exc)}
            )
            report["summary"]["unknown"] += 1
            continue

        report["blockers"].append(
            {"issue": item.issue, "name": item.name, "title": title, "category": item.category, "state": state, "url": url}
        )
        if state == "closed":
            report["summary"]["closed"] += 1
        elif state == "open":
            report["summary"]["open"] += 1
        else:
            report["summary"]["unknown"] += 1

    if report["summary"]["open"] == 0 and report["summary"]["unknown"] == 0:
        report["decision"] = "ELIGIBLE_FOR_GOVERNANCE_REVIEW"

    print(json.dumps(report, indent=2, sort_keys=True))

    if formal_release and report["decision"] != "ELIGIBLE_FOR_GOVERNANCE_REVIEW":
        print("RELEASE_BLOCKER_FAIL: formal release mode requires all blockers closed and verifiable")
        return 1

    print("RELEASE_BLOCKER_REPORT_COMPLETE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
