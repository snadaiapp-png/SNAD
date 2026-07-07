#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================================
 SNAD Design System — Compliance Lint
----------------------------------------------------------------------------
 PURPOSE
 -------
 Enforces the SNAD Design System (SDS) rule that no `.tsx`, `.ts`, or `.css`
 file under `apps/web/` may contain hardcoded color values (hex, rgb, rgba,
 hsl, hsla) or hardcoded `font-family` declarations. Every visual value must
 reference a `--snad-*` token.

 The script is:
   • idempotent — running it twice produces identical output
   • dependency-free — only the Python 3 standard library
   • fast — completes in well under 5 seconds on the current codebase
   • strict — exits with code 1 if any violation is found, else 0

 ALLOWLIST
 ---------
 Hardcoded colors are permitted ONLY in these "source-of-truth" files:
   • apps/web/design-system/tokens/theme.css
   • apps/web/design-system/tokens/tokens.json
   • apps/web/app/snad-tokens.css            (legacy — to be migrated)

 Additionally, the following pre-SDS files are allowlisted as "legacy pending
 migration" so the SDS foundation can ship without rewriting them. Each one
 is tracked for a future migration PR:
   • apps/web/app/crm/crm.module.css          (CRM module — pre-SDS palette)

 Hardcoded font-family declarations are allowed ONLY in the source-of-truth
 files above. Everywhere else, font-family must use `var(--snad-font-*)`.

 USAGE
 -----
   python3 scripts/ci/check-design-system-compliance.py [apps/web]

 EXIT CODES
 ----------
   0 — compliant (no violations found, or only in allowlisted files)
   1 — violations found
   2 — usage error / unexpected exception

 REPORT FORMAT
 -------------
 Each violation is printed as:
     <relative/path>:<line>:<col>: <rule_id> — <message>

============================================================================
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Iterable, List, Tuple

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# Default scan root: the Next.js web app.
DEFAULT_SCAN_ROOT = "apps/web"

# File extensions to scan.
SCAN_EXTENSIONS = {".tsx", ".ts", ".css"}

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

# Files that ARE allowed to contain raw color / font-family values.
# These are the canonical "source of truth" files where tokens are defined.
# Use forward-slash paths so the matcher works on every OS.
ALLOWED_FILES = {
    "apps/web/design-system/tokens/theme.css",
    "apps/web/design-system/tokens/tokens.json",
    "apps/web/app/snad-tokens.css",  # legacy compatibility shim
}

# Pre-SDS files that pre-date the design system and are pending migration.
# These are tracked as "legacy" so the SDS foundation can ship without
# rewriting them in this PR. Each one must be migrated in a follow-up.
#
# MIGRATION HISTORY:
#   - 2026-07-07: apps/web/app/crm/crm.module.css migrated to SDS tokens — removed from allowlist.
LEGACY_FILES = {
    # Currently empty — all pre-SDS files have been migrated.
}

# ---------------------------------------------------------------------------
# Regex patterns
# ---------------------------------------------------------------------------

# Hex colors: #RGB, #RGBA, #RRGGBB, #RRGGBBAA (word boundary on the right
# so we don't catch fragments of URL fragments like `#anchor`).
HEX_COLOR_RE = re.compile(r"#(?:[0-9a-fA-F]{3,4}){1,2}\b")

# rgb() / rgba() — supports both comma and space syntax:
#   rgb(255, 255, 255)
#   rgb(255 255 255)
#   rgba(255, 255, 255, 0.5)
#   rgb(255 255 255 / 0.5)
RGB_FUNC_RE = re.compile(
    r"\brgba?\(\s*"
    r"(?:"
    r"[0-9.]+\s*,?\s*[0-9.]+\s*,?\s*[0-9.]+(?:\s*,?\s*[0-9.]+)?"
    r"|"
    r"[0-9.]+%[,\s]+[0-9.]+%[,\s]+[0-9.]+%(?:\s*/\s*[0-9.]+%)?"
    r")"
    r"\s*\)",
    re.IGNORECASE,
)

# hsl() / hsla()
HSL_FUNC_RE = re.compile(
    r"\bhsla?\(\s*"
    r"[0-9.]+(?:deg)?[,\s]+[0-9.]+%[,\s]+[0-9.]+%(?:\s*/\s*[0-9.]+%|\s*,\s*[0-9.]+)?"
    r"\s*\)",
    re.IGNORECASE,
)

# Hardcoded font-family: any `font-family:` declaration whose value does NOT
# contain `var(--snad-font-`. We allow system font fallback lists inside
# var() definitions in the source-of-truth files (handled by ALLOWED_FILES).
# Match the property name + value up to the next semicolon or newline.
FONT_FAMILY_RE = re.compile(
    r"font-family\s*:\s*([^;}\n]+)",
    re.IGNORECASE,
)

