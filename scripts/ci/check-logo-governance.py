#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================================
 SNAD Logo Governance — CI Lint
----------------------------------------------------------------------------
 PURPOSE
 -------
 Enforces the executive order that the SnadLogo SDS component is the ONLY
 module in `apps/web/` permitted to import or reference brand SVG files
 directly. Every other surface MUST consume `<SnadLogo />`.

 RULES
 -----
 1. No `.tsx` file under `apps/web/` (excluding `components/sds/` and
    `__tests__/`) may:
      • import from `/assets/brand/snad-logo-*.svg` (or any brand SVG)
      • use `<img src="/assets/brand/snad-logo-*.svg">`
      • reference `snad-logo-*.svg`, `snad-favicon.svg`, or
        `snad-app-icon.svg` as a string literal
 2. The auth login form (`apps/web/components/auth/login-form.tsx`) MUST
    import and render `<SnadLogo />`. This guards against regressions
    where the brand mark is replaced with a plain `<div>SNAD</div>`.
 3. The only allowed exception is `apps/web/components/sds/SnadLogo.tsx`
    itself — that file is the canonical importer of brand SVGs.

 ALLOWLIST
 ---------
   • apps/web/components/sds/SnadLogo.tsx            (canonical importer)
   • apps/web/components/sds/__tests__/SnadLogo.test.tsx (tests reference
     the SVG paths to verify the variant → src mapping)

 USAGE
 -----
   python3 scripts/ci/check-logo-governance.py [apps/web]

 EXIT CODES
 ----------
   0 — compliant
   1 — violations found
   2 — usage error / unexpected exception

 REPORT FORMAT
 -------------
   <relative/path>:<line>:<col>: <rule_id> — <message>
============================================================================
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path
from typing import Iterable, List, Tuple

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DEFAULT_SCAN_ROOT = "apps/web"

# Only scan .tsx files for direct SVG references (TS files don't render JSX).
SCAN_EXTENSIONS = {".tsx"}

# Directories to skip entirely.
SKIP_DIRS = {
    "node_modules",
    ".next",
    ".git",
    "dist",
    "build",
    "coverage",
    ".turbo",
    "out",
}

# The SDS directory is the canonical owner of brand assets. We exclude it
# from the direct-import scan EXCEPT for SnadLogo.tsx itself, which we
# explicitly allow.
SDS_DIR = "apps/web/components/sds"

# Files that ARE allowed to reference brand SVGs directly.
ALLOWED_FILES = {
    "apps/web/components/sds/SnadLogo.tsx",
    "apps/web/components/sds/__tests__/SnadLogo.test.tsx",
}

# Auth layout file that MUST use SnadLogo.
AUTH_LOGIN_FORM = "apps/web/components/auth/login-form.tsx"

# ---------------------------------------------------------------------------
# Regex patterns
# ---------------------------------------------------------------------------

# Matches any reference to a brand SVG path, whether via import, img src,
# or string literal. Examples:
#   /assets/brand/snad-logo-primary.svg
#   /assets/brand/snad-logo-white.svg
#   /assets/brand/snad-logo-mono.svg
#   /assets/brand/snad-logo-vertical.svg
#   /assets/brand/snad-favicon.svg
#   /assets/brand/snad-app-icon.svg
BRAND_SVG_RE = re.compile(
    r"[/\\]assets[/\\]brand[/\\]snad-(?:logo-[a-z]+|favicon|app-icon)\.svg",
    re.IGNORECASE,
)

# Matches `import ... from "<path>"` where <path> ends in a brand SVG.
# We catch the broader BRAND_SVG_RE above so this is a stricter subset used
# only to produce a more specific rule_id.
IMPORT_BRAND_SVG_RE = re.compile(
    r"""import\s+[^;]*?from\s+["']([^"']*snad-(?:logo-[a-z]+|favicon|app-icon)\.svg)["']""",
    re.IGNORECASE,
)

