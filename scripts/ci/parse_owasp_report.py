#!/usr/bin/env python3
"""
SANAD — OWASP Dependency-Check JSON Report Parser (Fail-Closed)
=================================================================
EXEC-PROMPT-010R4 Section 7: Authoritative parser that fails closed.

A valid PASS requires:
    total_dependencies > 0
    high = 0
    critical = 0
    unknown = 0
    analysis_exceptions = 0

Allowed results:
    pass            — all gates passed
    failed          — HIGH > 0 or CRITICAL > 0
    incomplete      — unknown > 0 or analysis_exceptions > 0
    execution_error — missing report, invalid JSON, invalid schema,
                      zero dependencies, or parser error

Usage:
    python3 scripts/ci/parse_owasp_report.py <json-file>

Outputs snake_case key=value lines for $GITHUB_OUTPUT:
    result
    total_dependencies
    total_vulnerabilities
    dependencies_with_vulnerabilities
    low
    medium
    high
    critical
    unknown
    suppressed_unique
    analysis_exceptions

Exit codes:
    0 = parsed successfully (regardless of finding count)
    1 = file not found or unreadable
    2 = JSON parse error
    3 = unsupported schema or validation error
"""

import json
import sys
import os


# ─── Schema validation ──────────────────────────────────────

def validate_report_root(report):
    """Validate the top-level report is a dict with dependencies."""
    if not isinstance(report, dict):
        raise ValueError(
            f"OWASP report root must be an object, got "
            f"{type(report).__name__}"
        )
    if "dependencies" not in report:
        raise ValueError("OWASP report is missing dependencies")
    return report


def normalize_dependencies(report):
    """
    Normalize the dependencies node from either list or object form.

    Supports:
        {"dependencies": []}                     — list form
        {"dependencies": {"dependency": []}}     — object form

    Rejects every other type. Does NOT default to empty list.
    """
    dependencies_node = report.get("dependencies")

    if isinstance(dependencies_node, list):
        dependencies = dependencies_node
    elif isinstance(dependencies_node, dict):
        dependencies = dependencies_node.get("dependency", [])
        if not isinstance(dependencies, list):
            raise ValueError(
                f"Object-form dependencies.dependency is not a list: "
                f"{type(dependencies).__name__}"
            )
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


def validate_dependency_item(dep, index):
    """Every dependency item must be a JSON object."""
    if not isinstance(dep, dict):
        raise ValueError(
            f"Dependency at index {index} must be an object, got "
            f"{type(dep).__name__}"
        )
    return dep


def normalize_vulnerabilities(dep):
    """
    Normalize the vulnerabilities node from a dependency.

    Accepts:
        Missing key       → []
        null              → []
        list              → validate each item
        object containing 'vulnerability' list → normalize

    Rejects unknown node types (does NOT silently return []).
    """
    if "vulnerabilities" not in dep:
        return []
    vulns = dep.get("vulnerabilities")

    if vulns is None:
        return []
    if isinstance(vulns, list):
        return vulns
    if isinstance(vulns, dict):
        nested = vulns.get("vulnerability", [])
        if isinstance(nested, list):
            return nested
        raise ValueError(
            f"Object-form vulnerabilities.vulnerability is not a list: "
            f"{type(nested).__name__}"
        )
    raise ValueError(
        f"Unsupported vulnerabilities node type: "
        f"{type(vulns).__name__}"
    )


def validate_vulnerability_item(vuln, dep_index, vuln_index):
    """Every vulnerability item must be an object."""
    if not isinstance(vuln, dict):
        raise ValueError(
            f"Vulnerability at dep[{dep_index}].vuln[{vuln_index}] "
            f"must be an object, got {type(vuln).__name__}"
        )
    return vuln


# ─── Severity extraction ────────────────────────────────────

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
    return None


def severity_from_score(score):
    """Map CVSS score to severity bucket."""
    if score is None:
        return "UNKNOWN"
    if score >= 9.0:
        return "CRITICAL"
    if score >= 7.0:
        return "HIGH"
    if score >= 4.0:
        return "MEDIUM"
    if score > 0:
        return "LOW"
    return "UNKNOWN"


def get_severity(vuln):
    """Determine severity from vuln entry — prefer explicit, fall back to CVSS."""
    severity = vuln.get("severity", "")
    if isinstance(severity, str):
        severity = severity.upper()
        if severity == "MODERATE":
            severity = "MEDIUM"
        if severity in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
            return severity

    # Fall back to CVSS score
    score = get_cvss_score(vuln)
    return severity_from_score(score)


# ─── Suppression deduplication ──────────────────────────────

