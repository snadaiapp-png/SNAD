#!/usr/bin/env python3
"""Fail-closed governance for the owner-approved SNAD | سند artwork."""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

EXPECTED_ASSETS = {
    "apps/web/public/assets/brand/snad-logo-official-primary.webp":
        "7d3220e2f37b1dd9ca9e635c671693c73856ae4b2adb31263d3491daa18c4786",
    "apps/web/public/assets/brand/snad-logo-official-wordmark.webp":
        "406c537c2e537a0f80efd22b2271eddf77452c67b9444eae919ef61771a02403",
}

SNAD_LOGO_COMPONENT = REPO_ROOT / "apps/web/components/sds/SnadLogo.tsx"
AUTH_FORM = REPO_ROOT / "apps/web/components/auth/login-form.tsx"
GLOBAL_SHELL = REPO_ROOT / "apps/web/components/shell/GlobalShellBoundary.tsx"

OLD_ASSET_PATTERN = re.compile(
    r"/assets/brand/snad-(?:logo-(?:primary|vertical|white|mono)|app-icon|favicon)\.svg",
    re.IGNORECASE,
)
OFFICIAL_ASSET_PATTERN = re.compile(
    r"/assets/brand/snad-logo-official-(?:primary|wordmark)\.webp",
    re.IGNORECASE,
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def scan_direct_references() -> list[str]:
    violations: list[str] = []
    allowed = {
        SNAD_LOGO_COMPONENT.resolve(),
        (REPO_ROOT / "apps/web/components/sds/__tests__/SnadLogo.test.tsx").resolve(),
        (REPO_ROOT / "apps/web/e2e/visual-governance.spec.ts").resolve(),
    }
    for path in (REPO_ROOT / "apps/web").rglob("*"):
        if path.suffix.lower() not in {".ts", ".tsx", ".js", ".jsx", ".css"}:
            continue
        if any(part in {"node_modules", ".next", "coverage"} for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if path.resolve() not in allowed and OFFICIAL_ASSET_PATTERN.search(text):
            violations.append(
                f"{path.relative_to(REPO_ROOT)}: direct official-logo reference; use <SnadLogo />"
            )
        if OLD_ASSET_PATTERN.search(text):
            violations.append(
                f"{path.relative_to(REPO_ROOT)}: deprecated reconstructed SVG logo reference"
            )
    return violations


def main() -> int:
    violations: list[str] = []

    for relative, expected in EXPECTED_ASSETS.items():
        path = REPO_ROOT / relative
        if not path.is_file():
            violations.append(f"{relative}: missing canonical artwork")
            continue
        actual = sha256(path)
        if actual != expected:
            violations.append(
                f"{relative}: SHA-256 mismatch; expected {expected}, got {actual}"
            )

    if not SNAD_LOGO_COMPONENT.is_file():
        violations.append("SnadLogo.tsx is missing")
    else:
        component = SNAD_LOGO_COMPONENT.read_text(encoding="utf-8")
        for required in (
            "/assets/brand/snad-logo-official-primary.webp",
            "/assets/brand/snad-logo-official-wordmark.webp",
        ):
            if required not in component:
                violations.append(f"SnadLogo.tsx does not reference {required}")
        if OLD_ASSET_PATTERN.search(component):
            violations.append("SnadLogo.tsx still references a reconstructed SVG")

    if not AUTH_FORM.is_file() or "<SnadLogo" not in AUTH_FORM.read_text(encoding="utf-8"):
        violations.append("login-form.tsx must render <SnadLogo />")

    if not GLOBAL_SHELL.is_file():
        violations.append("GlobalShellBoundary.tsx is missing")
    else:
        shell = GLOBAL_SHELL.read_text(encoding="utf-8")
        if "ExecutiveShell" not in shell:
            violations.append("GlobalShellBoundary must apply ExecutiveShell")
        if 'pathname === "/"' not in shell:
            violations.append("GlobalShellBoundary must exclude the public login route")

    violations.extend(scan_direct_references())

    if violations:
        print("SNAD Official Logo Governance: FAIL")
        for violation in violations:
            print(f"  - {violation}")
        return 1

    print("SNAD Official Logo Governance: PASS")
    print("  canonical assets: 2/2 hash-verified")
    print("  rendering path: SnadLogo only")
    print("  authenticated shell: globally enforced")
    return 0


if __name__ == "__main__":
    sys.exit(main())
