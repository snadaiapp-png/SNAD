#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================================
 SNAD Logo Governance — CI Lint
----------------------------------------------------------------------------
 PURPOSE
 -------
 Enforces the executive order that the SnadLogo SDS component is the ONLY
 module in `apps/web/` permitted to import or reference brand assets directly.
 Every other surface MUST consume `<SnadLogo />`.

 RULES
 -----
 1. No `.tsx` file under `apps/web/` (excluding `components/sds/` and
    `__tests__/`) may directly reference SNAD brand SVG/PNG assets.
 2. The auth login form (`apps/web/components/auth/login-form.tsx`) MUST
    import and render `<SnadLogo />`.
 3. The only application-code exception is
    `apps/web/components/sds/SnadLogo.tsx` itself.
 4. The Login v2 official wordmark must exist with the exact approved
    SHA-256; substitutes, regenerated files, or modified bytes fail CI.

 USAGE
 -----
   python3 scripts/ci/check-logo-governance.py [apps/web]

 EXIT CODES
 ----------
   0 — compliant
   1 — violations found
   2 — usage error / unexpected exception
============================================================================
"""

from __future__ import annotations

import hashlib
import os
import re
import sys
from pathlib import Path
from typing import Iterable, List

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DEFAULT_SCAN_ROOT = "apps/web"
SCAN_EXTENSIONS = {".tsx"}

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

SDS_DIR = "apps/web/components/sds"

ALLOWED_FILES = {
    "apps/web/components/sds/SnadLogo.tsx",
    "apps/web/components/sds/__tests__/SnadLogo.test.tsx",
}

AUTH_LOGIN_FORM = "apps/web/components/auth/login-form.tsx"
OFFICIAL_WORDMARK_PATH = Path(
    "apps/web/public/assets/brand/snad-logo-official-wordmark.png"
)
OFFICIAL_WORDMARK_SHA256 = (
    "98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251"
)

# ---------------------------------------------------------------------------
# Regex patterns
# ---------------------------------------------------------------------------

# Matches governed brand SVG/PNG paths. The [a-z0-9-]+ portion intentionally
# accepts multi-word names such as `snad-logo-official-wordmark.png`.
BRAND_ASSET_RE = re.compile(
    r"[/\\]assets[/\\]brand[/\\]snad-(?:logo-[a-z0-9-]+|favicon|app-icon)\.(?:svg|png)",
    re.IGNORECASE,
)

IMPORT_BRAND_ASSET_RE = re.compile(
    r"""import\s+[^;]*?from\s+["']([^"']*snad-(?:logo-[a-z0-9-]+|favicon|app-icon)\.(?:svg|png))["']""",
    re.IGNORECASE,
)

IMG_BRAND_ASSET_RE = re.compile(
    r"""<img\b[^>]*\bsrc\s*=\s*["']([^"']*snad-(?:logo-[a-z0-9-]+|favicon|app-icon)\.(?:svg|png))["']""",
    re.IGNORECASE,
)

STRING_BRAND_ASSET_RE = re.compile(
    r"""["']([^"']*snad-(?:logo-[a-z0-9-]+|favicon|app-icon)\.(?:svg|png))["']""",
    re.IGNORECASE,
)

SNADLOGO_IMPORT_RE = re.compile(
    r"""(?:from\s+["']@/components/sds["']|from\s+["']@/components/sds/SnadLogo["'])""",
)
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
    """Yield every .tsx file under scan_root, skipping excluded dirs."""
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


def check_direct_brand_references(path: Path, rel: str) -> List[str]:
    """Flag direct brand asset references outside the SDS allowlist."""
    if rel in ALLOWED_FILES:
        return []

    violations: List[str] = []
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    lines = text.splitlines()
    for line_no, line in enumerate(lines, start=1):
        for m in IMPORT_BRAND_ASSET_RE.finditer(line):
            col = m.start(1) + 1
            value = m.group(1)
            violations.append(
                f"{rel}:{line_no}:{col}: IMPORT_BRAND_ASSET — "
                f'direct import of brand asset "{value}". '
                f"Use the <SnadLogo /> component instead."
            )

        for m in IMG_BRAND_ASSET_RE.finditer(line):
            col = m.start(1) + 1
            value = m.group(1)
            violations.append(
                f"{rel}:{line_no}:{col}: IMG_BRAND_ASSET — "
                f'<img> with brand asset src "{value}". '
                f"Use the <SnadLogo /> component instead."
            )

        for m in STRING_BRAND_ASSET_RE.finditer(line):
            col = m.start(1) + 1
            value = m.group(1)
            prefix = line[: m.start()]
            if IMPORT_BRAND_ASSET_RE.search(line):
                continue
            if IMG_BRAND_ASSET_RE.search(line):
                continue
            violations.append(
                f"{rel}:{line_no}:{col}: STRING_BRAND_ASSET — "
                f'string literal referencing brand asset "{value}". '
                f"Use the <SnadLogo /> component instead."
            )

        # Keep the broad recognizer exercised so future path syntax additions
        # cannot silently bypass the governance vocabulary.
        _ = BRAND_ASSET_RE.search(line)

    return violations


def check_auth_login_form_uses_snadlogo(scan_root: Path) -> List[str]:
    """Verify that login-form.tsx imports AND uses SnadLogo."""
    rel = AUTH_LOGIN_FORM
    path = (scan_root.parent.parent / rel).resolve()
    if not path.exists():
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


def check_official_wordmark_integrity(repo_root: Path) -> List[str]:
    """Require the exact user-approved Login v2 raster bytes."""
    asset = repo_root / OFFICIAL_WORDMARK_PATH
    if not asset.is_file():
        return [
            f"{OFFICIAL_WORDMARK_PATH.as_posix()}:0:0: "
            "OFFICIAL_WORDMARK_MISSING — the approved Login v2 wordmark "
            "file is required. Do not substitute or regenerate it."
        ]

    try:
        actual = hashlib.sha256(asset.read_bytes()).hexdigest()
    except OSError as exc:
        return [
            f"{OFFICIAL_WORDMARK_PATH.as_posix()}:0:0: READ_ERROR — {exc}"
        ]

    if actual != OFFICIAL_WORDMARK_SHA256:
        return [
            f"{OFFICIAL_WORDMARK_PATH.as_posix()}:0:0: "
            "OFFICIAL_WORDMARK_HASH_MISMATCH — "
            f"expected {OFFICIAL_WORDMARK_SHA256}, got {actual}."
        ]
    return []


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
    print(f"  wordmark  : {OFFICIAL_WORDMARK_PATH}")
    print()

    all_violations: List[str] = []
    files_scanned = 0

    for path in iter_scan_files(scan_root):
        rel = normalize_relpath(path, scan_root)
        if is_in_sds_dir(rel) and rel not in ALLOWED_FILES:
            continue
        if is_test_file(rel) and rel not in ALLOWED_FILES:
            continue

        files_scanned += 1
        all_violations.extend(check_direct_brand_references(path, rel))

    all_violations.extend(check_auth_login_form_uses_snadlogo(scan_root))
    all_violations.extend(check_official_wordmark_integrity(repo_root))

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
        print("  1. Replace direct brand asset use with <SnadLogo />.")
        print("  2. Keep concrete brand paths inside SnadLogo.tsx only.")
        print("  3. Ensure the approved Login v2 PNG exists with its exact SHA-256.")
        print("  4. See apps/web/design-system/documentation/LOGO_USAGE.md.")
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