def collect_suppressions(dep, dep_identifier):
    """
    Collect suppressed vulnerability keys for a dependency.
    Deduplicates using a stable key: (dep_identifier, cve_or_name).
    """
    suppressed_keys = set()

    # Check top-level suppressedVulnerabilities list
    suppressed_list = dep.get("suppressedVulnerabilities", [])
    if isinstance(suppressed_list, list):
        for sv in suppressed_list:
            if isinstance(sv, dict):
                cve = sv.get("name") or sv.get("cve") or ""
                key = (dep_identifier, str(cve))
                suppressed_keys.add(key)

    # Check per-vulnerability suppression entries
    for vuln in normalize_vulnerabilities(dep):
        if not isinstance(vuln, dict):
            continue

        # Check if vuln has suppressedVulnerabilities sub-list
        sub_suppressed = vuln.get("suppressedVulnerabilities", [])
        if isinstance(sub_suppressed, list):
            for sv in sub_suppressed:
                if isinstance(sv, dict):
                    cve = sv.get("name") or sv.get("cve") or vuln.get("name", "")
                    key = (dep_identifier, str(cve))
                    suppressed_keys.add(key)

        # Check suppressed flag
        if vuln.get("suppressed", False):
            cve = vuln.get("name") or vuln.get("cve") or ""
            key = (dep_identifier, str(cve))
            suppressed_keys.add(key)

    return suppressed_keys


def count_analysis_exceptions(dep):
    """Count analysis exceptions for a dependency."""
    exceptions = dep.get("analysisExceptions", [])
    if isinstance(exceptions, list):
        return len(exceptions)
    return 0


def get_dep_identifier(dep, index):
    """Get a stable identifier for a dependency."""
    for key in ("fileName", "filePath", "name", "id"):
        val = dep.get(key)
        if isinstance(val, str) and val:
            return val
    return f"dep-{index}"


# ─── Main parser ────────────────────────────────────────────

def parse_report(json_path):
    """
    Parse an OWASP Dependency-Check JSON report (fail-closed).

    Returns a dict with all counts and a result field.
    Raises on error.
    """
    if not os.path.isfile(json_path):
        raise FileNotFoundError(f"Report file not found: {json_path}")

    with open(json_path, "r", encoding="utf-8") as f:
        try:
            report = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(f"JSON parse error: {e}")

    # Validate root
    validate_report_root(report)

    # Normalize dependencies
    dependencies = normalize_dependencies(report)

    total_deps = len(dependencies)

    # EXEC-PROMPT-010R4 Section 7.3: zero dependencies = execution_error
    if total_deps == 0:
        return {
            "result": "execution_error",
            "total_dependencies": 0,
            "total_vulnerabilities": 0,
            "dependencies_with_vulnerabilities": 0,
            "low": 0,
            "medium": 0,
            "high": 0,
            "critical": 0,
            "unknown": 0,
            "suppressed_unique": 0,
            "analysis_exceptions": 0,
        }

    severity_counts = {"LOW": 0, "MEDIUM": 0, "HIGH": 0, "CRITICAL": 0, "UNKNOWN": 0}
    total_vulns = 0
    all_suppressed_keys = set()
    analysis_exceptions = 0
    deps_with_vulns = 0

    for i, dep in enumerate(dependencies):
        validate_dependency_item(dep, i)

        dep_identifier = get_dep_identifier(dep, i)
        vulns = normalize_vulnerabilities(dep)
        if vulns:
            deps_with_vulns += 1

        for j, vuln in enumerate(vulns):
            validate_vulnerability_item(vuln, i, j)
            total_vulns += 1
            severity = get_severity(vuln)
            severity_counts[severity] += 1

        # Collect suppressions (deduplicated)
        all_suppressed_keys.update(collect_suppressions(dep, dep_identifier))
        analysis_exceptions += count_analysis_exceptions(dep)

    high = severity_counts["HIGH"]
    critical = severity_counts["CRITICAL"]
    unknown = severity_counts["UNKNOWN"]

    # Determine result per EXEC-PROMPT-010R4 Section 8
    if high > 0 or critical > 0:
        result = "failed"
    elif unknown > 0 or analysis_exceptions > 0:
        result = "incomplete"
    else:
        result = "pass"

    return {
        "result": result,
        "total_dependencies": total_deps,
        "total_vulnerabilities": total_vulns,
        "dependencies_with_vulnerabilities": deps_with_vulns,
        "low": severity_counts["LOW"],
        "medium": severity_counts["MEDIUM"],
        "high": high,
        "critical": critical,
        "unknown": unknown,
        "suppressed_unique": len(all_suppressed_keys),
        "analysis_exceptions": analysis_exceptions,
    }


def main():
    if len(sys.argv) != 2:
        print("Usage: parse_owasp_report.py <json-file>", file=sys.stderr)
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

    # Output as snake_case key=value lines
    for key, value in result.items():
        print(f"{key}={value}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