# Inline-style color keys in TS/TSX. Catches:
#     color: "#XXXXXX"
#     backgroundColor: "#XXXXXX"
#     style={{ color: "rgb(...)" }}
# We don't ban these outright — we let the hex/rgb/hsl regexes above catch
# the raw values. This pattern is informational only and not used to flag.
INLINE_COLOR_KEY_RE = re.compile(
    r"\b(color|backgroundColor|borderColor|outlineColor|fill|stroke|"
    r"boxShadow|textDecorationColor|caretColor|accentColor)\s*:",
    re.IGNORECASE,
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def normalize_relpath(path: Path, scan_root: Path) -> str:
    """Return a forward-slash path relative to the git repo root (best-effort)."""
    try:
        # Walk up until we find a .git directory.
        repo_root = path
        while repo_root != repo_root.parent:
            if (repo_root / ".git").exists():
                return str(path.relative_to(repo_root).as_posix())
            repo_root = repo_root.parent
    except (ValueError, OSError):
        pass
    # Fallback: path relative to scan_root
    try:
        return str(path.relative_to(scan_root).as_posix())
    except ValueError:
        return str(path.as_posix())


def iter_scan_files(scan_root: Path) -> Iterable[Path]:
    """Yield every file under scan_root that should be scanned."""
    for dirpath, dirnames, filenames in os.walk(scan_root):
        # Prune skipped dirs in-place for os.walk efficiency.
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            p = Path(dirpath) / name
            if p.suffix.lower() in SCAN_EXTENSIONS:
                yield p


def line_col_of(line: str, match_start: int) -> Tuple[int, int]:
    """Return (line_no_offset, col) for a character offset within `line`."""
    # match_start is the index within the line; col is 1-based.
    return (1, match_start + 1)


def check_file(path: Path, scan_root: Path) -> List[str]:
    """Return a list of violation strings for the given file (may be empty)."""
    rel = normalize_relpath(path, scan_root)
    is_allowed = rel in ALLOWED_FILES
    is_legacy = rel in LEGACY_FILES

    # If the file is fully allowlisted, skip it entirely.
    if is_allowed or is_legacy:
        return []

    violations: List[str] = []

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        # If we can't read it, we can't lint it — report and move on.
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    lines = text.splitlines()

    for line_no, line in enumerate(lines, start=1):
        # Skip lines that are obviously comments (CSS / JS / TS).
        stripped = line.lstrip()
        if (
            stripped.startswith("//")
            or stripped.startswith("/*")
            or stripped.startswith("*")
            or stripped.startswith("<!--")
        ):
            # Still scan block-comment lines for accidental hex codes — many
            # developers paste color values into comments and then forget to
            # remove them. But we'll be lenient: only flag hex / rgb / hsl
            # that appear AFTER the comment marker (i.e. on the same line).
            pass

        # ---- Hex colors ------------------------------------------------
        for m in HEX_COLOR_RE.finditer(line):
            col = m.start() + 1
            value = m.group(0)
            # Ignore SHA-like hashes in URL fragments / data: URIs.
            if "data:" in line[: m.start()] and "base64" in line.lower():
                continue
            violations.append(
                f"{rel}:{line_no}:{col}: HEX_COLOR — "
                f'hardcoded hex color "{value}". '
                f"Use a var(--snad-color-*) token instead."
            )

        # ---- rgb() / rgba() --------------------------------------------
        for m in RGB_FUNC_RE.finditer(line):
            col = m.start() + 1
            value = m.group(0)
            violations.append(
                f"{rel}:{line_no}:{col}: RGB_FUNC — "
                f'hardcoded "{value}". '
                f"Use a var(--snad-color-*) token, or "
                f"color-mix(in srgb, var(--snad-color-*) N%, transparent) "
                f"for translucent overlays."
            )

        # ---- hsl() / hsla() --------------------------------------------
        for m in HSL_FUNC_RE.finditer(line):
            col = m.start() + 1
            value = m.group(0)
            violations.append(
                f"{rel}:{line_no}:{col}: HSL_FUNC — "
                f'hardcoded "{value}". '
                f"Use a var(--snad-color-*) token instead."
            )

        # ---- Hardcoded font-family -------------------------------------
        for m in FONT_FAMILY_RE.finditer(line):
            value = m.group(1).strip()
            # Allow if value references a var(--snad-font-*) token.
            if "var(--snad-font-" in value:
                continue
            # Allow `font-family: inherit`, `font-family: initial`, etc.
            if value.lower() in {"inherit", "initial", "unset", "revert"}:
                continue
            col = m.start(1) + 1
            violations.append(
                f"{rel}:{line_no}:{col}: FONT_FAMILY — "
                f'hardcoded font-family "{value}". '
                f"Use var(--snad-font-body) / var(--snad-font-arabic) / "
                f"var(--snad-font-latin) / var(--snad-font-numeric)."
            )

    return violations


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main(argv: List[str]) -> int:
    scan_root_arg = argv[1] if len(argv) > 1 else DEFAULT_SCAN_ROOT

    # Resolve scan_root relative to the git repo root (best-effort).
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

    print(f"SNAD Design System compliance check")
    print(f"  scan root : {scan_root}")
    print(f"  allowed   : {len(ALLOWED_FILES)} source-of-truth files")
    print(f"  legacy    : {len(LEGACY_FILES)} files pending migration")
    print()

    all_violations: List[str] = []
    files_scanned = 0

    for path in iter_scan_files(scan_root):
        files_scanned += 1
        all_violations.extend(check_file(path, scan_root))

    # Always print the legacy-file tracker so reviewers see what's deferred.
    if LEGACY_FILES:
        print("Legacy files (allowlisted, pending SDS migration):")
        for f, reason in sorted(LEGACY_FILES.items()):
            print(f"  • {f}")
            print(f"      reason: {reason}")
        print()

    if all_violations:
        print(f"FAIL — {len(all_violations)} violation(s) found in "
              f"{files_scanned} file(s) scanned:")
        print()
        for v in all_violations:
            print(f"  {v}")
        print()
        print("To fix:")
        print("  1. Replace hardcoded hex / rgb / rgba / hsl values with "
              "var(--snad-color-*) tokens.")
        print("  2. Replace hardcoded font-family declarations with "
              "var(--snad-font-*).")
        print("  3. For translucent overlays, use:")
        print("       color-mix(in srgb, var(--snad-color-brand-primary) "
              "N%, transparent)")
        print("  4. See apps/web/design-system/documentation/DESIGN_TOKENS.md"
              " for the full token reference.")
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
