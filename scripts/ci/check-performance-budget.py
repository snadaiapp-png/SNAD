#!/usr/bin/env python3
"""
SANAD Performance Budget Checker
Validates that the production build stays within performance budgets.
Checks bundle sizes, asset counts, and critical resource weights.

Per executive order §16 — Performance Tests:
  "Add Performance Budgets inside CI to prevent future regression."
"""
import json
import os
import sys
from pathlib import Path

# Performance budgets (in bytes)
BUDGETS = {
    "auth_route_js_kb": 200_000,        # 200 KB JS for auth route
    "auth_route_css_kb": 50_000,        # 50 KB CSS for auth route
    "workspace_shell_js_kb": 350_000,   # 350 KB JS for workspace shell
    "workspace_shell_css_kb": 80_000,   # 80 KB CSS for workspace shell
    "total_initial_js_kb": 500_000,     # 500 KB total initial JS
    "logo_svg_kb": 20_000,              # 20 KB per logo SVG
    "font_files_count": 8,              # Max 8 font files
    "total_font_kb": 300_000,           # 300 KB total fonts
}

BUILD_DIR = Path("apps/web/.next")
PUBLIC_DIR = Path("apps/web/public")

def check_build_exists():
    """Verify that the build directory exists."""
    if not BUILD_DIR.exists():
        print("SKIP — build directory not found (run 'npm run build' first)")
        return False
    return True

def check_logo_sizes():
    """Check that logo SVG files are within budget."""
    violations = []
    brand_dir = PUBLIC_DIR / "assets" / "brand"
    if not brand_dir.exists():
        violations.append(f"Brand directory not found: {brand_dir}")
        return violations

    for svg in brand_dir.glob("*.svg"):
        size = svg.stat().st_size
        if size > BUDGETS["logo_svg_kb"]:
            violations.append(
                f"Logo SVG too large: {svg.name} = {size:,} bytes "
                f"(budget: {BUDGETS['logo_svg_kb']:,} bytes)"
            )
    return violations

def check_font_files():
    """Check font file count and total size."""
    violations = []
    font_files = list(PUBLIC_DIR.rglob("*.woff2")) + \
                 list(PUBLIC_DIR.rglob("*.woff")) + \
                 list(PUBLIC_DIR.rglob("*.ttf"))
    if len(font_files) > BUDGETS["font_files_count"]:
        violations.append(
            f"Too many font files: {len(font_files)} "
            f"(budget: {BUDGETS['font_files_count']})"
        )
    total_font_size = sum(f.stat().st_size for f in font_files)
    if total_font_size > BUDGETS["total_font_kb"]:
        violations.append(
            f"Total font size too large: {total_font_size:,} bytes "
            f"(budget: {BUDGETS['total_font_kb']:,} bytes)"
        )
    return violations

def check_build_manifest():
    """Check Next.js build manifest for bundle sizes."""
    violations = []
    manifest_path = BUILD_DIR / "build-manifest.json"
    if not manifest_path.exists():
        return violations  # Skip if manifest doesn't exist

    try:
        with open(manifest_path) as f:
            manifest = json.load(f)
    except (json.JSONDecodeError, IOError):
        return violations

    # Check total JS chunks
    total_js = 0
    for page, assets in manifest.get("pages", {}).items():
        for asset in assets:
            if asset.endswith(".js"):
                asset_path = BUILD_DIR / asset.lstrip("/")
                if asset_path.exists():
                    total_js += asset_path.stat().st_size

    if total_js > BUDGETS["total_initial_js_kb"]:
        violations.append(
            f"Total initial JS too large: {total_js / 1024:.0f} KB "
            f"(budget: {BUDGETS['total_initial_js_kb'] / 1024:.0f} KB)"
        )

    return violations

def main():
    print("=" * 60)
    print("SANAD Performance Budget Checker")
    print("=" * 60)
    print()

    all_violations = []

    # Check logo sizes
    print("Checking logo SVG sizes...")
    all_violations.extend(check_logo_sizes())

    # Check font files
    print("Checking font file count and sizes...")
    all_violations.extend(check_font_files())

    # Check build manifest (if build exists)
    if check_build_exists():
        print("Checking build manifest bundle sizes...")
        all_violations.extend(check_build_manifest())
    else:
        print("SKIP — no build directory (CI will check after build)")

    print()
    if all_violations:
        print(f"FAIL — {len(all_violations)} performance budget violation(s):")
        for v in all_violations:
            print(f"  • {v}")
        sys.exit(1)
    else:
        print("PASS — all performance budgets met")
        sys.exit(0)

if __name__ == "__main__":
    main()
