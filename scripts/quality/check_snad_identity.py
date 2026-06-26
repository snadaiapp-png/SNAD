#!/usr/bin/env python3
"""Fail-closed SNAD identity governance checks.

The checker validates the canonical token contract and scans added lines in a
pull request for deprecated brand names or direct color literals. Historical
code remains traceable while every new change is governed automatically.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOKEN_FILE = ROOT / "apps/web/app/snad-tokens.css"

REQUIRED_TOKENS = {
    "--snad-petroleum-700": "#003b39",
    "--snad-gold-500": "#d4af37",
    "--snad-brand-primary": "var(--snad-petroleum-700)",
    "--snad-brand-gold": "var(--snad-gold-500)",
    "--snad-surface-canvas": "var(--snad-ivory)",
    "--snad-text-primary": "var(--snad-neutral-950)",
    "--snad-focus-ring": "var(--snad-petroleum-400)",
}

DEPRECATED_NAME_PATTERNS = (
    re.compile(r"\bSANAD\b"),
    re.compile(r"\bQAWN\b", re.IGNORECASE),
    re.compile(r"قاون"),
)
RAW_COLOR_PATTERN = re.compile(r"#[0-9a-fA-F]{3,8}\b")
DIRECT_COLOR_FUNCTION_PATTERN = re.compile(r"(?<![-\w])(rgb|rgba|hsl|hsla)\(")
FRONTEND_SUFFIXES = {".ts", ".tsx", ".js", ".jsx", ".css", ".scss"}
TOKEN_EXCEPTIONS = {
    Path("apps/web/app/snad-tokens.css"),
    Path("apps/web/app/globals.css"),
    Path("apps/web/app/layout.tsx"),
}
GOVERNANCE_FILE_EXCEPTIONS = {
    Path("scripts/quality/check_snad_identity.py"),
    Path("docs/brand/SNAD-VISUAL-IDENTITY-IMPLEMENTATION.md"),
}
HISTORICAL_EXCEPTIONS = (
    "docs/audit/",
    "docs/execution/",
    "docs/security/",
    ".git/",
)


def fail(message: str) -> None:
    print(f"::error::{message}")


def validate_tokens() -> list[str]:
    errors: list[str] = []
    if not TOKEN_FILE.is_file():
        return [f"Missing canonical token file: {TOKEN_FILE.relative_to(ROOT)}"]

    content = TOKEN_FILE.read_text(encoding="utf-8").lower()
    for token, expected in REQUIRED_TOKENS.items():
        pattern = re.compile(rf"{re.escape(token.lower())}\s*:\s*{re.escape(expected.lower())}\s*;")
        if not pattern.search(content):
            errors.append(f"Token contract violation: {token} must resolve to {expected}")

    if "[data-theme=\"dark\"]" not in content:
        errors.append("Dark theme token mapping is required")
    if "prefers-color-scheme: dark" not in content:
        errors.append("Automatic system dark-mode mapping is required")
    if "prefers-reduced-motion" in content:
        errors.append("Reduced-motion rules belong in globals.css, not the token source")
    return errors


def git_added_lines(base: str) -> list[tuple[Path, int, str]]:
    command = [
        "git",
        "diff",
        "--unified=0",
        "--no-color",
        f"{base}...HEAD",
        "--",
    ]
    completed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or "git diff failed")

    results: list[tuple[Path, int, str]] = []
    current_path: Path | None = None
    new_line = 0
    for line in completed.stdout.splitlines():
        if line.startswith("+++ b/"):
            current_path = Path(line[6:])
            continue
        if line.startswith("@@"):
            match = re.search(r"\+(\d+)(?:,(\d+))?", line)
            if match:
                new_line = int(match.group(1))
            continue
        if current_path is None:
            continue
        if line.startswith("+") and not line.startswith("+++"):
            results.append((current_path, new_line, line[1:]))
            new_line += 1
        elif not line.startswith("-"):
            new_line += 1
    return results


def is_historical(path: Path) -> bool:
    normalized = path.as_posix()
    return any(normalized.startswith(prefix) for prefix in HISTORICAL_EXCEPTIONS)


def validate_added_lines(base: str) -> list[str]:
    errors: list[str] = []
    for path, line_number, line in git_added_lines(base):
        normalized = path.as_posix()
        if is_historical(path):
            continue

        if path not in GOVERNANCE_FILE_EXCEPTIONS:
            for pattern in DEPRECATED_NAME_PATTERNS:
                if pattern.search(line):
                    errors.append(
                        f"{normalized}:{line_number}: deprecated brand name detected; use سند / SNAD"
                    )
                    break

        if path.suffix.lower() in FRONTEND_SUFFIXES and path not in TOKEN_EXCEPTIONS:
            if RAW_COLOR_PATTERN.search(line) or DIRECT_COLOR_FUNCTION_PATTERN.search(line):
                errors.append(
                    f"{normalized}:{line_number}: direct color literal detected; use a SNAD semantic token"
                )
    return errors


def validate_governed_files() -> list[str]:
    required = [
        ROOT / "apps/web/app/globals.css",
        ROOT / ".github/workflows/snad-identity-governance.yml",
        ROOT / "docs/brand/SNAD-VISUAL-IDENTITY-IMPLEMENTATION.md",
    ]
    return [f"Missing governed file: {path.relative_to(ROOT)}" for path in required if not path.is_file()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--diff-base",
        default=None,
        help="Git base ref/SHA used to scan only newly added lines (for example origin/main)",
    )
    args = parser.parse_args()

    errors = validate_tokens() + validate_governed_files()
    if args.diff_base:
        try:
            errors.extend(validate_added_lines(args.diff_base))
        except RuntimeError as exc:
            errors.append(str(exc))

    if errors:
        for error in errors:
            fail(error)
        print(f"SNAD identity governance failed with {len(errors)} violation(s).")
        return 1

    print("SNAD identity governance passed: tokens, themes, names, and new color usage are compliant.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
