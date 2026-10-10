#!/usr/bin/env python3
"""
SANAD Architecture Protection — Orchestrator
=============================================

Runs all architecture gate checks in sequence and aggregates results.
Exit code is 0 only if EVERY check passes.

Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md §10, §15
"""

from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
CHECKS = [
    ("backend-boundaries",  SCRIPTS_DIR / "check_backend_boundaries.py"),
    ("frontend-boundaries", SCRIPTS_DIR / "check_frontend_boundaries.py"),
    ("tenant-hardcoding",  SCRIPTS_DIR / "check_tenant_hardcoding.py"),
    ("protected-dirs",     SCRIPTS_DIR / "check_protected_directories.py"),
]


def run_check(name: str, script: Path) -> tuple[bool, float]:
    t0 = time.time()
    print(f"\n{'='*70}")
    print(f"  ARCHITECTURE GATE: {name}")
    print(f"{'='*70}")
    try:
        result = subprocess.run(
            [sys.executable, str(script)],
            check=False,
        )
        elapsed = time.time() - t0
        ok = result.returncode == 0
        status = "PASS" if ok else "FAIL"
        print(f"[{status}] {name} ({elapsed:.2f}s)")
        return ok, elapsed
    except (subprocess.SubprocessError, OSError) as e:
        elapsed = time.time() - t0
        print(f"[ERROR] {name} — {e}")
        return False, elapsed


def main() -> int:
    print("=" * 70)
    print("  SANAD ARCHITECTURE PROTECTION GATE — ORCHESTRATOR")
    print("  Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md")
    print("=" * 70)

    results = []
    total_t0 = time.time()
    for name, script in CHECKS:
        if not script.exists():
            print(f"[SKIP] {name} — script not found: {script}")
            results.append((name, False, 0.0, "script-missing"))
            continue
        ok, elapsed = run_check(name, script)
        results.append((name, ok, elapsed, "ok" if ok else "fail"))

    total_elapsed = time.time() - total_t0

    print("\n" + "=" * 70)
    print("  ARCHITECTURE GATE SUMMARY")
    print("=" * 70)
    print(f"  {'CHECK':<28} {'STATUS':<8} {'TIME':<10}")
    print(f"  {'-'*28} {'-'*8} {'-'*10}")
    all_passed = True
    for name, ok, elapsed, _ in results:
        status = "PASS" if ok else "FAIL"
        if not ok:
            all_passed = False
        print(f"  {name:<28} {status:<8} {elapsed:>6.2f}s")
    print(f"\n  Total elapsed: {total_elapsed:.2f}s")
    print("=" * 70)

    if all_passed:
        print("\n  ALL ARCHITECTURE GATES PASSED — merge allowed")
        return 0
    else:
        print("\n  ONE OR MORE ARCHITECTURE GATES FAILED — merge blocked")
        print("     Policy: §16 — Any PR that violates any rule SHALL be automatically rejected")
        print("     No exceptions. No manual override. No temporary bypass.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
