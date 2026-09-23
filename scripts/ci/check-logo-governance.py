#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SNAD logo-governance CI lint.

`SnadLogo` is the only application component allowed to reference governed
brand artwork directly. Authentication and product surfaces consume the SDS
component instead of raw SVG/PNG paths.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path
from typing import Iterable, List

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

# Govern both the legacy/canonical SVG set and approved raster derivatives.
# Keep the filename family intentionally narrow so unrelated product imagery is
# not pulled into logo governance.
BRAND_ASSET_NAME = r"snad-(?:logo-[a-z0-9-]+|favicon|app-icon)\.(?:svg|png)"
BRAND_ASSET_PATH_RE = re.compile(
    rf"[/\\]assets[/\\]brand[/\\]{BRAND_ASSET_NAME}",
    re.IGNORECASE,
)
IMPORT_BRAND_ASSET_RE = re.compile(
    rf"""import\s+[^;]*?from\s+["']([^"']*{BRAND_ASSET_NAME})["']""",
    re.IGNORECASE,
)
IMG_BRAND_ASSET_RE = re.compile(
    rf"""<img\b[^>]*\bsrc\s*=\s*["']([^"']*{BRAND_ASSET_NAME})["']""",
    re.IGNORECASE,
)
STRING_BRAND_ASSET_RE = re.compile(
    rf"""["']([^"']*{BRAND_ASSET_NAME})["']""",
    re.IGNORECASE,
)
SNADLOGO_IMPORT_RE = re.compile(
    r"""(?:from\s+["']@/components/sds["']|from\s+["']@/components/sds/SnadLogo["'])""",
)
SNADLOGO_USAGE_RE = re.compile(r"<SnadLogo\b")


def normalize_relpath(path: Path, scan_root: Path) -> str:
    """Return a forward-slash path relative to the git repository root."""
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
    for dirpath, dirnames, filenames in os.walk(scan_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            path = Path(dirpath) / name
            if path.suffix.lower() in SCAN_EXTENSIONS:
                yield path


def is_in_sds_dir(rel_path: str) -> bool:
    return rel_path.startswith(f"{SDS_DIR}/")


def is_test_file(rel_path: str) -> bool:
    parts = rel_path.split("/")
    return "__tests__" in parts or rel_path.endswith(".test.tsx")


def check_direct_brand_references(path: Path, rel: str) -> List[str]:
    """Flag direct governed-logo asset references outside the allowlist."""
    if rel in ALLOWED_FILES:
        return []

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    violations: List[str] = []
    for line_no, line in enumerate(text.splitlines(), start=1):
        # Imports are reported with a specific rule.
        import_spans: list[tuple[int, int]] = []
        for match in IMPORT_BRAND_ASSET_RE.finditer(line):
            import_spans.append(match.span())
            value = match.group(1)
            violations.append(
                f"{rel}:{line_no}:{match.start(1) + 1}: IMPORT_BRAND_ASSET — "
                f'direct import of governed brand asset "{value}". '
                "Use the <SnadLogo /> component instead."
            )

        # Raw <img src> references are reported with a specific rule.
        img_spans: list[tuple[int, int]] = []
        for match in IMG_BRAND_ASSET_RE.finditer(line):
            img_spans.append(match.span())
            value = match.group(1)
            violations.append(
                f"{rel}:{line_no}:{match.start(1) + 1}: IMG_BRAND_ASSET — "
                f'<img> with governed brand asset src "{value}". '
                "Use the <SnadLogo /> component instead."
            )

        # Catch any remaining string literal references without double-reporting
        # a value already covered by a more specific rule above.
        for match in STRING_BRAND_ASSET_RE.finditer(line):
            span = match.span()
            if any(start <= span[0] and span[1] <= end for start, end in import_spans):
                continue
            if any(start <= span[0] and span[1] <= end for start, end in img_spans):
                continue
            value = match.group(1)
            violations.append(
                f"{rel}:{line_no}:{match.start(1) + 1}: STRING_BRAND_ASSET — "
                f'string literal referencing governed brand asset "{value}". '
                "Use the <SnadLogo /> component instead."
            )

    return violations


def check_auth_login_form_uses_snadlogo(scan_root: Path) -> List[str]:
    """Verify that the canonical login form imports and renders SnadLogo."""
    rel = AUTH_LOGIN_FORM
    path = (scan_root.parent.parent / rel).resolve()
    if not path.exists():
        path = (Path.cwd() / rel).resolve()
    if not path.exists():
        return [
            f"{rel}:0:0: AUTH_FORM_MISSING — login form file not found at expected path."
        ]

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    violations: List[str] = []
    if not SNADLOGO_IMPORT_RE.search(text):
        violations.append(
            f"{rel}:0:0: AUTH_FORM_NO_SNADLOGO_IMPORT — "
            "login form must import SnadLogo from @/components/sds."
        )
    if not SNADLOGO_USAGE_RE.search(text):
        violations.append(
            f"{rel}:0:0: AUTH_FORM_NO_SNADLOGO_USAGE — "
            "login form must render <SnadLogo /> for the brand mark."
        )
    return violations


def main(argv: List[str]) -> int:
    scan_root_arg = argv[1] if len(argv) > 1 else DEFAULT_SCAN_ROOT

    repo_root = Path.cwd()
    while repo_root != repo_root.parent:
        if (repo_root / ".git").exists():
            break
        repo_root = repo_root.parent

    scan_root = (repo_root / scan_root_arg).resolve()
    if not scan_root.exists():
        print(f"ERROR: scan root does not exist: {scan_root}", file=sys.stderr)
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
        if is_in_sds_dir(rel) and rel not in ALLOWED_FILES:
            continue
        if is_test_file(rel) and rel not in ALLOWED_FILES:
            continue
        files_scanned += 1
        all_violations.extend(check_direct_brand_references(path, rel))

    all_violations.extend(check_auth_login_form_uses_snadlogo(scan_root))

    if all_violations:
        print(
            f"FAIL — {len(all_violations)} violation(s) found in "
            f"{files_scanned} file(s) scanned:"
        )
        print()
        for violation in all_violations:
            print(f"  {violation}")
        print()
        print("To fix:")
        print("  1. Replace direct governed brand-asset references with <SnadLogo />.")
        print('  2. Import SnadLogo from "@/components/sds".')
        print("  3. Keep brand asset paths centralized in SnadLogo.tsx.")
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
