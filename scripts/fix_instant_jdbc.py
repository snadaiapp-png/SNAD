#!/usr/bin/env python3
"""
Fix all JdbcTemplate.update/query calls that pass java.time.Instant directly
by wrapping them in java.sql.Timestamp.from().

PostgreSQL JDBC 42.7.x does not reliably bind java.time.Instant to timestamptz
columns via Spring JdbcTemplate varargs update()/query() methods.
"""
import re
from pathlib import Path

INSTANT_VARS = {'now', 'trialEndsAt', 'periodEnd', 'periodStart', 'effectiveAt', 'oneHourAgo'}

def split_top_level_commas(s):
    parts = []
    current = []
    depth = 0
    i = 0
    while i < len(s):
        ch = s[i]
        if ch == '"' or ch == "'":
            quote = ch
            current.append(ch)
            i += 1
            while i < len(s):
                current.append(s[i])
                if s[i] == '\\':
                    i += 1
                    if i < len(s):
                        current.append(s[i])
                    i += 1
                    continue
                if s[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        elif ch == ',' and depth == 0:
            parts.append(''.join(current))
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    if current:
        parts.append(''.join(current))
    return parts

def wrap_arg(arg):
    stripped = arg.strip()
    if stripped.startswith('Timestamp.from('):
        return arg
    if stripped.startswith('"'):
        return arg
    if stripped == 'Instant.now()':
        return arg.replace('Instant.now()', 'Timestamp.from(Instant.now())')
    if stripped in INSTANT_VARS:
        leading = arg[:len(arg) - len(arg.lstrip())]
        trailing = arg[len(arg.rstrip()):]
        return f'{leading}Timestamp.from({stripped}){trailing}'
    m = re.match(r'^(now|trialEndsAt|periodEnd|periodStart|effectiveAt|oneHourAgo)\.(plus|minus)\(', stripped)
    if m:
        leading = arg[:len(arg) - len(arg.lstrip())]
        trailing = arg[len(arg.rstrip()):]
        return f'{leading}Timestamp.from({stripped}){trailing}'
    return arg

def fix_jdbc_calls(content):
    changes = 0
    result = []
    i = 0
    pattern = re.compile(r'(jdbcTemplate|jdbc)\.(update|queryForObject|query)\(')
    while i < len(content):
        m = pattern.search(content, i)
        if not m:
            result.append(content[i:])
            break
        result.append(content[i:m.start()])
        result.append(content[m.start():m.end()])
        pos = m.end()
        depth = 1
        while pos < len(content) and depth > 0:
            ch = content[pos]
            if ch == '"' or ch == "'":
                quote = ch
                pos += 1
                while pos < len(content):
                    if content[pos] == '\\':
                        pos += 2
                        continue
                    if content[pos] == quote:
                        break
                    pos += 1
            elif ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            pos += 1
        args_str = content[m.end():pos-1]
        parts = split_top_level_commas(args_str)
        fixed_parts = [wrap_arg(p) for p in parts]
        fixed_args = ','.join(fixed_parts)
        if fixed_args != args_str:
            changes += 1
        result.append(fixed_args)
        result.append(')')
        i = pos
    return ''.join(result), changes

files = [
    'apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/SaasAdministrationService.java',
    'apps/sanad-platform/src/main/java/com/sanad/platform/health/service/HealthIntelligenceService.java',
]

base = Path('/home/z/my-project')
total = 0
for f in files:
    filepath = base / f
    if not filepath.exists():
        print(f"  SKIP: {f}")
        continue
    original = filepath.read_text()
    fixed, changes = fix_jdbc_calls(original)
    if changes > 0:
        filepath.write_text(fixed)
        print(f"  FIXED: {f} ({changes} calls)")
        total += changes
    else:
        print(f"  OK: {f}")
print(f"\nTotal: {total} calls patched")
