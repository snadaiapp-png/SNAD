#!/usr/bin/env python3
"""
SANAD — OWASP Parser Tests
============================
EXEC-PROMPT-010R3 Section 10: Deterministic tests for the OWASP
report parser covering both JSON schemas, severity detection,
suppression counting, and error handling.

Run:
    python3 tests/ci/test_parse_owasp.py
"""

import os
import sys
import json

# Add scripts/ci to path
SCRIPT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "ci")
sys.path.insert(0, SCRIPT_DIR)

from parse_owasp_report import parse_report, normalize_dependencies, normalize_vulnerabilities, get_severity, severity_from_score

FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures", "owasp")

TESTS_PASSED = 0
TESTS_FAILED = 0


def assert_eq(actual, expected, label):
    global TESTS_PASSED, TESTS_FAILED
    if actual == expected:
        TESTS_PASSED += 1
        print(f"  ✅ {label}: {actual}")
    else:
        TESTS_FAILED += 1
        print(f"  ❌ {label}: expected={expected}, actual={actual}")


def assert_true(value, label):
    global TESTS_PASSED, TESTS_FAILED
    if value:
        TESTS_PASSED += 1
        print(f"  ✅ {label}")
    else:
        TESTS_FAILED += 1
        print(f"  ❌ {label}: expected True, got False")


def assert_raises(func, args, label):
    global TESTS_PASSED, TESTS_FAILED
    try:
        func(*args)
        TESTS_FAILED += 1
        print(f"  ❌ {label}: expected exception but none raised")
    except (ValueError, TypeError):
        TESTS_PASSED += 1
        print(f"  ✅ {label}: raised as expected")
    except Exception as e:
        TESTS_FAILED += 1
        print(f"  ❌ {label}: expected ValueError, got {type(e).__name__}")


def fixture(name):
    return os.path.join(FIXTURES_DIR, name)


def test_empty_report():
    print("\n--- Test: Empty report ---")
    result = parse_report(fixture("empty-report.json"))
    assert_eq(result["result"], "pass", "result")
    assert_eq(result["total-dependencies"], 0, "total-dependencies")
    assert_eq(result["total-vulnerabilities"], 0, "total-vulnerabilities")
    assert_eq(result["high"], 0, "high")
    assert_eq(result["critical"], 0, "critical")


def test_list_form_dependencies():
    print("\n--- Test: List-form dependencies ---")
    result = parse_report(fixture("dependencies-list.json"))
    assert_eq(result["total-dependencies"], 2, "total-dependencies")
    assert_eq(result["total-vulnerabilities"], 0, "total-vulnerabilities")
    assert_eq(result["result"], "pass", "result")


def test_object_form_dependencies():
    print("\n--- Test: Object-form dependencies ---")
    result = parse_report(fixture("dependencies-object.json"))
    assert_eq(result["total-dependencies"], 2, "total-dependencies")
    assert_eq(result["total-vulnerabilities"], 0, "total-vulnerabilities")
    assert_eq(result["result"], "pass", "result")


def test_high_finding():
    print("\n--- Test: HIGH finding ---")
    result = parse_report(fixture("high-finding.json"))
    assert_eq(result["result"], "failed", "result")
    assert_eq(result["high"], 1, "high")
    assert_eq(result["critical"], 0, "critical")
    assert_eq(result["total-vulnerabilities"], 1, "total-vulnerabilities")
    assert_eq(result["deps-with-vulns"], 1, "deps-with-vulns")


def test_critical_finding():
    print("\n--- Test: CRITICAL finding ---")
    result = parse_report(fixture("critical-finding.json"))
    assert_eq(result["result"], "failed", "result")
    assert_eq(result["high"], 0, "high")
    assert_eq(result["critical"], 1, "critical")
    assert_eq(result["total-vulnerabilities"], 1, "total-vulnerabilities")


def test_suppressed_and_exceptions():
    print("\n--- Test: Suppressed + exceptions + null vulns ---")
    result = parse_report(fixture("suppressed-and-exceptions.json"))
    assert_eq(result["total-dependencies"], 4, "total-dependencies")
    assert_eq(result["total-vulnerabilities"], 1, "total-vulnerabilities (active only)")
    assert_eq(result["high"], 1, "high")
    assert_eq(result["suppressed"], 1, "suppressed")
    assert_eq(result["analysis-exceptions"], 1, "analysis-exceptions")
    assert_eq(result["result"], "failed", "result (HIGH > 0)")


def test_invalid_schema():
    print("\n--- Test: Invalid schema ---")
    assert_raises(parse_report, (fixture("invalid-schema.json"),), "invalid schema raises ValueError")


def test_missing_file():
    global TESTS_PASSED, TESTS_FAILED
    print("\n--- Test: Missing file ---")
    try:
        parse_report("/nonexistent/path/to/report.json")
        TESTS_FAILED += 1
        print("  ❌ Missing file: expected FileNotFoundError")
    except FileNotFoundError:
        TESTS_PASSED += 1
        print("  ✅ Missing file: raised FileNotFoundError")


def test_severity_from_score():
    print("\n--- Test: severity_from_score ---")
    assert_eq(severity_from_score(9.8), "CRITICAL", "9.8 → CRITICAL")
    assert_eq(severity_from_score(9.0), "CRITICAL", "9.0 → CRITICAL")
    assert_eq(severity_from_score(7.5), "HIGH", "7.5 → HIGH")
    assert_eq(severity_from_score(7.0), "HIGH", "7.0 → HIGH")
    assert_eq(severity_from_score(6.9), "MEDIUM", "6.9 → MEDIUM")
    assert_eq(severity_from_score(4.0), "MEDIUM", "4.0 → MEDIUM")
    assert_eq(severity_from_score(3.9), "LOW", "3.9 → LOW")
    assert_eq(severity_from_score(0.1), "LOW", "0.1 → LOW")
    assert_eq(severity_from_score(0.0), "UNKNOWN", "0.0 → UNKNOWN")


def test_normalize_vulnerabilities():
    print("\n--- Test: normalize_vulnerabilities ---")
    assert_eq(len(normalize_vulnerabilities({"vulnerabilities": []})), 0, "empty list")
    assert_eq(len(normalize_vulnerabilities({"vulnerabilities": None})), 0, "null")
    assert_eq(len(normalize_vulnerabilities({})), 0, "missing key")
    assert_eq(len(normalize_vulnerabilities({"vulnerabilities": [{"name": "CVE-1"}]})), 1, "single vuln")


def main():
    print("=" * 60)
    print("SANAD OWASP Parser Tests")
    print("=" * 60)

    test_empty_report()
    test_list_form_dependencies()
    test_object_form_dependencies()
    test_high_finding()
    test_critical_finding()
    test_suppressed_and_exceptions()
    test_invalid_schema()
    test_missing_file()
    test_severity_from_score()
    test_normalize_vulnerabilities()

    print("\n" + "=" * 60)
    print(f"Tests passed: {TESTS_PASSED}")
    print(f"Tests failed: {TESTS_FAILED}")
    print("=" * 60)

    return 0 if TESTS_FAILED == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
