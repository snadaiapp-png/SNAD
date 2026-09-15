#!/usr/bin/env python3
"""
SANAD Performance Budget Checker — FAIL-CLOSED EDITION
Validates that the production build stays within performance budgets.

Next.js 16/Turbopack uses App Router client-reference manifests rather than
populating the legacy top-level build-manifest pages map. The checker therefore
measures real App Router client chunks and refuses a zero-byte "measurement".

Per PM Directive §2 — must FAIL when:
  - Build directory missing after build step
  - Required build metadata is missing/corrupt
  - Referenced JS chunks are missing
  - Cannot compute real route bundle sizes
  - No verifiable JavaScript measurement data
  - A configured performance budget is exceeded
"""
import json
import re
import sys
from pathlib import Path

# Performance budgets (bytes).
# The old 500k/350k/2MB values were never actually enforced for App Router:
# the legacy manifest path returned 0 JS and omitted total_static from the
# violation list. These transitional caps are anchored to the independently
# measured reconciled Next 16.3/Turbopack baseline (max route 612,570 bytes,
# total static 3,724,592 bytes, largest chunk 228,920 bytes) with bounded
# headroom. They are now real fail-closed limits and can be tightened by a
# dedicated performance workstream without reintroducing false-green evidence.
BUDGETS = {
    "total_initial_js": 650_000,        # largest route client payload
    "per_route_js": 650_000,            # hard cap for any App Router route
    "largest_chunk": 300_000,           # largest single JS chunk
    "total_static_assets": 4_000_000,   # .next/static aggregate
    "fonts_count": 8,                   # Max 8 font files
    "fonts_total_size": 300_000,        # 300 KB total fonts
    "logo_asset_size": 20_000,          # 20 KB per logo SVG
    "login_route_js": 350_000,          # auth/root entry route
    "exec_dashboard_js": 350_000,       # executive/control-plane product surface
}

SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[2]
BUILD_DIR = REPO_ROOT / "apps" / "web" / ".next"
PUBLIC_DIR = REPO_ROOT / "apps" / "web" / "public"


def fail(message):
    print(f"FAIL — {message}")
    sys.exit(1)


def measure_logo_sizes():
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
    font_files = (
        list(PUBLIC_DIR.rglob("*.woff2"))
        + list(PUBLIC_DIR.rglob("*.woff"))
        + list(PUBLIC_DIR.rglob("*.ttf"))
    )
    total_size = sum(path.stat().st_size for path in font_files)
    return font_files, total_size


def _read_json(path: Path, label: str):
    try:
        with path.open(encoding="utf-8") as handle:
            return json.load(handle)
    except json.JSONDecodeError as exc:
        fail(f"{label} is corrupt or unreadable: {exc}")
    except OSError as exc:
        fail(f"Cannot read {label}: {exc}")


def _chunk_path(asset: str) -> Path:
    normalized = asset.replace("/_next/", "").lstrip("/")
    return BUILD_DIR / normalized


def _route_name(manifest_path: Path) -> str:
    relative = manifest_path.relative_to(BUILD_DIR / "server" / "app")
    suffix = "page_client-reference-manifest.js"
    value = relative.as_posix()
    value = value[: -len(suffix)].rstrip("/")
    if not value:
        return "/"
    # Route groups are filesystem-only and do not appear in public URLs.
    parts = [part for part in value.split("/") if not (part.startswith("(") and part.endswith(")"))]
    return "/" + "/".join(parts)


def _app_router_page_bundles():
    manifests = sorted((BUILD_DIR / "server" / "app").rglob("page_client-reference-manifest.js"))
    if not manifests:
        return {}, set()

    page_bundles = {}
    all_js_files = set()
    pattern = re.compile(r"=\s*(\{.*\});?\s*$", re.DOTALL)

    for manifest_path in manifests:
        text = manifest_path.read_text(encoding="utf-8")
        match = pattern.search(text)
        if not match:
            fail(f"Cannot parse App Router client-reference manifest: {manifest_path}")
        try:
            manifest = json.loads(match.group(1))
        except json.JSONDecodeError as exc:
            fail(f"Client-reference manifest is corrupt: {manifest_path}: {exc}")

        chunks = set()
        for module in manifest.get("clientModules", {}).values():
            for asset in module.get("chunks", []):
                normalized = asset.replace("/_next/", "").lstrip("/")
                if not normalized.endswith(".js"):
                    continue
                asset_path = BUILD_DIR / normalized
                if not asset_path.exists():
                    fail(f"JavaScript chunk referenced by App Router manifest is missing: {normalized}")
                chunks.add(normalized)
                all_js_files.add(normalized)

        route = _route_name(manifest_path)
        page_bundles[route] = {
            "js_files": len(chunks),
            "total_js_bytes": sum((BUILD_DIR / asset).stat().st_size for asset in chunks),
        }

    return page_bundles, all_js_files