# Matches `<img ... src="<brand-svg>" ...>` JSX usage.
IMG_BRAND_SVG_RE = re.compile(
    r"""<img\b[^>]*\bsrc\s*=\s*["']([^"']*snad-(?:logo-[a-z]+|favicon|app-icon)\.svg)["']""",
    re.IGNORECASE,
)

# Matches a string literal containing a brand SVG path (catch-all).
STRING_BRAND_SVG_RE = re.compile(
    r"""["']([^"']*snad-(?:logo-[a-z]+|favicon|app-icon)\.svg)["']""",
    re.IGNORECASE,
)

# Matches the SnadLogo component import path.
SNADLOGO_IMPORT_RE = re.compile(
    r"""(?:from\s+["']@/components/sds["']|from\s+["']@/components/sds/SnadLogo["'])""",
)

# Matches the SnadLogo JSX usage.
SNADLOGO_USAGE_RE = re.compile(r"<SnadLogo\b")

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def normalize_relpath(path: Path, scan_root: Path) -> str:
    """Return a forward-slash path relative to the git repo root (best-effort)."""
    try:
        repo_root = path
        while repo_root != repo_root.parent:
            if (repo_root / ".git").exists():
                return str(path.relative_to(repo_root).as_posix())
            repo_root = repo_root.parent
    except (ValueError, OSError):
        pass
    try:
        return str(path.relative_to(scan_root).as_posix())
    except ValueError:
        return str(path.as_posix())


