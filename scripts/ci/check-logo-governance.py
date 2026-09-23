#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SNAD logo governance CI check.

All application surfaces must render brand artwork through the centralized
``SnadLogo`` SDS component. The component and its focused tests are the only
code locations allowed to reference concrete files in ``/assets/brand``
directly. Both SVG and PNG assets are governed. The Login v2 official
wordmark is additionally pinned by SHA-256 so a missing, regenerated, or
modified raster can never pass the brand gate.
"""

from __future__ import annotations

import hashlib
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
OFFICIAL_WORDMARK_PATH = Path(
    "apps/web/public/assets/brand/snad-logo-official-wordmark.png"
)
OFFICIAL_WORDMARK_SHA256 = (
    "98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251"
)

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


def normalize_relpath(path: Path, scan_root: Path) -> str:
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
            p = Path(dirpath) / name
            if p.suffix.lower() in SCAN_EXTENSIONS:
                yield p


def is_in_sds_dir(rel_path: str) -> bool:
    return rel_path.startswith(f"{SDS_DIR}/")


def is_test_file(rel_path: str) -> bool:
    parts = rel_path.split("/")
    return "__tests__" in parts or rel_path.endswith(".test.tsx")


def check_direct_brand_references(path: Path, rel: str) -> List[str]:
    if rel in ALLOWED_FILES:
        return []

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    violations: List[str] = []
    for line_no, line in enumerate(text.splitlines(), start=1):
        matches = list(BRAND_ASSET_RE.finditer(line))
        for match in matches:
            value = match.group(0)
            col = match.start() + 1
            if IMPORT_BRAND_ASSET_RE.search(line):
                rule = "IMPORT_BRAND_ASSET"
            elif IMG_BRAND_ASSET_RE.search(line):
                rule = "IMG_BRAND_ASSET"
            elif STRING_BRAND_ASSET_RE.search(line):
                rule = "STRING_BRAND_ASSET"
            else:
                rule = "BRAND_ASSET_REFERENCE"
            violations.append(
                f"{rel}:{line_no}:{col}: {rule} — direct brand asset reference "
                f'"{value}". Use the <SnadLogo /> component instead.'
            )
    return violations


def check_auth_login_form_uses_snadlogo(scan_root: Path) -> List[str]:
    repo_root = scan_root.parent.parent
    path = (repo_root / AUTH_LOGIN_FORM).resolve()
    if not path.exists():
        path = (Path.cwd() / AUTH_LOGIN_FORM).resolve()
    if not path.exists():
        return [
            f"{AUTH_LOGIN_FORM}:0:0: AUTH_FORM_MISSING — login form file not found."
        ]

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{AUTH_LOGIN_FORM}:0:0: READ_ERROR — {exc}"]

    violations: List[str] = []
    if not SNADLOGO_IMPORT_RE.search(text):
        violations.append(
            f"{AUTH_LOGIN_FORM}:0:0: AUTH_FORM_NO_SNADLOGO_IMPORT — "
            "login form must import SnadLogo from @/components/sds."
        )
    if not SNADLOGO_USAGE_RE.search(text):
        violations.append(
            f"{AUTH_LOGIN_FORM}:0:0: AUTH_FORM_NO_SNADLOGO_USAGE — "
            "login form must render <SnadLogo /> for the brand mark."
        )
    return violations


def check_official_wordmark_integrity(repo_root: Path) -> List[str]:
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
        print(f"ERROR: scan root does not exist: {scan_root}", file=sys.stderr)
        return 2

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

    print("SNAD Logo Governance check")
    if all_violations:
        print(f"FAIL — {len(all_violations)} violation(s) in {files_scanned} file(s):")
        for violation in all_violations:
            print(f"  {violation}")
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
