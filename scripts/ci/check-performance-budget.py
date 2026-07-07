#!/usr/bin/env python3
"""SNAD route-level performance budget and regression gate."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD_DIR = ROOT / "apps/web/.next"
PUBLIC_DIR = ROOT / "apps/web/public"
BASELINE_FILE = ROOT / "apps/web/performance-baseline.json"

ABSOLUTE_ROUTE_BUDGETS = {
    "/": 620_000,
    "/workspace": 620_000,
    "/control-plane": 650_000,
    "/crm": 620_000,
}
MAX_REGRESSION_RATIO = 1.08
MAX_LOGO_BYTES = 100_000
MAX_FONT_FILES = 8
MAX_FONT_BYTES = 300_000


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> int:
    violations: list[str] = []
    warnings: list[str] = []

    official_assets = [
        PUBLIC_DIR / "assets/brand/snad-logo-official-primary.webp",
        PUBLIC_DIR / "assets/brand/snad-logo-official-wordmark.webp",
    ]
    for asset in official_assets:
        if not asset.is_file():
            violations.append(f"missing logo asset: {asset.relative_to(ROOT)}")
        elif asset.stat().st_size > MAX_LOGO_BYTES:
            violations.append(
                f"logo asset exceeds {MAX_LOGO_BYTES:,} bytes: "
                f"{asset.name}={asset.stat().st_size:,}"
            )

    fonts = [
        path for suffix in ("*.woff2", "*.woff", "*.ttf")
        for path in PUBLIC_DIR.rglob(suffix)
    ]
    if len(fonts) > MAX_FONT_FILES:
        violations.append(f"font file count {len(fonts)} > {MAX_FONT_FILES}")
    total_font_bytes = sum(path.stat().st_size for path in fonts)
    if total_font_bytes > MAX_FONT_BYTES:
        violations.append(f"font bytes {total_font_bytes:,} > {MAX_FONT_BYTES:,}")

    stats_path = BUILD_DIR / "diagnostics/route-bundle-stats.json"
    if not stats_path.is_file():
        violations.append(
            "route-bundle-stats.json is missing; route budgets cannot be verified"
        )
    else:
        stats = {
            item["route"]: int(item["firstLoadUncompressedJsBytes"])
            for item in load_json(stats_path)
        }
        baseline = load_json(BASELINE_FILE) if BASELINE_FILE.is_file() else {}

        print("SNAD route bundle evidence:")
        for route, absolute_budget in ABSOLUTE_ROUTE_BUDGETS.items():
            current = stats.get(route)
            if current is None:
                violations.append(f"route missing from bundle diagnostics: {route}")
                continue
            previous = baseline.get(route)
            print(
                f"  {route:<16} current={current:,} "
                f"budget={absolute_budget:,} baseline={previous or 'n/a'}"
            )
            if current > absolute_budget:
                violations.append(
                    f"{route} first-load JS {current:,} > {absolute_budget:,}"
                )
            if isinstance(previous, int):
                regression_limit = int(previous * MAX_REGRESSION_RATIO)
                if current > regression_limit:
                    violations.append(
                        f"{route} regressed by more than 8% "
                        f"({previous:,} -> {current:,})"
                    )

        unknown = sorted(set(stats) - set(ABSOLUTE_ROUTE_BUDGETS))
        if unknown:
            warnings.append("unbudgeted routes: " + ", ".join(unknown))

    for warning in warnings:
        print(f"WARNING: {warning}")

    if violations:
        print("SNAD Performance Budget: FAIL")
        for violation in violations:
            print(f"  - {violation}")
        return 1

    print("SNAD Performance Budget: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
