#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================================
 SNAD Module Visual Compliance Check
----------------------------------------------------------------------------
 PURPOSE
 -------
 System-wide enforcement of the SNAD Module Visual Contract
 (docs/superpowers/specs/2026-10-02-snad-module-visual-contract.md).

 No module under apps/web/** may create an independent visual identity.
 Every module must consume the shared primitives in apps/web/components/sds/
 module/ and reference SDS tokens via var(--snad-*).

 CHECKS (fail closed on NEW violations)
 --------------------------------------
   HARDCODED_COLOR        raw hex/rgb(a)/hsl(a) color values outside the
                          token source-of-truth files
   HARDCODED_FONT         font-family declarations not using var(--snad-font-*)
   NON_TOKEN_SHADOW       box-shadow literals not built from tokens
   MODULE_LOCAL_PALETTE   CSS custom property *definitions* carrying color
                          literals in module CSS
   RAW_LOGO_IMPORT        direct brand SVG imports outside the SnadLogo
                          component (must consume <SnadLogo />)
   DUPLICATE_SHELL        module CSS re-declaring shell geometry
                          (100vh canvas + sticky header / sidebar grid) that
                          duplicates the shared module shell primitives
   RTL_HOSTILE_PROPERTY   physical directional CSS (padding-left/right,
                          margin-left/right, text-align:left/right,
                          positional left/right) where the canonical logical
                          properties are required
   UNGOVERNED_BREAKPOINT  @media widths outside the governed responsive set
                          (768px, 480px — including the 47.999rem mobile
                          record swap) where shared responsive primitives exist

 SCOPE / ALLOWLIST MODEL (fail closed on NEW violations)
 -------------------------------------------------------
   • Token sources (apps/web/design-system/**, apps/web/app/snad-tokens.css,
     apps/web/app/globals.css, apps/web/app/snad-tailwind.css) and the shared
     primitives (apps/web/components/sds/**) are structurally exempt.
   • Pre-contract legacy surfaces are allowlisted PER FILE with the rules
     they are frozen under, and tracked in the Visual Migration Ledger.
     New files are never allowlisted; new violations in allowlisted files
     still fail when they use a rule the file is not frozen under.

 SELF TEST
 ---------
 `--self-test` runs the checker against an embedded compliant fixture and an
 embedded non-compliant fixture and asserts both outcomes, proving the check
 stays fail-closed. CI runs this on every PR that touches apps/web.

 USAGE
 -----
   python3 scripts/ci/check-module-visual-compliance.py [repo-root]
   python3 scripts/ci/check-module-visual-compliance.py apps/web
   python3 scripts/ci/check-module-visual-compliance.py --self-test

 EXIT CODES
 ----------
   0 — compliant / self-test pass
   1 — violations found (or self-test failure)
   2 — usage error
"""

from __future__ import annotations

import os
import re
import sys
import tempfile
from pathlib import Path

WEB_PREFIX = "apps/web"

# Paths relative to the repo root, never scanned (token sources + shared
# primitives + tooling + test infrastructure).
STRUCTURAL_EXEMPT_PREFIXES = (
    f"{WEB_PREFIX}/design-system/",
    f"{WEB_PREFIX}/components/sds/",
    "scripts/",
    "e2e/",
    "playwright-report/",
    "test-results/",
    "node_modules/",
)

GLOBAL_EXEMPT = {
    f"{WEB_PREFIX}/app/globals.css",
    f"{WEB_PREFIX}/app/snad-tailwind.css",
    f"{WEB_PREFIX}/app/snad-tokens.css",  # legacy v1 alias shim (values are var() refs)
}

# --------------------------------------------------------------------------
# Legacy allowlist — pre-contract surfaces, drift-frozen, tracked in
# docs/superpowers/specs/2026-10-02-visual-migration-ledger.md.
# Map: repo-relative path -> set of rule names exempted for that file.
# --------------------------------------------------------------------------
FULL_FREEZE = {
    "HARDCODED_COLOR", "HARDCODED_FONT", "NON_TOKEN_SHADOW", "MODULE_LOCAL_PALETTE",
    "DUPLICATE_SHELL", "RTL_HOSTILE_PROPERTY", "UNGOVERNED_BREAKPOINT", "RAW_LOGO_IMPORT",
}

LEGACY_ALLOWLIST: dict[str, set[str]] = {
    # HR pre-contract module styles. Shell classes retired by G3 Task 6
    # (HrWorkspace renders the shared shell); remaining page anatomy frozen.
    f"{WEB_PREFIX}/app/hr/hr.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/hr/components/hr-g2-visual.module.css": FULL_FREEZE,
    # CRM page-level styles (shell migrated to shared primitives in Task 6;
    # page anatomy remains, drift-frozen).
    f"{WEB_PREFIX}/app/crm/crm-shared-styles.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/crm/crm.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/crm/(operational)/integrations/crm-integrations.module.css": FULL_FREEZE,
    # Other pre-contract module surfaces — drift-frozen pending their own
    # migration tasks (see ledger).
    f"{WEB_PREFIX}/app/workspace/workspace.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/workflow/workflow.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/workflow/definitions/[id]/components/workflow-designer.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/erp/erp.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/system-health/system-health.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/control-plane/control-plane.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/executive/scp.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/management/users/users.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/app/management/access/access.module.css": FULL_FREEZE,
    # Cross-module app chrome predating the contract (login, executive shell,
    # route skeletons) — drift-frozen.
    f"{WEB_PREFIX}/components/auth/auth.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/components/auth/login-v2.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/components/auth/login-v3.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/components/shell/ExecutiveShell.module.css": FULL_FREEZE,
    f"{WEB_PREFIX}/components/shell/route-skeleton.module.css": FULL_FREEZE,
}

# --------------------------------------------------------------------------
# Rule regexes
# --------------------------------------------------------------------------
HEX_COLOR_RE = re.compile(r"#[0-9a-fA-F]{3,8}\b")
RGB_FUNC_RE = re.compile(r"\brgba?\(|\bhsla?\(")
FONT_FAMILY_RE = re.compile(r"font-family\s*:\s*([^;]+);", re.IGNORECASE)
BOX_SHADOW_RE = re.compile(r"box-shadow\s*:\s*([^;]+);", re.IGNORECASE)
PALETTE_DEF_RE = re.compile(r"(--[a-zA-Z0-9-]+)\s*:\s*([^;]+);")
LOGO_IMPORT_RE = re.compile(r"import\s+[^;]*\.svg|require\(['\"][^'\"]*\.svg['\"]\)")
RTL_HOSTILE_RE = re.compile(
    r"(?<![-\w])(padding-left|padding-right|margin-left|margin-right|text-align)\s*:\s*(left|right)\b"
    r"|(?<![-\w])(left|right)\s*:\s*\d",
)
MEDIA_BREAKPOINT_RE = re.compile(r"@media[^{]*?\(\s*(?:max|min)-width\s*:\s*([0-9.]+)\s*(px|rem)\s*\)")
GOVERNED_BREAKPOINTS_PX = {768.0, 480.0}
SHELL_INDICATORS = ("100vh", "100dvh")


def to_repo_relative(path: Path, scan_root: Path) -> str:
    """Express `path` relative to the repo root (the dir that contains apps/web)."""
    repo_root = scan_root
    while repo_root != repo_root.parent and repo_root.name != WEB_PREFIX.split("/")[0]:
        if (repo_root / WEB_PREFIX.split("/")[0] / WEB_PREFIX.split("/")[1]).exists() or repo_root.name == "SNAD":
            break
        repo_root = repo_root.parent
    # Preferred: walk up from scan_root until a sibling "apps" dir exists.
    cursor = scan_root
    while cursor != cursor.parent:
        if cursor.name == "web" and cursor.parent.name == "apps":
            repo_root = cursor.parent.parent
            break
        cursor = cursor.parent
    try:
        return path.relative_to(repo_root).as_posix()
    except ValueError:
        return path.as_posix()


def iter_files(scan_root: Path):
    for dirpath, dirnames, filenames in os.walk(scan_root):
        dirnames[:] = [d for d in dirnames if d not in ("node_modules", ".next", ".git", "__screenshots__")]
        for filename in filenames:
            if filename.endswith((".css", ".tsx", ".ts")) and not filename.endswith(".d.ts"):
                yield Path(dirpath) / filename


def strip_comments(content: str) -> str:
    content = re.sub(r"/\*.*?\*/", "", content, flags=re.DOTALL)
    return content


