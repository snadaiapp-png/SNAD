#!/usr/bin/env python3
"""
SANAD — NVD Bulk Feed Verifier
================================
EXEC-PROMPT-010R12L Section 15 — verifies a downloaded NVD bulk feed
bundle before consumption by Snapshot Builder.
"""
from __future__ import annotations

import gzip
import json
import os
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_feed_archive import extract_feed_archive, validate_feed_archive
from scripts.security.nvd_bulk_feed_mirror import NVD_YEAR_START, current_year, expected_feed_names


def verify_feed_content(feed_dir: Path) -> dict:
    """Verify all feed files in a directory.

    Returns dict with verification results.
    """
    feed_dir = Path(feed_dir)
    results = {
        "all_years_present": True,
        "modified_present": False,
        "recent_present": False,
        "no_duplicates": True,
        "no_unexpected": True,
        "no_partial": True,
        "no_zero_byte": True,
        "gzip_valid": True,
        "json_valid": True,
        "feed_count": 0,
        "errors": [],
    }

    expected = set(expected_feed_names())
    actual = set()
    for f in feed_dir.iterdir():
        if f.is_file():
            actual.add(f.name)

    # Check all expected feeds present
    missing = expected - actual
    if missing:
        results["all_years_present"] = False
        results["errors"].append(f"Missing feeds: {sorted(missing)}")

    # Check modified and recent
    if "nvdcve-modified.json.gz" not in actual:
        results["errors"].append("Missing nvdcve-modified.json.gz")
    else:
        results["modified_present"] = True

    if "nvdcve-recent.json.gz" not in actual:
        results["errors"].append("Missing nvdcve-recent.json.gz")
    else:
        results["recent_present"] = True

    # Check for unexpected files
    unexpected = actual - expected
    if unexpected:
        results["no_unexpected"] = False
        results["errors"].append(f"Unexpected files: {sorted(unexpected)}")

    # Check each file
    for f in sorted(feed_dir.iterdir()):
        if not f.is_file():
            continue
        if f.name not in expected:
            continue

        results["feed_count"] += 1

        # Zero-byte check
        if f.stat().st_size == 0:
            results["no_zero_byte"] = False
            results["errors"].append(f"Zero-byte file: {f.name}")

        # Partial file check
        if f.name.endswith(".part"):
            results["no_partial"] = False
            results["errors"].append(f"Partial file: {f.name}")

        # Gzip + JSON validation
        if f.name.endswith(".json.gz"):
            try:
                with gzip.open(f, "rt", encoding="utf-8") as gz:
                    data = json.load(gz)
                    if not isinstance(data, dict):
                        results["json_valid"] = False
                        results["errors"].append(f"{f.name}: root is not JSON object")
            except Exception as e:
                results["gzip_valid"] = False
                results["json_valid"] = False
                results["errors"].append(f"{f.name}: {e}")

    results["valid"] = (
        results["all_years_present"]
        and results["modified_present"]
        and results["no_unexpected"]
        and results["no_partial"]
        and results["no_zero_byte"]
        and results["gzip_valid"]
        and results["json_valid"]
    )

    return results
