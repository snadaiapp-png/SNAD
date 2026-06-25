#!/usr/bin/env python3
"""Generate a non-destructive GitHub branch reconciliation inventory."""

from __future__ import annotations

import csv
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

API_ROOT = "https://api.github.com"


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


def api_request(path: str, token: str) -> tuple[Any, dict[str, str]]:
    url = path if path.startswith("http") else f"{API_ROOT}{path}"
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "SANAD-branch-inventory",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return payload, dict(response.headers.items())
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub API {exc.code} for {url}: {body[:500]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"GitHub API connection failed for {url}: {exc}") from exc


def paginated(path: str, token: str) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    separator = "&" if "?" in path else "?"
    page = 1
    while True:
        payload, _ = api_request(f"{path}{separator}per_page=100&page={page}", token)
        if not isinstance(payload, list):
            raise RuntimeError(f"Expected a list from paginated endpoint: {path}")
        items.extend(payload)
        if len(payload) < 100:
            return items
        page += 1


def get_commit_date(repository: str, sha: str, token: str) -> str:
    payload, _ = api_request(f"/repos/{repository}/commits/{sha}", token)
    commit = payload.get("commit", {}) if isinstance(payload, dict) else {}
    committer = commit.get("committer", {}) if isinstance(commit, dict) else {}
    author = commit.get("author", {}) if isinstance(commit, dict) else {}
    return str(committer.get("date") or author.get("date") or "")


def compare_branch(repository: str, base: str, head: str, token: str) -> dict[str, Any]:
    base_q = urllib.parse.quote(base, safe="")
    head_q = urllib.parse.quote(head, safe="")
    payload, _ = api_request(f"/repos/{repository}/compare/{base_q}...{head_q}", token)
    if not isinstance(payload, dict):
        raise RuntimeError(f"Invalid compare response for {head}")
    return payload


def find_pull_requests(repository: str, owner: str, branch: str, token: str) -> list[dict[str, Any]]:
    head = urllib.parse.quote(f"{owner}:{branch}", safe="")
    return paginated(f"/repos/{repository}/pulls?state=all&head={head}", token)


def changed_paths(compare: dict[str, Any]) -> list[str]:
    files = compare.get("files", [])
    if not isinstance(files, list):
        return []
    return [str(item.get("filename", "")) for item in files if isinstance(item, dict)]


def has_security_sensitive_change(paths: list[str]) -> bool:
    markers = (
        ".github/workflows/",
        "security",
        "auth",
        "credential",
        "secret",
        "docker",
        "terraform",
        "infrastructure",
        "flyway",
    )
    return any(any(marker in path.lower() for marker in markers) for path in paths)


def has_deployment_dependency(branch: str, paths: list[str]) -> bool:
    branch_markers = ("release", "deploy", "production", "prod", "render", "vercel")
    path_markers = ("render.yaml", "vercel.json", "docker-compose.prod", "deployment")
    branch_lower = branch.lower()
    return any(marker in branch_lower for marker in branch_markers) or any(
        any(marker in path.lower() for marker in path_markers) for path in paths
    )


def classify(
    branch: str,
    default_branch: str,
    ahead_by: int,
    open_prs: list[dict[str, Any]],
    merged_prs: list[dict[str, Any]],
    security_sensitive: bool,
    deployment_dependency: bool,
) -> tuple[str, bool, str]:
    if branch == default_branch:
        return "MAIN", False, "Default branch"
    if open_prs:
        numbers = ",".join(str(pr.get("number")) for pr in open_prs)
        return "OPEN PR — KEEP", False, f"Referenced by open PR(s): {numbers}"
    if deployment_dependency:
        return "RELEASE BRANCH", False, "Potential release or deployment dependency requires owner review"
    if ahead_by > 0:
        reason = f"Contains {ahead_by} commit(s) not present in {default_branch}"
        if security_sensitive:
            reason += "; security-sensitive paths detected"
        return "UNIQUE WORK — REVIEW", False, reason
    if merged_prs:
        numbers = ",".join(str(pr.get("number")) for pr in merged_prs)
        return "MERGED — DELETE", True, f"Merged PR evidence: {numbers}; no unique commits"
    return "MERGED — DELETE", True, f"No commits ahead of {default_branch}"