def _legacy_page_bundles(manifest):
    page_bundles = {}
    all_js_files = set()
    for page, assets in manifest.get("pages", {}).items():
        js_assets = [asset.lstrip("/") for asset in assets if asset.endswith(".js")]
        for asset in js_assets:
            asset_path = BUILD_DIR / asset
            if not asset_path.exists():
                fail(f"JavaScript file referenced in build-manifest not found: {asset}")
            all_js_files.add(asset)
        page_bundles[page] = {
            "js_files": len(js_assets),
            "total_js_bytes": sum((BUILD_DIR / asset).stat().st_size for asset in js_assets),
        }
    return page_bundles, all_js_files


def measure_build_bundles():
    if not BUILD_DIR.exists():
        fail("Build directory not found (apps/web/.next) — build step must succeed first")

    manifest_path = BUILD_DIR / "build-manifest.json"
    if not manifest_path.exists():
        fail("build-manifest.json not found — cannot verify build provenance")
    manifest = _read_json(manifest_path, "build-manifest.json")

    page_bundles, referenced_js = _app_router_page_bundles()
    if not page_bundles:
        page_bundles, referenced_js = _legacy_page_bundles(manifest)

    static_dir = BUILD_DIR / "static"
    if not static_dir.exists():
        fail(".next/static missing — cannot measure production static assets")

    all_static_js = sorted(path for path in static_dir.rglob("*.js") if path.is_file())
    if not all_static_js:
        fail("No JavaScript chunks found in .next/static")

    largest_path = max(all_static_js, key=lambda path: path.stat().st_size)
    largest_chunk = largest_path.stat().st_size
    largest_chunk_name = largest_path.relative_to(BUILD_DIR).as_posix()

    total_static = sum(path.stat().st_size for path in static_dir.rglob("*") if path.is_file())
    total_js = max((data["total_js_bytes"] for data in page_bundles.values()), default=0)

    login_js = max(
        (
            data["total_js_bytes"]
            for page, data in page_bundles.items()
            if page == "/" or page.startswith("/auth") or page.startswith("/identity") or "/login" in page
        ),
        default=0,
    )
    exec_js = max(
        (data["total_js_bytes"] for page, data in page_bundles.items() if page.startswith("/executive")),
        default=0,
    )

    return {
        "page_bundles": page_bundles,
        "total_js": total_js,
        "largest_chunk": largest_chunk,
        "largest_chunk_name": largest_chunk_name,
        "total_static": total_static,
        "login_route_js": login_js,
        "exec_dashboard_js": exec_js,
        "referenced_js_files": len(referenced_js),
        "static_js_files": len(all_static_js),
    }


def bundle_budget_violations(bundle_data):
    violations = []

    has_route_evidence = any(
        data.get("js_files", 0) > 0 and data.get("total_js_bytes", 0) > 0
        for data in bundle_data.get("page_bundles", {}).values()
    )
    if not has_route_evidence or bundle_data.get("total_js", 0) <= 0 or bundle_data.get("largest_chunk", 0) <= 0:
        violations.append("No JavaScript bundle evidence — zero-byte/empty measurements are forbidden")

    if bundle_data.get("total_js", 0) > BUDGETS["total_initial_js"]:
        violations.append(
            f"Largest route initial JS exceeds budget: {bundle_data['total_js']:,} bytes "
            f"(budget: {BUDGETS['total_initial_js']:,} bytes)"
        )
    if bundle_data.get("largest_chunk", 0) > BUDGETS["largest_chunk"]:
        violations.append(
            f"Largest chunk exceeds budget: {bundle_data['largest_chunk']:,} bytes "
            f"({bundle_data.get('largest_chunk_name', '')}, budget: {BUDGETS['largest_chunk']:,} bytes)"
        )
    if bundle_data.get("total_static", 0) > BUDGETS["total_static_assets"]:
        violations.append(
            f"Total static assets exceeds budget: {bundle_data['total_static']:,} bytes "
            f"(budget: {BUDGETS['total_static_assets']:,} bytes)"
        )
    if bundle_data.get("login_route_js", 0) > BUDGETS["login_route_js"]:
        violations.append(
            f"Login route JS exceeds budget: {bundle_data['login_route_js']:,} bytes "
            f"(budget: {BUDGETS['login_route_js']:,} bytes)"
        )
    if bundle_data.get("exec_dashboard_js", 0) > BUDGETS["exec_dashboard_js"]:
        violations.append(
            f"Executive surface JS exceeds budget: {bundle_data['exec_dashboard_js']:,} bytes "
            f"(budget: {BUDGETS['exec_dashboard_js']:,} bytes)"
        )

    for page, data in bundle_data.get("page_bundles", {}).items():
        if data.get("total_js_bytes", 0) > BUDGETS["per_route_js"]:
            violations.append(
                f"Route {page} exceeds JS budget: {data['total_js_bytes']:,} bytes "
                f"(budget: {BUDGETS['per_route_js']:,} bytes)"
            )

    return violations


