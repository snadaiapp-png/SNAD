#!/usr/bin/env python3
"""
SANAD — OWASP Dependency-Check JSON Report Parser
===================================================
EXEC-PROMPT-010R3 Section 6: Extracted parser supporting both
Dependency-Check JSON schemas (list and object form).

Usage:
    python3 scripts/ci/parse-owasp-report.py <json-file>

Outputs key=value lines suitable for $GITHUB_OUTPUT:
    result=pass|failed|execution-error
    total-dependencies=N
    total-vulnerabilities=N
    low=N
    medium=N
    high=N
    critical=N
    suppressed=N
    analysis-exceptions=N
    deps-with-vulns=N

Exit codes:
    0 = parsed successfully (regardless of finding count)
    1 = file not found or unreadable
    2 = JSON parse error
    3 = unsupported schema
"""

import json
import sys
import os


def normalize_dependencies(report):
    """
    Normalize the dependencies node from either list or object form.

    Supports:
        {"dependencies": []}                     — list form
        {"dependencies": {"dependency": []}}     — object form

    Raises ValueError for unsupported schemas.
    """
    dependencies_node = report.get("dependencies", [])

    if isinstance(dependencies_node, list):
        dependencies = dependencies_node
    elif isinstance(dependencies_node, dict):
        dependencies = dependencies_node.get("dependency", [])
    else:
        raise ValueError(
            f"Unsupported dependencies node type: "
            f"{type(dependencies_node).__name__}"
        )

    if not isinstance(dependencies, list):
        raise ValueError(
            f"Normalized dependencies value is not a list: "
            f"{type(dependencies).__name__}"
        )

    return dependencies


def normalize_vulnerabilities(dep):
    """
    Normalize the vulnerabilities node from a dependency.

    Supports:
        Missing key       → []
        null              → []
        list              → list
        dict with nested  → list (uncommon but defensive)
    """
    vulns = dep.get("vulnerabilities", [])

    if vulns is None:
        return []
    if isinstance(vulns, list):
        return vulns
    if isinstance(vulns, dict):
        # Some schemas nest under "vulnerability"
        nested = vulns.get("vulnerability", [])
        if isinstance(nested, list):
            return nested
        return [nested] if nested else []
    # Unknown type — treat as empty rather than crash
    return []


def get_cvss_score(vuln):
    """Extract CVSS score from either cvssv3 or cvssv2."""
    for key in ("cvssv3", "cvssv2"):
        cvss = vuln.get(key)
        if isinstance(cvss, dict):
            score = cvss.get("baseScore")
            if isinstance(score, (int, float)):
                return float(score)
        elif isinstance(cvss, (int, float)):
            return float(cvss)
    return 0.0


def severity_from_score(score):
    """Map CVSS score to severity bucket."""
    if score >= 9.0:
        return "CRITICAL"
    elif score >= 7.0:
        return "HIGH"
    elif score >= 4.0:
        return "MEDIUM"
    elif score > 0:
        return "LOW"
    return "UNKNOWN"


def get_severity(vuln):
    """Determine severity from vuln entry — prefer explicit, fall back to CVSS."""
    severity = vuln.get("severity", "").upper()
    if severity in ("CRITICAL", "HIGH", "MEDIUM", "MODERATE", "LOW"):
        if severity == "MODERATE":
            severity = "MEDIUM"
        return severity

    # Fall back to CVSS score
    score = get_cvss_score(vuln)
    return severity_from_score(score)


def count_suppressed(dep):
    """Count suppressed vulnerabilities for a dependency."""
    count = 0
    # Check top-level suppressedVulnerabilities list
    suppressed_list = dep.get("suppressedVulnerabilities", [])
    if isinstance(suppressed_list, list):
        count += len(suppressed_list)

    # Check per-vulnerability suppression entries
    for vuln in normalize_vulnerabilities(dep):
        sup_entries = vuln.get("suppressedVulnerabilities", [])
        if isinstance(sup_entries, list):
            count += len(sup_entries)
        if vuln.get("suppressed", False):
            count += 1

    return count


def count_analysis_exceptions(dep):
    """Count analysis exceptions for a dependency."""
    exceptions = dep.get("analysisExceptions", [])
    if isinstance(exceptions, list):
        return len(exceptions)
    return 0


def parse_report(json_path):
    """
    Parse an OWASP Dependency-Check JSON report.

    Returns a dict with all counts, or raises on error.
    """
    if not os.path.isfile(json_path):
        raise FileNotFoundError(f"Report file not found: {json_path}")

    with open(json_path, "r", encoding="utf-8") as f:
        try:
            report = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(f"JSON parse error: {e}")

    # Normalize dependencies
    try:
        dependencies = normalize_dependencies(report)
    except ValueError as e:
        raise ValueError(f"Schema validation failed: {e}")

    total_deps = len(dependencies)
    severity_counts = {"LOW": 0, "MEDIUM": 0, "HIGH": 0, "CRITICAL": 0, "UNKNOWN": 0}
    total_vulns = 0
    suppressed = 0
    analysis_exceptions = 0
    deps_with_vulns = 0

    for dep in dependencies:
        vulns = normalize_vulnerabilities(dep)
        if vulns:
            deps_with_vulns += 1

        for vuln in vulns:
            total_vulns += 1
            severity = get_severity(vuln)
            if severity in severity_counts:
                severity_counts[severity] += 1
            else:
                severity_counts["UNKNOWN"] += 1

        suppressed += count_suppressed(dep)
        analysis_exceptions += count_analysis_exceptions(dep)

    # Determine result
    high = severity_counts["HIGH"]
    critical = severity_counts["CRITICAL"]

    if high > 0 or critical > 0:
        result = "failed"
    else:
        result = "pass"

    return {
        "result": result,
        "total-dependencies": total_deps,
        "total-vulnerabilities": total_vulns,
        "low": severity_counts["LOW"],
        "medium": severity_counts["MEDIUM"],
        "high": high,
        "critical": critical,
        "suppressed": suppressed,
        "analysis-exceptions": analysis_exceptions,
        "deps-with-vulns": deps_with_vulns,
    }


def main():
    if len(sys.argv) != 2:
        print("Usage: parse-owasp-report.py <json-file>", file=sys.stderr)
        sys.exit(1)

    json_path = sys.argv[1]

    try:
        result = parse_report(json_path)
    except FileNotFoundError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
    except ValueError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(3)

    # Output as key=value lines
    for key, value in result.items():
        print(f"{key}={value}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