def main() -> int:
    token = require_env("GH_TOKEN")
    repository = require_env("GITHUB_REPOSITORY")
    default_branch = os.environ.get("DEFAULT_BRANCH", "main").strip() or "main"
    output_dir = Path(os.environ.get("OUTPUT_DIR", "branch-audit"))
    output_dir.mkdir(parents=True, exist_ok=True)

    owner = repository.split("/", 1)[0]
    branches = paginated(f"/repos/{repository}/branches", token)
    rows: list[dict[str, Any]] = []

    for index, branch_item in enumerate(branches, start=1):
        branch = str(branch_item.get("name", ""))
        commit = branch_item.get("commit", {}) if isinstance(branch_item, dict) else {}
        head_sha = str(commit.get("sha", "")) if isinstance(commit, dict) else ""
        if not branch or not head_sha:
            raise RuntimeError(f"Invalid branch record at index {index}")

        print(f"[{index}/{len(branches)}] Inspecting {branch}", flush=True)
        compare = compare_branch(repository, default_branch, branch, token)
        prs = find_pull_requests(repository, owner, branch, token)
        open_prs = [pr for pr in prs if pr.get("state") == "open"]
        merged_prs = [pr for pr in prs if pr.get("merged_at")]
        paths = changed_paths(compare)
        security_sensitive = has_security_sensitive_change(paths)
        deployment_dependency = has_deployment_dependency(branch, paths)
        ahead_by = int(compare.get("ahead_by", 0) or 0)
        behind_by = int(compare.get("behind_by", 0) or 0)
        classification, deletion_eligible, reason = classify(
            branch,
            default_branch,
            ahead_by,
            open_prs,
            merged_prs,
            security_sensitive,
            deployment_dependency,
        )

        rows.append(
            {
                "branch": branch,
                "head_sha": head_sha,
                "last_commit_date": get_commit_date(repository, head_sha, token),
                "ahead_by": ahead_by,
                "behind_by": behind_by,
                "compare_status": str(compare.get("status", "")),
                "associated_pr_numbers": ",".join(str(pr.get("number")) for pr in prs),
                "associated_pr_states": ",".join(str(pr.get("state", "")) for pr in prs),
                "merged_pr_numbers": ",".join(str(pr.get("number")) for pr in merged_prs),
                "changed_file_count": len(paths),
                "changed_files": " | ".join(paths[:50]),
                "security_sensitive": str(security_sensitive).lower(),
                "deployment_dependency": str(deployment_dependency).lower(),
                "classification": classification,
                "deletion_eligible": str(deletion_eligible).lower(),
                "reason": reason,
            }
        )

    fieldnames = list(rows[0].keys()) if rows else ["branch"]
    csv_path = output_dir / "branch-inventory.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    counts: dict[str, int] = {}
    for row in rows:
        classification = str(row["classification"])
        counts[classification] = counts.get(classification, 0) + 1

    md_path = output_dir / "branch-inventory.md"
    with md_path.open("w", encoding="utf-8") as handle:
        handle.write("# SANAD Branch Reconciliation Inventory\n\n")
        handle.write(f"- Repository: `{repository}`\n")
        handle.write(f"- Default branch: `{default_branch}`\n")
        handle.write(f"- Total branches: **{len(rows)}**\n\n")
        handle.write("## Classification Summary\n\n")
        handle.write("| Classification | Count |\n|---|---:|\n")
        for classification in sorted(counts):
            handle.write(f"| {classification} | {counts[classification]} |\n")
        handle.write("\n## Deletion Candidates\n\n")
        candidates = [row for row in rows if row["deletion_eligible"] == "true"]
        if not candidates:
            handle.write("No automatically eligible branches were identified.\n")
        else:
            handle.write("| Branch | Head SHA | Reason |\n|---|---|---|\n")
            for row in candidates:
                handle.write(
                    f"| `{row['branch']}` | `{str(row['head_sha'])[:12]}` | {row['reason']} |\n"
                )
        handle.write("\n> This report is non-destructive. No branch is deleted by this workflow.\n")

    print(json.dumps({"branches": len(rows), "classifications": counts}, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
