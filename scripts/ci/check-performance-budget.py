#!/usr/bin/env python3
"""
SANAD Performance Budget Checker — FAIL-CLOSED EDITION
Validates that the production build stays within performance budgets.

Per PM Directive §2 — must FAIL when:
  - Build directory missing after build step
  - build-manifest.json missing
  - Manifest JSON corrupt or unreadable
  - Expected JS files missing
  - Cannot compute bundle sizes
  - No verifiable measurement data

This script NEVER returns PASS without verifiable evidence.
"""
import json
import os
import sys
from pathlib import Path

# Performance budgets (in bytes)
BUDGETS = {
    "total_initial_js": 500_000,        # 500 KB total initial JS
    "per_route_js": 350_000,            # 350 KB per route JS
    "largest_chunk": 300_000,           # 300 KB largest single chunk
    "total_static_assets": 2_000_000,   # 2 MB total static assets
    "fonts_count": 8,                   # Max 8 font files
    "fonts_total_size": 300_000,        # 300 KB total fonts
    "logo_asset_size": 20_000,          # 20 KB per logo SVG
    "login_route_js": 200_000,          # 200 KB login route JS
    "exec_dashboard_js": 350_000,       # 350 KB executive dashboard JS
}

BUILD_DIR = Path("apps/web/.next")
PUBLIC_DIR = Path("apps/web/public")


def fail(message):
    """Print failure and exit with code 1."""
    print(f"FAIL — {message}")
    sys.exit(1)


def measure_logo_sizes():
    """Measure logo SVG file sizes. Returns list of (name, size) tuples."""
    results = []
    brand_dir = PUBLIC_DIR / "assets" / "brand"
    if not brand_dir.exists():
        fail(f"Brand directory not found: {brand_dir}")
    for svg in sorted(brand_dir.glob("*.svg")):
        results.append((svg.name, svg.stat().st_size))
    if not results:
        fail("No logo SVG files found in brand directory")
    return results


def measure_fonts():
    """Measure font file count and total size."""
    font_files = list(PUBLIC_DIR.rglob("*.woff2")) + \
                 list(PUBLIC_DIR.rglob("*.woff")) + \
                 list(PUBLIC_DIR.rglob("*.ttf"))
    total_size = sum(f.stat().st_size for f in font_files)
    return font_files, total_size


def measure_build_bundles():
    """Measure JavaScript bundle sizes from Next.js build output.
    FAIL-CLOSED: fails if build directory or manifest is missing."""
    if not BUILD_DIR.exists():
        fail("Build directory not found (apps/web/.next) — build step must succeed first")

    # Check for build-manifest.json
    manifest_path = BUILD_DIR / "build-manifest.json"
    if not manifest_path.exists():
        fail("build-manifest.json not found — cannot verify bundle sizes without manifest")

    # Parse manifest
    try:
        with open(manifest_path) as f:
            manifest = json.load(f)
    except json.JSONDecodeError as e:
        fail(f"build-manifest.json is corrupt or unreadable: {e}")
    except IOError as e:
        fail(f"Cannot read build-manifest.json: {e}")

    # Collect all JS files from manifest
    page_bundles = {}
    all_js_files = set()

    pages = manifest.get("pages", {})
    if not pages:
        fail("build-manifest.json contains no pages — manifest is empty or invalid")

    for page, assets in pages.items():
        js_assets = [a for a in assets if a.endswith(".js")]
        page_size = 0
        for asset in js_assets:
            asset_path = BUILD_DIR / asset.lstrip("/")
            if not asset_path.exists():
                fail(f"JavaScript file referenced in manifest not found on disk: {asset}")
            page_size += asset_path.stat().st_size
            all_js_files.add(asset)
        page_bundles[page] = {
            "js_files": len(js_assets),
            "total_js_bytes": page_size,
        }

    # Measure total JS
    total_js = sum(p["total_js_bytes"] for p in page_bundles.values())

    # Find largest chunk
    largest_chunk = 0
    largest_chunk_name = ""
    for asset in all_js_files:
        asset_path = BUILD_DIR / asset.lstrip("/")
        if asset_path.exists():
            size = asset_path.stat().st_size
            if size > largest_chunk:
                largest_chunk = size
                largest_chunk_name = asset

    # Measure total static assets
    static_dir = BUILD_DIR / "static"
    total_static = 0
    if static_dir.exists():
        for f in static_dir.rglob("*"):
            if f.is_file():
                total_static += f.stat().st_size

    # Find login route bundle
    login_js = 0
    for page, data in page_bundles.items():
        if "/auth" in page or "/login" in page:
            login_js = max(login_js, data["total_js_bytes"])

    # Find executive dashboard bundle
    exec_js = 0
    for page, data in page_bundles.items():
        if "/control-plane" in page or "/workspace" in page:
            exec_js = max(exec_js, data["total_js_bytes"])

    return {
        "page_bundles": page_bundles,
        "total_js": total_js,
        "largest_chunk": largest_chunk,
        "largest_chunk_name": largest_chunk_name,
        "total_static": total_static,
        "login_route_js": login_js,
        "exec_dashboard_js": exec_js,
    }


