#!/usr/bin/env python3
"""
SANAD — OWASP Parser Tests (unittest)
=======================================
EXEC-PROMPT-010R4 Section 9: 20 explicit test scenarios using unittest.

Run:
    python3 -m pytest tests/ci/test_parse_owasp.py -q
or:
    python3 tests/ci/test_parse_owasp.py
"""

import json
import os
import sys
import tempfile
import unittest

# Add scripts/ci to path
SCRIPT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "ci")
sys.path.insert(0, SCRIPT_DIR)

from parse_owasp_report import (
    parse_report,
    validate_report_root,
    normalize_dependencies,
    normalize_vulnerabilities,
    get_severity,
    severity_from_score,
    get_cvss_score,
    collect_suppressions,
)

FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures", "owasp")


def fixture(name):
    return os.path.join(FIXTURES_DIR, name)


def write_temp_report(data):
    """Write a dict to a temp JSON file and return the path."""
    fd, path = tempfile.mkstemp(suffix=".json")
    with os.fdopen(fd, "w") as f:
        json.dump(data, f)
    return path


class TestScenario1_ListFormDependencies(unittest.TestCase):
    """Scenario 1: Valid list-form dependencies."""

    def test_list_form(self):
        path = fixture("dependencies-list.json")
        result = parse_report(path)
        self.assertEqual(result["total_dependencies"], 2)
        self.assertEqual(result["result"], "pass")
        self.assertEqual(result["high"], 0)
        self.assertEqual(result["critical"], 0)
        self.assertEqual(result["unknown"], 0)
        self.assertEqual(result["analysis_exceptions"], 0)


class TestScenario2_ObjectFormDependencies(unittest.TestCase):
    """Scenario 2: Valid object-form dependencies."""

    def test_object_form(self):
        path = fixture("dependencies-object.json")
        result = parse_report(path)
        self.assertEqual(result["total_dependencies"], 2)
        self.assertEqual(result["result"], "pass")


class TestScenario3_MissingDependencies(unittest.TestCase):
    """Scenario 3: Missing dependencies key → execution_error."""

    def test_missing_dependencies(self):
        path = write_temp_report({"projectInfo": {"name": "test"}})
        with self.assertRaises(ValueError) as ctx:
            parse_report(path)
        self.assertIn("missing dependencies", str(ctx.exception))
        os.unlink(path)


class TestScenario4_ZeroDependencies(unittest.TestCase):
    """Scenario 4: Zero dependencies → execution_error (NOT pass)."""

    def test_zero_dependencies(self):
        path = fixture("empty-report.json")
        result = parse_report(path)
        self.assertEqual(result["total_dependencies"], 0)
        self.assertEqual(result["result"], "execution_error")


class TestScenario5_InvalidDependenciesNode(unittest.TestCase):
    """Scenario 5: Invalid dependencies node type → ValueError."""

    def test_string_dependencies(self):
        path = fixture("invalid-schema.json")
        with self.assertRaises(ValueError) as ctx:
            parse_report(path)
        self.assertIn("Unsupported dependencies node type", str(ctx.exception))


class TestScenario6_InvalidDependencyItem(unittest.TestCase):
    """Scenario 6: Invalid dependency item (not object) → ValueError."""

    def test_string_dependency_item(self):
        path = write_temp_report({"dependencies": ["not-an-object"]})
        with self.assertRaises(ValueError) as ctx:
            parse_report(path)
        self.assertIn("must be an object", str(ctx.exception))
        os.unlink(path)


class TestScenario7_MissingVulnerabilities(unittest.TestCase):
    """Scenario 7: Missing vulnerabilities key → empty list (no crash)."""

    def test_missing_vulns(self):
        path = write_temp_report({
            "dependencies": [{"fileName": "lib.jar"}]
        })
        result = parse_report(path)
        self.assertEqual(result["total_vulnerabilities"], 0)
        self.assertEqual(result["result"], "pass")
        os.unlink(path)


class TestScenario8_NullVulnerabilities(unittest.TestCase):
    """Scenario 8: Null vulnerabilities → empty list."""

    def test_null_vulns(self):
        path = write_temp_report({
            "dependencies": [{"fileName": "lib.jar", "vulnerabilities": None}]
        })
        result = parse_report(path)
        self.assertEqual(result["total_vulnerabilities"], 0)
        os.unlink(path)


class TestScenario9_InvalidVulnerabilitiesNode(unittest.TestCase):
    """Scenario 9: Invalid vulnerabilities node type → ValueError."""

    def test_string_vulns(self):
        path = write_temp_report({
            "dependencies": [{"fileName": "lib.jar", "vulnerabilities": "string"}]
        })
        with self.assertRaises(ValueError) as ctx:
            parse_report(path)
        self.assertIn("Unsupported vulnerabilities node type", str(ctx.exception))
        os.unlink(path)


class TestScenario10_InvalidVulnerabilityItem(unittest.TestCase):
    """Scenario 10: Invalid vulnerability item (not object) → ValueError."""

    def test_string_vuln_item(self):
        path = write_temp_report({
            "dependencies": [{"fileName": "lib.jar", "vulnerabilities": ["string"]}]
        })
        with self.assertRaises(ValueError) as ctx:
            parse_report(path)
        self.assertIn("must be an object", str(ctx.exception))
        os.unlink(path)


