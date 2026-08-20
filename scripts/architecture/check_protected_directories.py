#!/usr/bin/env python3
"""
SANAD Architecture Protection — Protected Directories Validator
==============================================================

Enforces Architecture Protection Policy §13 (Protected Directories):
  Changes to protected directories MUST include an architecture impact document.

Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md §13
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

PROTECTED_DIRS = [
    "apps/web/app/executive/",
    "apps/web/app/system-health/",
    "apps/web/app/crm/",
    "apps/web/app/erp/",
    "apps/web/app/accounting/",
    "apps/web/app/hrm/",
    "apps/web/app/pos/",
    "apps/sanad-platform/src/main/java/com/sanad/platform/executive/",
    "apps/sanad-platform/src/main/java/com/sanad/platform/health/",
    "apps/sanad-platform/src/main/java/com/sanad/platform/crm/",
    "apps/sanad-platform/src/main/java/com/sanad/platform/businessprocess/",
    "apps/sanad-platform/src/main/java/com/sanad/platform/access/",
    "apps/sanad-platform/src/main/java/com/sanad/platform/shared/",
]

IMPACT_DOC_DIR = REPO_ROOT / "docs" / "architecture" / "impact"


def run_git(args: list[str], cwd: Path = REPO_ROOT) -> str:
    try:
        result = subprocess.run(
            ["git"] + args,
            cwd=str(cwd),
            check=False,
            capture_output=True,
            text=True,
            timeout=30,
        )
        return result.stdout.strip() if result.returncode == 0 else ""
    except (subprocess.SubprocessError, FileNotFoundError):
        return ""


def get_diff_files() -> list[str]:
    diff = run_git(["diff", "--name-only", "origin/main...HEAD"])
    if not diff:
        diff = run_git(["diff", "--name-only", "main...HEAD"])
    if not diff:
        diff = run_git(["diff", "--name-only", "HEAD"]) + "\n" + run_git(["ls-files", "--others", "--exclude-standard"])
    return [line.strip() for line in diff.splitlines() if line.strip()]


def check_protected_changes(changed_files: list[str]) -> list[str]:
    touched = []
    seen = set()
    for f in changed_files:
        for pd in PROTECTED_DIRS:
            if f.startswith(pd) and pd not in seen:
                touched.append(pd)
                seen.add(pd)
                break
    return touched


def check_impact_doc(branch: str) -> Path | None:
    if not IMPACT_DOC_DIR.exists():
        return None
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", branch)[:80]
    candidates = [
        IMPACT_DOC_DIR / f"{safe}.md",
    ]
    # also any .md file whose name contains the branch slug
    slug_parts = [p for p in safe.split("_") if len(p) > 3]
    for md in IMPACT_DOC_DIR.glob("*.md"):
        candidates.append(md)
        # match if any slug part appears in the filename
        if any(part.lower() in md.stem.lower() for part in slug_parts):
            return md
    for c in candidates:
        if c.exists():
            return c
    return None


def main() -> int:
    branch = run_git(["rev-parse", "--abbrev-ref", "HEAD"]) or "HEAD"
    changed = get_diff_files()

    if not changed:
        print(f"[SKIP] No diff detected on branch '{branch}' — protected directory check passed (vacuously)")
        return 0

    touched = check_protected_changes(changed)

    if not touched:
        print(f"[PASS] No protected directories modified on branch '{branch}'")
        return 0

    print(f"[INFO] Protected directories touched on branch '{branch}':")
    for pd in touched:
        print(f"    - {pd}")

    impact_doc = check_impact_doc(branch)
    if impact_doc:
        print(f"[PASS] Architecture impact analysis document found: {impact_doc.relative_to(REPO_ROOT)}")
        text = impact_doc.read_text(encoding="utf-8", errors="replace")
        required_sections = [
            "Architecture impact analysis",
            "Dependency analysis",
            "Risk analysis",
            "Migration strategy",
            "Rollback strategy",
            "Updated documentation",
        ]
        missing = [s for s in required_sections if s.lower() not in text.lower()]
        if missing:
            print(f"[FAIL] Impact doc is missing required sections: {missing}")
            return 1
        print("[PASS] Impact doc contains all required sections")
        return 0

    print(
        f"\n[FAIL] Changes to protected directories detected, but NO matching architecture impact document was found.\n"
        f"       Required: docs/architecture/impact/<branch-slug>.md\n"
        f"       Required sections:\n"
        f"         - Architecture impact analysis\n"
        f"         - Dependency analysis\n"
        f"         - Risk analysis\n"
        f"         - Migration strategy\n"
        f"         - Rollback strategy\n"
        f"         - Updated documentation\n"
        f"       Policy: §13 — Changes to protected directories require architecture approval"
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
