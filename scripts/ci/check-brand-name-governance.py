#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
============================================================================
 SNAD Brand Name Governance — CI Lint
----------------------------------------------------------------------------
 PURPOSE
 -------
 Enforces the executive order that the official brand name is "SNAD" (Latin)
 or "سند" (Arabic). The variants `SANAD`, `Sanad`, `Snad`, and `sanad` are
 forbidden everywhere except in clearly-defined exception contexts.

 RULES
 -----
 A forbidden variant is any case-insensitive 5-letter spelling "sanad"
 OR the 4-letter camelCase "Snad" (the lowercase/camel variant of the
 correct "SNAD"). Allowed spellings:
   • SNAD     — official Latin form (4 letters, all caps)
   • سند      — official Arabic form

 Forbidden spellings (and the contexts that allow them):
   • SANAD    — allowed only in: comments, URLs, email addresses,
                env var names (SANAD_*), and file paths.
   • Sanad    — same exceptions.
   • Snad     — same exceptions. (Forbidden because the correct camelCase
                of SNAD is "SnadLogo", which is itself a single identifier
                and is therefore not matched by the word-boundary regex.)
   • sanad    — same exceptions. (Lowercase 5-letter variant.)

 EXCEPTION CONTEXTS
 ------------------
 A forbidden match is ALLOWED when any of the following is true:
   1. The match appears inside a comment (//, /* */, #, <!-- -->, *).
   2. The match appears inside a URL (https?://, ftp://, ws://).
   3. The match appears inside an email address (local@domain.tld).
   4. The match is part of an env var name (e.g. SANAD_ADMIN_EMAIL,
      SANAD_PASSWORD, SANAD_*).
   5. The match is part of a file path (preceded by / or \ and followed
      by / or \, OR part of a known path segment like "sanad-platform").
   6. The match appears inside inline code in Markdown (single backticks)
      or a fenced code block (```...```).
   7. The match appears in the allowlist (see ALLOWED_FILES).

 USAGE
 -----
   python3 scripts/ci/check-brand-name-governance.py [apps/web]

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
from typing import Iterable, List

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DEFAULT_SCAN_ROOT = "apps/web"

# File extensions to scan.
SCAN_EXTENSIONS = {".tsx", ".ts", ".css", ".md"}

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
    "SNAD-https",  # backup snapshot directory — out of scope
}

# Files explicitly allowed to mention forbidden variants (e.g. this script
# itself, the PR template that documents the rule, the brand governance
# docs).
ALLOWED_FILES = {
    "scripts/ci/check-brand-name-governance.py",
    "scripts/ci/check-logo-governance.py",
    "apps/web/design-system/documentation/BRAND_GOVERNANCE.md",
    "apps/web/design-system/documentation/BRAND_CHANGE_PROCESS.md",
    "apps/web/design-system/documentation/AUTH_UI_GUIDE.md",
    "apps/web/design-system/documentation/AUTH_PERFORMANCE.md",
    "apps/web/design-system/documentation/AUTH_STATE_MACHINE.md",
    "apps/web/design-system/documentation/EXECUTIVE_SHELL_GUIDE.md",
    "apps/web/design-system/documentation/WORKSPACE_BOOTSTRAP.md",
    "apps/web/design-system/documentation/LOGO_USAGE.md",
    "apps/web/design-system/documentation/DESIGN_SYSTEM.md",
    "apps/web/design-system/documentation/COLOR_SYSTEM.md",
    "apps/web/design-system/documentation/COMPONENT_USAGE.md",
    ".github/PULL_REQUEST_TEMPLATE.md",
}

# ---------------------------------------------------------------------------
# Regex patterns
# ---------------------------------------------------------------------------

# Match any of the forbidden variants. We use \b (word boundary) so that
# `SnadLogo` (single identifier) does NOT match `\bSnad\b`.
FORBIDDEN_RE = re.compile(r"\b(?:SANAD|Sanad|Snad|sanad)\b")

# Match the correct forms — used to skip past them in scan output. Not
# strictly necessary, but documents the allowed spellings.
CORRECT_RE = re.compile(r"\bSNAD\b|سند")

# Match a URL scheme. We allow any forbidden variant that appears inside
# a URL.
URL_RE = re.compile(r"\b(?:https?|ftp|ws|wss)://[^\s'\"<>]+", re.IGNORECASE)

# Match an email address.
EMAIL_RE = re.compile(r"\b[\w.+-]+@[\w-]+(?:\.[\w-]+)+\b")

# Match an env var name (UPPER_SNAKE_CASE starting with SANAD, Sanad, etc.).
# We allow SANAD_*, Sanad_*, etc. as env var names. The trailing portion may
# be lowercase (snake_case) or uppercase (SCREAMING_SNAKE_CASE).
ENV_VAR_RE = re.compile(
    r"\b(?:SANAD|Sanad|Snad|sanad)_[A-Za-z0-9_]+\b"
)

# Match a file path containing "sanad" (case-insensitive). We use a
# liberal pattern: any occurrence of /sanad/ or \sanad\ or /sanad- or
# similar. This catches apps/sanad-platform/, com/sanad/platform/, etc.
PATH_RE = re.compile(
    r"(?:[/\\])(?:sanad|Sanad|SANAD)(?=[/\\.-])",
    re.IGNORECASE,
)

# Match a hyphenated identifier containing a forbidden variant — e.g.
# `x-sanad-refresh-token`, `sanad-backend`, `sanad-backend-mcrj`. These
# are technical identifiers (HTTP header names, hostnames, package names),
# NOT brand displays, so they are allowed.
HYPHEN_IDENT_RE = re.compile(
    r"\b[\w]+-(?:SANAD|Sanad|Snad|sanad)(?:-[\w]+)*\b"
    r"|"
    r"\b(?:SANAD|Sanad|Snad|sanad)-[\w]+(?:-[\w]+)*\b",
    re.IGNORECASE,
)

# Match a domain name containing a forbidden variant — e.g.
# `sanad-backend-mcrj.onrender.com`, `sanad.app`. These are hostnames,
# not brand displays, so they are allowed.
DOMAIN_RE = re.compile(
    r"\b(?:[\w-]+\.)+[\w-]+\b",
    re.IGNORECASE,
)

# Match a comment marker at the start of a (stripped) line.
COMMENT_LINE_RE = re.compile(r"^\s*(?://|/|\*|#|<!--|-->|\*)")

# Match fenced code block delimiters in Markdown.
FENCED_CODE_OPEN_RE = re.compile(r"^\s*```")
FENCED_CODE_CLOSE_RE = re.compile(r"^\s*```")

# Match inline code in Markdown (single backticks).
INLINE_CODE_RE = re.compile(r"`[^`\n]*`")

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
    """Yield every file under scan_root whose extension is in SCAN_EXTENSIONS."""
    for dirpath, dirnames, filenames in os.walk(scan_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            p = Path(dirpath) / name
            if p.suffix.lower() in SCAN_EXTENSIONS:
                yield p


def strip_inline_code(line: str) -> str:
    """Replace inline code spans (backticks) with placeholder spaces so the
    forbidden-variant scanner does not flag text inside inline code in
    Markdown. The placeholder preserves column offsets for reporting.
    """
    def replace(m: re.Match) -> str:
        return " " * len(m.group(0))

    return INLINE_CODE_RE.sub(replace, line)


def find_url_email_path_spans(line: str) -> List[tuple]:
    """Return a list of (start, end) spans covering URLs, emails, env vars,
    file paths, hyphenated identifiers, and domain names in `line`.
    Forbidden matches inside these spans are allowed."""
    spans: List[tuple] = []
    for pattern in (
        URL_RE,
        EMAIL_RE,
        ENV_VAR_RE,
        PATH_RE,
        HYPHEN_IDENT_RE,
        DOMAIN_RE,
    ):
        for m in pattern.finditer(line):
            spans.append((m.start(), m.end()))
    return spans


def is_in_forbidden_span(pos: int, spans: List[tuple]) -> bool:
    for start, end in spans:
        if start <= pos < end:
            return True
    return False


def check_file(path: Path, scan_root: Path) -> List[str]:
    rel = normalize_relpath(path, scan_root)
    if rel in ALLOWED_FILES:
        return []

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return [f"{rel}:0:0: READ_ERROR — {exc}"]

    violations: List[str] = []
    lines = text.splitlines()
    in_fenced_code = False
    in_block_comment = False

    for line_no, line in enumerate(lines, start=1):
        stripped = line.lstrip()

        # Track fenced code blocks in Markdown.
        if path.suffix.lower() == ".md":
            if FENCED_CODE_OPEN_RE.match(line):
                # Toggle fence state. A closing fence matches the same regex.
                in_fenced_code = not in_fenced_code
                continue
            if in_fenced_code:
                # Forbidden variants inside fenced code blocks are allowed.
                continue

        # Track block comments in CSS/TS/TSX.
        if "/*" in stripped and "*/" not in stripped:
            in_block_comment = True
        # If the line opens AND closes a block comment on the same line,
        # we treat the entire line as a comment for the rest of this pass.

        # Determine if this line is a comment-only line.
        is_comment_line = bool(COMMENT_LINE_RE.match(line))
        if in_block_comment:
            is_comment_line = True

        # Close block comment if applicable.
        if in_block_comment and "*/" in stripped:
            in_block_comment = False

        # Replace inline code in Markdown so the scanner doesn't flag
        # backtick-quoted forbidden variants.
        scan_line = strip_inline_code(line) if path.suffix.lower() == ".md" else line

        # Find URL/email/env-var/path spans to allow forbidden matches inside.
        allowed_spans = find_url_email_path_spans(scan_line)

        # Find all forbidden matches on this line.
        for m in FORBIDDEN_RE.finditer(scan_line):
            col = m.start() + 1
            value = m.group(0)

            # Exception 1: comment-only line.
            if is_comment_line:
                continue

            # Exception 2: inside a URL / email / env var / file path.
            if is_in_forbidden_span(m.start(), allowed_spans):
                continue

            violations.append(
                f"{rel}:{line_no}:{col}: FORBIDDEN_BRAND_NAME — "
                f'incorrect brand name "{value}". '
                f'Use "SNAD" (Latin) or "سند" (Arabic) instead.'
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

    print("SNAD Brand Name Governance check")
    print(f"  scan root : {scan_root}")
    print(f"  allowed   : {len(ALLOWED_FILES)} documentation/script files")
    print(f"  correct   : SNAD (Latin), سند (Arabic)")
    print(f"  forbidden : SANAD, Sanad, Snad, sanad (except in comments, "
          f"URLs, emails, env vars, file paths, code blocks)")
    print()

    all_violations: List[str] = []
    files_scanned = 0

    for path in iter_scan_files(scan_root):
        files_scanned += 1
        all_violations.extend(check_file(path, scan_root))

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
        print('  1. Replace any "SANAD", "Sanad", "Snad", or "sanad" with')
        print('     "SNAD" (Latin) or "سند" (Arabic).')
        print("  2. If the variant appears in a comment, URL, email, env var,")
        print("     or file path, it is allowed — no action needed.")
        print("  3. If the variant appears in inline code or a fenced code")
        print("     block in Markdown, it is allowed — no action needed.")
        print("  4. See apps/web/design-system/documentation/BRAND_GOVERNANCE.md")
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