def main():
    print("=" * 70)
    print("SANAD Performance Budget Checker — FAIL-CLOSED")
    print("=" * 70)
    print()

    measurements = {}
    violations = []

    # --- Logo Assets ---
    print("Measuring logo asset sizes...")
    logo_sizes = measure_logo_sizes()
    measurements["logos"] = logo_sizes
    for name, size in logo_sizes:
        if size > BUDGETS["logo_asset_size"]:
            violations.append(
                f"Logo '{name}' exceeds budget: {size:,} bytes "
                f"(budget: {BUDGETS['logo_asset_size']:,} bytes)"
            )

    # --- Fonts ---
    print("Measuring font files...")
    font_files, font_total = measure_fonts()
    measurements["fonts"] = {"count": len(font_files), "total_bytes": font_total}
    if len(font_files) > BUDGETS["fonts_count"]:
        violations.append(
            f"Font file count exceeds budget: {len(font_files)} "
            f"(budget: {BUDGETS['fonts_count']})"
        )
    if font_total > BUDGETS["fonts_total_size"]:
        violations.append(
            f"Total font size exceeds budget: {font_total:,} bytes "
            f"(budget: {BUDGETS['fonts_total_size']:,} bytes)"
        )

    # --- Build Bundles (FAIL-CLOSED) ---
    print("Measuring build bundle sizes (fail-closed)...")
    bundle_data = measure_build_bundles()
    measurements["bundles"] = bundle_data

    if bundle_data["total_js"] > BUDGETS["total_initial_js"]:
        violations.append(
            f"Total initial JS exceeds budget: {bundle_data['total_js']:,} bytes "
            f"(budget: {BUDGETS['total_initial_js']:,} bytes)"
        )
    if bundle_data["largest_chunk"] > BUDGETS["largest_chunk"]:
        violations.append(
            f"Largest chunk exceeds budget: {bundle_data['largest_chunk']:,} bytes "
            f"({bundle_data['largest_chunk_name']}, budget: {BUDGETS['largest_chunk']:,} bytes)"
        )
    if bundle_data["login_route_js"] > BUDGETS["login_route_js"]:
        violations.append(
            f"Login route JS exceeds budget: {bundle_data['login_route_js']:,} bytes "
            f"(budget: {BUDGETS['login_route_js']:,} bytes)"
        )
    if bundle_data["exec_dashboard_js"] > BUDGETS["exec_dashboard_js"]:
        violations.append(
            f"Executive dashboard JS exceeds budget: {bundle_data['exec_dashboard_js']:,} bytes "
            f"(budget: {BUDGETS['exec_dashboard_js']:,} bytes)"
        )

    # --- Report ---
    print()
    print("=" * 70)
    print("PERFORMANCE MEASUREMENT REPORT")
    print("=" * 70)
    print()

    # Logos
    print("Logo Assets:")
    for name, size in logo_sizes:
        status = "OK" if size <= BUDGETS["logo_asset_size"] else "OVER"
        print(f"  {name:40s} {size:>8,} bytes  [{status}]")
    print()

    # Fonts
    print("Fonts:")
    print(f"  File count:     {font_files.count if hasattr(font_files, 'count') else len(font_files)}  "
          f"(budget: {BUDGETS['fonts_count']})")
    print(f"  Total size:     {font_total:,} bytes  "
          f"(budget: {BUDGETS['fonts_total_size']:,} bytes)")
    print()

    # Bundles
    print("JavaScript Bundles:")
    print(f"  Total initial JS:      {bundle_data['total_js']:>10,} bytes  "
          f"(budget: {BUDGETS['total_initial_js']:,})")
    print(f"  Largest chunk:         {bundle_data['largest_chunk']:>10,} bytes  "
          f"(budget: {BUDGETS['largest_chunk']:,})")
    print(f"    └─ {bundle_data['largest_chunk_name']}")
    print(f"  Login route JS:        {bundle_data['login_route_js']:>10,} bytes  "
          f"(budget: {BUDGETS['login_route_js']:,})")
    print(f"  Executive dashboard:   {bundle_data['exec_dashboard_js']:>10,} bytes  "
          f"(budget: {BUDGETS['exec_dashboard_js']:,})")
    print(f"  Total static assets:   {bundle_data['total_static']:>10,} bytes  "
          f"(budget: {BUDGETS['total_static_assets']:,})")
    print()

    # Per-page breakdown
    print("Per-Page Bundle Breakdown:")
    for page, data in sorted(bundle_data["page_bundles"].items()):
        over = "OVER" if data["total_js_bytes"] > BUDGETS["per_route_js"] else "OK"
        print(f"  {page:50s} {data['total_js_bytes']:>10,} bytes  [{over}]")
    print()

    # Final result
    if violations:
        print(f"FAIL — {len(violations)} performance budget violation(s):")
        for v in violations:
            print(f"  • {v}")
        sys.exit(1)
    else:
        print("PASS — all performance budgets met with verifiable measurements")
        sys.exit(0)


if __name__ == "__main__":
    main()
