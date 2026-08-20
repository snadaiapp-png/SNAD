#!/usr/bin/env python3
"""
SANAD Architecture Protection — Frontend Bounded Context Boundary Validator
============================================================================

Enforces Architecture Protection Policy §4.1 (frontend import rules) on the
Next.js frontend (`apps/web/`).

Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md §4.1, §5.2
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
WEB_ROOT = REPO_ROOT / "apps" / "web"

CONTEXT_PATH_PATTERNS = {
    "executive":    [re.compile(r"^app/executive/"), re.compile(r"^lib/api/executive-"),
                    re.compile(r"^lib/routes/executive-"), re.compile(r"^lib/navigation/executive-"),
                    re.compile(r"^lib/modules/executive-")],
    "system-health": [re.compile(r"^app/system-health/"), re.compile(r"^lib/api/system-health-"),
                      re.compile(r"^lib/routes/system-health-"), re.compile(r"^lib/navigation/system-health-"),
                      re.compile(r"^lib/modules/system-health-")],
    "crm":          [re.compile(r"^app/crm/"), re.compile(r"^lib/api/crm-")],
}

FORBIDDEN_MATRIX = {
    "executive":    ["system-health", "crm"],
    "system-health": ["executive", "crm"],
    "crm":          ["executive", "system-health"],
}

IMPORT_RE = re.compile(
    r"""(?:import\s+[^'"]*?from\s+|import\s+|require\s*\(\s*|export\s+[^'"]*?from\s+)['"]([^'"]+)['"]""",
    re.MULTILINE | re.DOTALL,
)


def context_of_source_file(rel_path: str) -> str | None:
    for ctx, patterns in CONTEXT_PATH_PATTERNS.items():
        for p in patterns:
            if p.match(rel_path):
                return ctx
    return None


def context_of_import_path(import_path: str) -> str | None:
    if import_path.startswith("@/"):
        target = import_path[2:]
    else:
        return None
    for ctx, patterns in CONTEXT_PATH_PATTERNS.items():
        for p in patterns:
            if p.match(target):
                return ctx
    return None


def find_source_files(root: Path) -> list[Path]:
    out = []
    for ext in ("*.ts", "*.tsx", "*.js", "*.jsx", "*.mjs", "*.cjs"):
        out.extend(root.rglob(ext))
    return out


def scan_file(path: Path) -> list[str]:
    rel = path.relative_to(WEB_ROOT)
    rel_str = rel.as_posix()
    src_ctx = context_of_source_file(rel_str)
    if src_ctx is None:
        return []
    forbidden_targets = FORBIDDEN_MATRIX.get(src_ctx, [])

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except (OSError, UnicodeDecodeError):
        return []

    text_clean = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text_clean = re.sub(r"//.*$", "", text_clean, flags=re.MULTILINE)

    violations = []
    for m in IMPORT_RE.finditer(text_clean):
        imp = m.group(1)
        tgt_ctx = context_of_import_path(imp)
        if tgt_ctx is None:
            continue
        if tgt_ctx == src_ctx:
            continue
        if tgt_ctx in forbidden_targets:
            line_no = text_clean[:m.start()].count("\n") + 1
            violations.append(
                f"[CROSS-CONTEXT] {rel_str}:{line_no}\n"
                f"    {src_ctx} imports {tgt_ctx} (import: {imp})\n"
                f"    Policy: §4.1 — bounded contexts communicate only through public APIs / events / message bus"
            )
    return violations


def check_feature_flags_purity() -> list[str]:
    ff = WEB_ROOT / "lib" / "feature-flags" / "feature-flags.ts"
    if not ff.exists():
        return []
    text = ff.read_text(encoding="utf-8", errors="replace")
    text_clean = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text_clean = re.sub(r"//.*$", "", text_clean, flags=re.MULTILINE)
    violations = []
    for m in IMPORT_RE.finditer(text_clean):
        imp = m.group(1)
        if imp.startswith("@/app/") or imp.startswith("@/lib/api/"):
            line_no = text_clean[:m.start()].count("\n") + 1
            violations.append(
                f"[FF-IMPURITY] lib/feature-flags/feature-flags.ts:{line_no}\n"
                f"    imports {imp}\n"
                f"    Policy: §9 — Feature flag registry MUST NOT import business modules"
            )
    return violations


def check_routes_purity() -> list[str]:
    violations = []
    routes_dir = WEB_ROOT / "lib" / "routes"
    if not routes_dir.exists():
        return violations
    for f in routes_dir.glob("*.ts"):
        text = f.read_text(encoding="utf-8", errors="replace")
        text_clean = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
        text_clean = re.sub(r"//.*$", "", text_clean, flags=re.MULTILINE)
        for m in IMPORT_RE.finditer(text_clean):
            imp = m.group(1)
            if imp.startswith("@/app/") or imp.startswith("@/lib/api/"):
                line_no = text_clean[:m.start()].count("\n") + 1
                rel = f.relative_to(WEB_ROOT).as_posix()
                violations.append(
                    f"[ROUTES-IMPURITY] {rel}:{line_no}\n"
                    f"    imports {imp}\n"
                    f"    Policy: §5.2 — Route registries must be pure constants (no runtime imports)"
                )
    return violations


def main() -> int:
    if not WEB_ROOT.exists():
        print(f"[SKIP] web root not found at {WEB_ROOT}")
        return 0

    files = find_source_files(WEB_ROOT)
    files = [f for f in files if "node_modules" not in f.parts and ".next" not in f.parts]
    print(f"[INFO] Scanning {len(files)} frontend source files under {WEB_ROOT}")

    all_violations: list[str] = []
    for f in files:
        all_violations += scan_file(f)

    all_violations += check_feature_flags_purity()
    all_violations += check_routes_purity()

    if not all_violations:
        print("[PASS] Frontend architecture boundaries validated — 0 violations")
        return 0

    print(f"[FAIL] Frontend architecture violations: {len(all_violations)}\n")
    for v in all_violations:
        print(v)
        print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
