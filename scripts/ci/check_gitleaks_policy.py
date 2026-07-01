#!/usr/bin/env python3
"""
Stage 04A.3.6.2 — Gitleaks Policy Gate.

Fails CI if `.gitleaks.toml` contains BROAD path exclusions that mask
real secret-detection coverage. The ONLY paths allowed in the allowlist
are build artifacts produced by the build tooling itself.

Forbidden broad exclusions (any of these in the allowlist.paths array
fails the gate):
    .github/workflows
    src/test
    src/main
    application.yml
    application-prod.yml
    docker-compose
    render.yaml
    docs

Usage:
    python3 scripts/ci/check_gitleaks_policy.py [.gitleaks.toml]

Exit codes:
    0 — policy satisfied (no broad exclusions detected)
    1 — policy violated (broad exclusion detected)
    2 — unexpected error (file missing, parse failure)
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# Patterns that, if present anywhere in any allowlist path entry, indicate
# a broad exclusion that masks real secret-detection coverage. We match
# by substring (case-sensitive) to catch both regex and plain-string forms.
FORBIDDEN_PATTERNS = [
    ".github/workflows",
    "src/test",
    "src/main",
    "application.yml",
    "application-prod.yml",
    "application-prod\\.yml",
    "application\\.yml",
    "docker-compose",
    "render.yaml",
    "render\\.yaml",
    "docs/",
    "docs\\",
    # Wildcards that exclude entire directory trees.
    "^docs",
    "^src",
    "^\\.github",
]

# Path prefixes that ARE allowed (build artifacts only).
ALLOWED_PATH_PATTERNS = [
    re.compile(r"^apps/sanad-platform/target/"),
    re.compile(r"^apps/web/\.next/"),
]


def parse_allowlist_paths(toml_text: str) -> list[str]:
    """
    Naive TOML parser that extracts the `paths` array from the `[allowlist]`
    table. We avoid a full TOML dependency to keep the gate self-contained.
    """
    paths: list[str] = []
    in_allowlist = False
    in_paths_array = False
    for raw_line in toml_text.splitlines():
        line = raw_line.strip()
        # Table header detection.
        if line.startswith("[") and line.endswith("]"):
            in_allowlist = line.lower().startswith("[allowlist")
            in_paths_array = False
            continue
        if not in_allowlist:
            continue
        # paths = [ ... ] may be on one line or split across multiple lines.
        if "paths" in line and "=" in line and "[" in line:
            in_paths_array = True
            # Extract everything after the first [
            after_eq = line.split("=", 1)[1]
            after_bracket = after_eq.split("[", 1)[1] if "[" in after_eq else ""
            entries = _extract_path_entries(after_bracket)
            for entry in entries:
                paths.append(entry)
                if after_bracket.rstrip().endswith("]"):
                    in_paths_array = False
            continue
        if in_paths_array:
            entries = _extract_path_entries(line)
            for entry in entries:
                paths.append(entry)
            if line.rstrip().endswith("]"):
                in_paths_array = False
    return paths


def _extract_path_entries(text: str) -> list[str]:
    """
    Extract individual path string entries from a fragment of a TOML paths
    array. Entries are triple-quoted ('''...''') or single-quoted ('...')
    or double-quoted ("..."). We strip quotes and return the inner text.

    Triple-quoted strings are matched first, and their spans are masked
    before single/double-quoted patterns run, so we don't double-count
    the same entry.
    """
    entries: list[str] = []
    masked = list(text)
    triple_pattern = re.compile(r"'''([^']*)'''")
    double_pattern = re.compile(r'"([^"]*)"')
    single_pattern = re.compile(r"'([^']*)'")

    # Pass 1: triple-quoted strings.
    for match in triple_pattern.finditer(text):
        inner = match.group(1).strip()
        if inner:
            entries.append(inner)
        # Mask the matched span so single-pattern doesn't re-match it.
        for i in range(match.start(), match.end()):
            masked[i] = " "

    masked_text = "".join(masked)

    # Pass 2: double-quoted strings (rare in TOML paths, but supported).
    for match in double_pattern.finditer(masked_text):
        inner = match.group(1).strip()
        if inner:
            entries.append(inner)

    # Pass 3: single-quoted strings.
    for match in single_pattern.finditer(masked_text):
        inner = match.group(1).strip()
        if inner:
            entries.append(inner)

    return entries


def normalize_path_entry(entry: str) -> str:
    """
    Normalize a TOML path entry by stripping regex anchors and escaping
    so we can do substring matching against the forbidden patterns.
    """
    # Strip leading/trailing regex anchors.
    s = entry
    if s.startswith("^"):
        s = s[1:]
    if s.endswith("$") and not s.endswith("\\$"):
        s = s[:-1]
    # Unescape backslashes for substring matching.
    s = s.replace("\\.", ".")
    s = s.replace("\\-", "-")
    s = s.replace("\\_", "_")
    s = s.replace("\\\\", "\\")
    return s


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        config_path = Path(".gitleaks.toml")
    else:
        config_path = Path(argv[1])

    if not config_path.exists():
        print(f"FAIL: gitleaks config not found: {config_path}", file=sys.stderr)
        return 2

    toml_text = config_path.read_text(encoding="utf-8")
    paths = parse_allowlist_paths(toml_text)

    if not paths:
        print("WARN: no allowlist.paths entries found — nothing to check")
        return 0

    print(f"Gitleaks allowlist paths ({len(paths)} entries):")
    for p in paths:
        print(f"  - {p}")

    violations: list[tuple[str, str]] = []
    for raw_entry in paths:
        normalized = normalize_path_entry(raw_entry)
        for forbidden in FORBIDDEN_PATTERNS:
            # Skip patterns that are themselves regex escapes (e.g. "application\\.yml")
            # — we already normalized them above, so match the literal form.
            forbidden_normalized = forbidden.replace("\\.", ".").replace("\\-", "-")
            if forbidden_normalized in normalized:
                violations.append((raw_entry, forbidden))
                break

    # Check that every path entry matches one of the ALLOWED_PATH_PATTERNS.
    # If an entry doesn't match any allowed pattern, it's a violation.
    for raw_entry in paths:
        normalized = normalize_path_entry(raw_entry)
        is_allowed = any(p.search(normalized) or p.search(raw_entry) for p in ALLOWED_PATH_PATTERNS)
        if not is_allowed:
            # Check if it's already flagged as a forbidden pattern.
            already_flagged = any(
                forbidden.replace("\\.", ".").replace("\\-", "-") in normalize_path_entry(raw_entry)
                for forbidden in FORBIDDEN_PATTERNS
            )
            if not already_flagged:
                violations.append((raw_entry, "<not in allowed build-artifact patterns>"))

    if violations:
        print()
        print(f"FAIL: gitleaks policy violated — {len(violations)} broad exclusion(s) detected:")
        for entry, forbidden in violations:
            print(f"  - path entry: {entry!r}")
            print(f"    matches forbidden pattern: {forbidden!r}")
        print()
        print("Allowed path entries are limited to build artifacts:")
        for p in ALLOWED_PATH_PATTERNS:
            print(f"  - {p.pattern}")
        return 1

    print()
    print("GITLEAKS POLICY: PASS — no broad path exclusions detected")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
