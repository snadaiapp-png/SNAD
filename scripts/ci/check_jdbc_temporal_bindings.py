#!/usr/bin/env python3
"""Fail CI when java.time temporal values are bound unsafely to PostgreSQL JDBC.

The PostgreSQL JDBC driver does not infer a SQL type for java.time.Instant when
passed through JdbcTemplate/NamedParameterJdbcTemplate generic argument binding.
This scanner is repository-wide and module-agnostic so new modules inherit the
same safety contract.

Safe patterns include:
- Timestamp.from(instant)
- OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
- PreparedStatement#setObject(..., value, Types.TIMESTAMP_WITH_TIMEZONE)
- PreparedStatement#setTimestamp(..., Timestamp.from(value))
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

JAVA_ROOT = Path("apps/sanad-platform/src/main/java")
TEMPORAL_TYPES = ("Instant", "OffsetDateTime", "LocalDateTime", "ZonedDateTime")

CALL_PREFIXES = (
    ".update(",
    ".query(",
    ".queryForObject(",
    ".queryForList(",
    ".batchUpdate(",
    ".addValue(",
    ".setObject(",
)

SAFE_WRAPPERS = (
    "Timestamp.from(",
    "java.sql.Timestamp.from(",
    "OffsetDateTime.ofInstant(",
    "java.time.OffsetDateTime.ofInstant(",
    "toSqlTimestamp(",
    "toOffsetDateTime(",
)

DECL_RE = re.compile(
    r"\b(?:final\s+)?(?:java\.time\.)?"
    r"(?:Instant|OffsetDateTime|LocalDateTime|ZonedDateTime)\s+([A-Za-z_$][\w$]*)\b"
)


def _balanced_call(text: str, open_paren: int) -> tuple[str, int] | None:
    depth = 0
    quote: str | None = None
    escaped = False
    i = open_paren
    while i < len(text):
        ch = text[i]
        if quote is not None:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            i += 1
            continue
        if ch in ('"', "'"):
            quote = ch
            i += 1
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren + 1:i], i + 1
        i += 1
    return None


def _split_top_level(arguments: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    escaped = False
    for i, ch in enumerate(arguments):
        if quote is not None:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append(arguments[start:i].strip())
            start = i + 1
    parts.append(arguments[start:].strip())
    return parts


def _is_safe_expression(expr: str) -> bool:
    return any(wrapper in expr for wrapper in SAFE_WRAPPERS)


def scan_source(path: Path, text: str) -> list[str]:
    temporal_vars = set(DECL_RE.findall(text))
    findings: list[str] = []

    # Direct Instant.now()/etc generic binding is always unsafe.
    direct_temporal_factory = re.compile(
        r"\b(?:Instant|OffsetDateTime|LocalDateTime|ZonedDateTime)\.now\s*\("
    )

    idx = 0
    while idx < len(text):
        hits = [(text.find(prefix, idx), prefix) for prefix in CALL_PREFIXES]
        hits = [(pos, prefix) for pos, prefix in hits if pos >= 0]
        if not hits:
            break
        pos, prefix = min(hits, key=lambda item: item[0])
        open_paren = pos + len(prefix) - 1
        parsed = _balanced_call(text, open_paren)
        if parsed is None:
            break
        args_text, end = parsed
        args = _split_top_level(args_text)

        is_set_object = prefix == ".setObject("
        has_explicit_sql_type = is_set_object and len(args) >= 3

        for arg_index, arg in enumerate(args):
            if _is_safe_expression(arg):
                continue
            if direct_temporal_factory.search(arg):
                if not has_explicit_sql_type:
                    line = text.count("\n", 0, pos) + 1
                    findings.append(
                        f"{path}:{line}: unsafe temporal JDBC binding in {prefix[:-1]} arg {arg_index + 1}: {arg[:140]}"
                    )
                continue

            for var in temporal_vars:
                if re.search(rf"\b{re.escape(var)}\b", arg):
                    if has_explicit_sql_type:
                        continue
                    # Read-side mapper lambdas may reference a temporal variable name
                    # coincidentally; only flag argument expressions, not SQL text.
                    if arg.startswith('"') or arg.startswith('"""'):
                        continue
                    line = text.count("\n", 0, pos) + 1
                    findings.append(
                        f"{path}:{line}: unsafe {var} JDBC binding in {prefix[:-1]} arg {arg_index + 1}: {arg[:140]}"
                    )
                    break
        idx = end

    return findings


def scan_tree(root: Path) -> list[str]:
    findings: list[str] = []
    if not root.exists():
        return [f"{root}: source root does not exist"]
    for path in sorted(root.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        findings.extend(scan_source(path, text))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=JAVA_ROOT)
    args = parser.parse_args()

    findings = scan_tree(args.root)
    if findings:
        print("UNSAFE_JDBC_TEMPORAL_BINDINGS = FOUND")
        for finding in findings:
            print(finding)
        return 1

    print("UNSAFE_JDBC_TEMPORAL_BINDINGS = 0")
    print("JDBC_TEMPORAL_BINDING_GUARD = PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