def font_family_is_governed(value: str) -> bool:
    v = value.strip()
    return v.startswith("var(--snad-") or v in {"inherit", "monospace"} or re.fullmatch(r"[a-zA-Z-]+,\s*(monospace|inherit)", v) is not None


def shadow_is_governed(value: str) -> bool:
    v = value.strip().lower()
    if v in {"none", "unset", "inherit", "initial"}:
        return True
    return "var(--snad-" in v


def check_css(content: str) -> list[tuple[str, int, str]]:
    findings: list[tuple[str, int, str]] = []
    stripped = strip_comments(content)

    def line_of(pos: int) -> int:
        return content[:pos].count("\n") + 1

    for m in HEX_COLOR_RE.finditer(stripped):
        findings.append(("HARDCODED_COLOR", line_of(m.start()), f"raw hex color {m.group(0)}"))
    for m in RGB_FUNC_RE.finditer(stripped):
        findings.append(("HARDCODED_COLOR", line_of(m.start()), f"raw rgb/hsl color {m.group(0)}"))
    for m in FONT_FAMILY_RE.finditer(stripped):
        if not font_family_is_governed(m.group(1)):
            findings.append(("HARDCODED_FONT", line_of(m.start()), f"non-token font-family: {m.group(1).strip()[:60]}"))
    for m in BOX_SHADOW_RE.finditer(stripped):
        if not shadow_is_governed(m.group(1)):
            findings.append(("NON_TOKEN_SHADOW", line_of(m.start()), f"box-shadow without token: {m.group(1).strip()[:60]}"))
    for m in PALETTE_DEF_RE.finditer(stripped):
        name, value = m.group(1), m.group(2).strip()
        if HEX_COLOR_RE.search(value) or RGB_FUNC_RE.search(value):
            findings.append(("MODULE_LOCAL_PALETTE", line_of(m.start()), f"palette definition {name}: {value[:50]}"))

    has_canvas = any(ind in stripped for ind in SHELL_INDICATORS)
    has_sticky = "position: sticky" in stripped or "position:sticky" in stripped
    has_sidebar_grid = re.search(r"grid-template-columns\s*:\s*\d{3}px", stripped) is not None
    if has_canvas and (has_sticky or has_sidebar_grid):
        findings.append(("DUPLICATE_SHELL", 1, "module CSS re-declares shell geometry (canvas + sticky header / sidebar grid)"))

    for m in RTL_HOSTILE_RE.finditer(stripped):
        findings.append(("RTL_HOSTILE_PROPERTY", line_of(m.start()), f"physical property {m.group(0)[:40]}"))

    for m in MEDIA_BREAKPOINT_RE.finditer(stripped):
        width = float(m.group(1))
        if m.group(2) == "rem":
            width = round(width * 16.0, 1)
        if width not in GOVERNED_BREAKPOINTS_PX:
            findings.append(("UNGOVERNED_BREAKPOINT", line_of(m.start()), f"breakpoint {m.group(1)}{m.group(2)} outside governed set {sorted(GOVERNED_BREAKPOINTS_PX)}px"))

    return findings