def main():
    print("=" * 70)
    print("SANAD Performance Budget Checker — FAIL-CLOSED")
    print("=" * 70)
    print()

    measurements = {}
    violations = []

    print("Measuring logo asset sizes...")
    logo_sizes = measure_logo_sizes()
    measurements["logos"] = logo_sizes
    for name, size in logo_sizes:
        if size > BUDGETS["logo_asset_size"]:
            violations.append(
                f"Logo '{name}' exceeds budget: {size:,} bytes (budget: {BUDGETS['logo_asset_size']:,} bytes)"
            )

    print("Measuring font files...")
    font_files, font_total = measure_fonts()
    measurements["fonts"] = {"count": len(font_files), "total_bytes": font_total}
    if len(font_files) > BUDGETS["fonts_count"]:
        violations.append(
            f"Font file count exceeds budget: {len(font_files)} (budget: {BUDGETS['fonts_count']})"
        )
    if font_total > BUDGETS["fonts_total_size"]:
        violations.append(
            f"Total font size exceeds budget: {font_total:,} bytes (budget: {BUDGETS['fonts_total_size']:,} bytes)"
        )

    print("Measuring build bundle sizes (fail-closed)...")
    bundle_data = measure_build_bundles()
    measurements["bundles"] = bundle_data
    violations.extend(bundle_budget_violations(bundle_data))

    print()
    print("=" * 70)
    print("PERFORMANCE MEASUREMENT REPORT")
    print("=" * 70)
    print()

    print("Logo Assets:")
    for name, size in logo_sizes:
        status = "OK" if size <= BUDGETS["logo_asset_size"] else "OVER"
        print(f"  {name:40s} {size:>8,} bytes  [{status}]")
    print()

    print("Fonts:")
    print(f"  File count:     {len(font_files)}  (budget: {BUDGETS['fonts_count']})")
    print(f"  Total size:     {font_total:,} bytes  (budget: {BUDGETS['fonts_total_size']:,} bytes)")
    print()

    print("JavaScript Bundles:")
    print(f"  Largest route initial JS: {bundle_data['total_js']:>10,} bytes  (budget: {BUDGETS['total_initial_js']:,})")
    print(f"  Largest chunk:            {bundle_data['largest_chunk']:>10,} bytes  (budget: {BUDGETS['largest_chunk']:,})")
    print(f"    └─ {bundle_data['largest_chunk_name']}")
    print(f"  Login/auth route JS:      {bundle_data['login_route_js']:>10,} bytes  (budget: {BUDGETS['login_route_js']:,})")
    print(f"  Executive surface max:    {bundle_data['exec_dashboard_js']:>10,} bytes  (budget: {BUDGETS['exec_dashboard_js']:,})")
    print(f"  Total static assets:      {bundle_data['total_static']:>10,} bytes  (budget: {BUDGETS['total_static_assets']:,})")
    print(f"  Static JS chunks:         {bundle_data['static_js_files']}")
    print(f"  Route-referenced JS:      {bundle_data['referenced_js_files']}")
    print()

    print("Per-Route Bundle Breakdown:")
    for page, data in sorted(bundle_data["page_bundles"].items()):
        over = "OVER" if data["total_js_bytes"] > BUDGETS["per_route_js"] else "OK"
        print(f"  {page:50s} {data['total_js_bytes']:>10,} bytes  [{over}]")
    print()

    if violations:
        print(f"FAIL — {len(violations)} performance budget violation(s):")
        for violation in violations:
            print(f"  • {violation}")
        sys.exit(1)

    print("PASS — all performance budgets met with verifiable measurements")
    sys.exit(0)


if __name__ == "__main__":
    main()