def iter_scan_files(scan_root: Path) -> Iterable[Path]:
    """Yield every .tsx file under scan_root, skipping excluded dirs and
    the SDS directory (except SnadLogo.tsx and its test, which are
    allowlisted explicitly)."""
    for dirpath, dirnames, filenames in os.walk(scan_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            p = Path(dirpath) / name
            if p.suffix.lower() not in SCAN_EXTENSIONS:
                continue
            yield p


def is_in_sds_dir(rel_path: str) -> bool:
    return rel_path.startswith("apps/web/components/sds/")


def is_test_file(rel_path: str) -> bool:
    parts = rel_path.split("/")
    return "__tests__" in parts or rel_path.endswith(".test.tsx")


# ---------------------------------------------------------------------------
# Check functions
# ---------------------------------------------------------------------------


def check_direct_brand_references(
    path: Path, rel: str
) -> List[str]:
    """Flag any direct reference to a brand SVG file outside the allowlist."""
    if rel in ALLOWED_FILES:
        return []

    violations: List[str] = []
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    lines = text.splitlines()
    for line_no, line in enumerate(lines, start=1):
        # Skip comment-only lines (// or /* */ or *).
        stripped = line.lstrip()
        if (
            stripped.startswith("//")
            or stripped.startswith("/*")
            or stripped.startswith("*")
        ):
            # We still scan comment lines for brand SVG references because
            # developers often leave dead imports in comments. But we tag
            # them as a separate, lower-severity rule.
            pass

        # 1) import ... from "snad-logo-*.svg"
        for m in IMPORT_BRAND_SVG_RE.finditer(line):
            col = m.start(1) + 1
            value = m.group(1)
            violations.append(
                f"{rel}:{line_no}:{col}: IMPORT_BRAND_SVG — "
                f'direct import of brand SVG "{value}". '
                f"Use the <SnadLogo /> component instead."
            )

        # 2) <img src="snad-logo-*.svg">
        for m in IMG_BRAND_SVG_RE.finditer(line):
            col = m.start(1) + 1
            value = m.group(1)
            violations.append(
                f"{rel}:{line_no}:{col}: IMG_BRAND_SVG — "
                f'<img> with brand SVG src "{value}". '
                f"Use the <SnadLogo /> component instead."
            )

        # 3) Any other string literal containing a brand SVG path
        for m in STRING_BRAND_SVG_RE.finditer(line):
            # Skip if already flagged by the import or img rule on this line.
            start = m.start(1)
            col = start + 1
            value = m.group(1)
            # Determine if this match is part of an import statement.
            prefix = line[: m.start()]
            if "from" in prefix and prefix.strip().endswith(("'", '"')):
                continue  # Already flagged by IMPORT_BRAND_SVG_RE
            if "<img" in prefix and "src" in prefix:
                continue  # Already flagged by IMG_BRAND_SVG_RE
            violations.append(
                f"{rel}:{line_no}:{col}: STRING_BRAND_SVG — "
                f'string literal referencing brand SVG "{value}". '
                f"Use the <SnadLogo /> component instead."
            )

    return violations


def check_auth_login_form_uses_snadlogo(scan_root: Path) -> List[str]:
    """Verify that login-form.tsx imports AND uses SnadLogo."""
    rel = AUTH_LOGIN_FORM
    path = (scan_root.parent.parent / rel).resolve()
    if not path.exists():
        # Try as relative to the current working directory
        path = (Path.cwd() / rel).resolve()
    if not path.exists():
        return [
            f"{rel}:0:0: AUTH_FORM_MISSING — "
            f"login form file not found at expected path."
        ]

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    violations: List[str] = []
    if not SNADLOGO_IMPORT_RE.search(text):
        violations.append(
            f"{rel}:0:0: AUTH_FORM_NO_SNADLOGO_IMPORT — "
            f"login form must import SnadLogo from @/components/sds."
        )
    if not SNADLOGO_USAGE_RE.search(text):
        violations.append(
            f"{rel}:0:0: AUTH_FORM_NO_SNADLOGO_USAGE — "
            f"login form must render <SnadLogo /> for the brand mark."
        )

    return violations


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main(argv: List[str]) -> int:
    scan_root_arg = argv[1] if len(argv) > 1 else DEFAULT_SCAN_ROOT

    cwd = Path.cwd()
    repo_root = cwd
    while repo_root != repo_root.parent:
        if (repo_root / ".git").exists():
            break
        repo_root = repo_root.parent

    scan_root = (repo_root / scan_root_arg).resolve()
    if not scan_root.exists():
        print(
            f"ERROR: scan root does not exist: {scan_root}",
            file=sys.stderr,
        )
        return 2

    print("SNAD Logo Governance check")
    print(f"  scan root : {scan_root}")
    print(f"  allowed   : {len(ALLOWED_FILES)} canonical importer file(s)")
    print(f"  auth form : {AUTH_LOGIN_FORM}")
    print()

    all_violations: List[str] = []
    files_scanned = 0

    for path in iter_scan_files(scan_root):
        rel = normalize_relpath(path, scan_root)
        # Skip files inside components/sds/ except the allowlisted ones.
        if is_in_sds_dir(rel) and rel not in ALLOWED_FILES:
            continue
        # Skip __tests__ files (they may legitimately reference SVG paths).
        if is_test_file(rel) and rel not in ALLOWED_FILES:
            continue

        files_scanned += 1
        all_violations.extend(check_direct_brand_references(path, rel))

    # Special check: the auth login form must import + use SnadLogo.
    all_violations.extend(check_auth_login_form_uses_snadlogo(scan_root))

    if all_violations:
        print(
            f"FAIL — {len(all_violations)} violation(s) found in "
            f"{files_scanned} file(s) scanned:"
        )
        print()
        for v in all_violations:
            print(f"  {v}")
        print()
        print("To fix:")
        print("  1. Replace any direct brand SVG import / <img src> with")
        print("     <SnadLogo variant=\"primary\" size=\"md\" />.")
        print("  2. Import SnadLogo from @/components/sds:")
        print('       import { SnadLogo } from "@/components/sds";')
        print("  3. The auth login form MUST render <SnadLogo /> for the brand")
        print("     mark — no raw <div>SNAD</div> or <img> is permitted.")
        print("  4. See apps/web/design-system/documentation/LOGO_USAGE.md")
        print("     for the full governance policy.")
        return 1

    print(f"PASS — 0 violations across {files_scanned} files scanned.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except KeyboardInterrupt:
        sys.exit(130)
    except Exception as exc:  # pragma: no cover
        print(f"FATAL: {exc!r}", file=sys.stderr)
        sys.exit(2)