def check_ts(content: str) -> list[tuple[str, int, str]]:
    findings: list[tuple[str, int, str]] = []
    stripped = strip_comments(content)
    stripped = re.sub(r"//[^\n]*", "", stripped)

    def line_of(pos: int) -> int:
        return content[:pos].count("\n") + 1

    for m in HEX_COLOR_RE.finditer(stripped):
        findings.append(("HARDCODED_COLOR", line_of(m.start()), f"raw hex color {m.group(0)}"))
    for m in LOGO_IMPORT_RE.finditer(stripped):
        findings.append(("RAW_LOGO_IMPORT", line_of(m.start()), f"direct SVG import: {m.group(0)[:50]}"))

    return findings


def scan(scan_root: Path) -> list[str]:
    violations: list[str] = []
    for path in iter_files(scan_root):
        rel = to_repo_relative(path, scan_root)
        if any(rel.startswith(prefix) for prefix in STRUCTURAL_EXEMPT_PREFIXES):
            continue
        if rel in GLOBAL_EXEMPT:
            continue
        legacy_rules = LEGACY_ALLOWLIST.get(rel)

        content = path.read_text(encoding="utf-8", errors="replace")
        findings = check_css(content) if path.suffix == ".css" else check_ts(content)

        for rule, line, message in findings:
            if legacy_rules and rule in legacy_rules:
                continue
            violations.append(f"{rel}:{line}:{rule} — {message}")
    return violations