class TestScenario11_LowFinding(unittest.TestCase):
    """Scenario 11: LOW finding."""

    def test_low(self):
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [{"name": "CVE-1", "severity": "LOW"}]
            }]
        })
        result = parse_report(path)
        self.assertEqual(result["low"], 1)
        self.assertEqual(result["result"], "pass")  # LOW doesn't block
        os.unlink(path)


class TestScenario12_MediumFinding(unittest.TestCase):
    """Scenario 12: MEDIUM finding."""

    def test_medium(self):
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [{"name": "CVE-1", "severity": "MEDIUM"}]
            }]
        })
        result = parse_report(path)
        self.assertEqual(result["medium"], 1)
        self.assertEqual(result["result"], "pass")
        os.unlink(path)


class TestScenario13_HighFinding(unittest.TestCase):
    """Scenario 13: HIGH finding → failed."""

    def test_high(self):
        path = fixture("high-finding.json")
        result = parse_report(path)
        self.assertEqual(result["high"], 1)
        self.assertEqual(result["result"], "failed")


class TestScenario14_CriticalFinding(unittest.TestCase):
    """Scenario 14: CRITICAL finding → failed."""

    def test_critical(self):
        path = fixture("critical-finding.json")
        result = parse_report(path)
        self.assertEqual(result["critical"], 1)
        self.assertEqual(result["result"], "failed")


class TestScenario15_UnknownSeverity(unittest.TestCase):
    """Scenario 15: Unknown severity → incomplete."""

    def test_unknown_severity(self):
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [{"name": "CVE-1", "severity": "WTF"}]
            }]
        })
        result = parse_report(path)
        self.assertEqual(result["unknown"], 1)
        self.assertEqual(result["result"], "incomplete")
        os.unlink(path)


class TestScenario16_CVSSFallback(unittest.TestCase):
    """Scenario 16: CVSS score fallback when severity missing."""

    def test_cvss_fallback_high(self):
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [{"name": "CVE-1", "cvssv3": {"baseScore": 7.5}}]
            }]
        })
        result = parse_report(path)
        self.assertEqual(result["high"], 1)
        self.assertEqual(result["result"], "failed")
        os.unlink(path)

    def test_cvss_fallback_critical(self):
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [{"name": "CVE-1", "cvssv3": {"baseScore": 9.8}}]
            }]
        })
        result = parse_report(path)
        self.assertEqual(result["critical"], 1)
        os.unlink(path)


class TestScenario17_AnalysisException(unittest.TestCase):
    """Scenario 17: Analysis exception → incomplete."""

    def test_analysis_exception(self):
        path = fixture("suppressed-and-exceptions.json")
        result = parse_report(path)
        self.assertEqual(result["analysis_exceptions"], 1)
        self.assertEqual(result["result"], "incomplete")


class TestScenario18_DuplicateSuppression(unittest.TestCase):
    """Scenario 18: Duplicate suppression deduplication."""

    def test_duplicate_suppression(self):
        # Same CVE appears as both suppressedVulnerabilities entry and suppressed=true flag
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [
                    {"name": "CVE-1", "severity": "HIGH", "suppressed": True}
                ],
                "suppressedVulnerabilities": [
                    {"name": "CVE-1", "severity": "HIGH"}
                ]
            }]
        })
        result = parse_report(path)
        # Should count as 1 unique suppression, not 2
        self.assertEqual(result["suppressed_unique"], 1)
        # The HIGH vuln is suppressed, so active high should be 1 (it's still counted)
        self.assertEqual(result["high"], 1)
        os.unlink(path)


class TestScenario19_InvalidJSON(unittest.TestCase):
    """Scenario 19: Invalid JSON → execution_error."""

    def test_invalid_json(self):
        fd, path = tempfile.mkstemp(suffix=".json")
        with os.fdopen(fd, "w") as f:
            f.write("{ invalid json }}}")
        with self.assertRaises(ValueError) as ctx:
            parse_report(path)
        self.assertIn("JSON parse error", str(ctx.exception))
        os.unlink(path)


class TestScenario20_MissingReportFile(unittest.TestCase):
    """Scenario 20: Missing report file → FileNotFoundError."""

    def test_missing_file(self):
        with self.assertRaises(FileNotFoundError):
            parse_report("/nonexistent/path/to/report.json")


# ─── Helper function tests ──────────────────────────────────

class TestSeverityFromScore(unittest.TestCase):
    def test_critical_threshold(self):
        self.assertEqual(severity_from_score(9.8), "CRITICAL")
        self.assertEqual(severity_from_score(9.0), "CRITICAL")

    def test_high_threshold(self):
        self.assertEqual(severity_from_score(7.5), "HIGH")
        self.assertEqual(severity_from_score(7.0), "HIGH")

    def test_medium_threshold(self):
        self.assertEqual(severity_from_score(6.9), "MEDIUM")
        self.assertEqual(severity_from_score(4.0), "MEDIUM")

    def test_low_threshold(self):
        self.assertEqual(severity_from_score(3.9), "LOW")
        self.assertEqual(severity_from_score(0.1), "LOW")

    def test_zero_score(self):
        self.assertEqual(severity_from_score(0.0), "UNKNOWN")

    def test_none_score(self):
        self.assertEqual(severity_from_score(None), "UNKNOWN")


class TestModerateAlias(unittest.TestCase):
    def test_moderate_maps_to_medium(self):
        path = write_temp_report({
            "dependencies": [{
                "fileName": "lib.jar",
                "vulnerabilities": [{"name": "CVE-1", "severity": "MODERATE"}]
            }]
        })
        result = parse_report(path)
        self.assertEqual(result["medium"], 1)
        os.unlink(path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