# --------------------------------------------------------------------------
# Self test
# --------------------------------------------------------------------------
COMPLIANT_FIXTURE_CSS = """
.goodCard {
  background: var(--snad-surface-primary);
  border: 1px solid var(--snad-border-default);
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: var(--snad-shadow-sm);
  margin-inline-start: 4px;
  text-align: start;
  font-family: var(--snad-font-body);
}

@media (max-width: 768px) {
  .goodCard { padding: 14px; }
}
"""

COMPLIANT_FIXTURE_TSX = """
import { SnadModuleShell } from "@/components/sds/module";
import { SnadLogo } from "@/components/sds/SnadLogo";

export function GoodModule({ children }: { children: React.ReactNode }) {
  return (
    <SnadModuleShell brandMark="S" title="Good" navLabel="Good nav" navSections={[]} activeHref="/good">
      {children}
    </SnadModuleShell>
  );
}
"""

NONCOMPLIANT_FIXTURE_CSS = """
.badShell {
  min-height: 100vh;
  position: sticky;
  background: #123456;
  padding-left: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.4);
}

.badBadge { --bad-palette: #ff0000; color: var(--bad-palette); }

@media (max-width: 900px) {
  .badShell { padding: 4px; }
}
"""

NONCOMPLIANT_FIXTURE_TSX = """
import logo from "./brand.svg";
export const BRAND = logo;
"""


def run_self_test() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        compliant_dir = base / "compliant" / "web" / "app" / "good"
        noncompliant_dir = base / "noncompliant" / "web" / "app" / "bad"
        compliant_dir.mkdir(parents=True)
        noncompliant_dir.mkdir(parents=True)

        (compliant_dir / "good.module.css").write_text(COMPLIANT_FIXTURE_CSS, encoding="utf-8")
        (compliant_dir / "page.tsx").write_text(COMPLIANT_FIXTURE_TSX, encoding="utf-8")
        (noncompliant_dir / "bad.module.css").write_text(NONCOMPLIANT_FIXTURE_CSS, encoding="utf-8")
        (noncompliant_dir / "brand.ts").write_text(NONCOMPLIANT_FIXTURE_TSX, encoding="utf-8")

        compliant_violations = scan(base / "compliant")
        print(f"SELF_TEST_COMPLIANT_FIXTURE={'PASS' if not compliant_violations else 'FAIL'}")
        for v in compliant_violations:
            print(f"  unexpected: {v}")

        noncompliant_violations = scan(base / "noncompliant")
        noncompliant_ok = len(noncompliant_violations) > 0
        print(f"SELF_TEST_NONCOMPLIANT_FIXTURE={'FAIL(as expected)' if noncompliant_ok else 'PASS(BAD)'}")
        for v in noncompliant_violations:
            print(f"  caught: {v}")
        if not noncompliant_ok:
            print("  expected violations in non-compliant fixture, got none")

        if not compliant_violations and noncompliant_ok:
            print("SELF_TEST=PASS")
            return 0
        print("SELF_TEST=FAIL")
        return 1


def main() -> int:
    args = sys.argv[1:]
    if "--self-test" in args:
        return run_self_test()

    target = Path(args[0]) if args else Path(".")
    if target.name == "web" and target.parent.name == "apps" and (target / "app").exists():
        scan_root = target
    elif (target / WEB_PREFIX / "app").exists():
        scan_root = target / WEB_PREFIX
    else:
        print(f"usage: {sys.argv[0]} [repo-root|apps/web] (cannot locate apps/web from {target})")
        return 2

    violations = scan(scan_root)
    for v in violations:
        print(v)
    print(f"MODULE_VISUAL_COMPLIANCE={'FAIL' if violations else 'PASS'} violations={len(violations)}")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
