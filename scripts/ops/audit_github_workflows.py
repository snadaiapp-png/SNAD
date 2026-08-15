#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import pathlib
import re
from dataclasses import asdict, dataclass, field
from typing import Iterable

PLACEHOLDER_PASSWORDS = {
    "wrong", "example", "password", "changeme", "change-me", "test", "dummy",
    "placeholder", "redacted", "masked", "secret", "your-password", "your_password",
    "<password>",
}


@dataclass
class Finding:
    path: str
    name: str
    triggers: list[str] = field(default_factory=list)
    writes_source: bool = False
    writes_render: bool = False
    writes_render_env: bool = False
    writes_database: bool = False
    runs_flyway: bool = False
    builds_image: bool = False
    deploys_image: bool = False
    reads_production: bool = False
    uses_production_environment: bool = False
    secret_candidate_types: list[str] = field(default_factory=list)
    risk: str = "LOW"
    classification_hint: str = "KEEP_OR_REVIEW"
    reasons: list[str] = field(default_factory=list)

    def to_dict(self):
        return asdict(self)


def _uniq(items: Iterable[str]) -> list[str]:
    return sorted(set(item for item in items if item))


def _workflow_name(text: str, path: str) -> str:
    match = re.search(r"(?mi)^name:\s*[\"']?([^\n\"']+)", text)
    return match.group(1).strip() if match else pathlib.Path(path).name


def _triggers(text: str) -> list[str]:
    result: list[str] = []
    inline = re.search(r"(?mi)^on:\s*(.+)$", text)
    if inline and inline.group(1).strip():
        raw = inline.group(1).strip().strip("[]{}")
        result.extend(
            re.findall(
                r"\b(push|pull_request|workflow_dispatch|workflow_call|schedule|repository_dispatch|release)\b",
                raw,
            )
        )

    lines = text.splitlines()
    in_on = False
    on_indent = 0
    for line in lines:
        if re.match(r"^\s*#", line) or not line.strip():
            continue
        marker = re.match(r"^(\s*)on:\s*$", line)
        if marker:
            in_on = True
            on_indent = len(marker.group(1))
            continue
        if in_on:
            indent = len(line) - len(line.lstrip())
            if indent <= on_indent:
                in_on = False
                continue
            event = re.match(
                r"^\s+(push|pull_request|workflow_dispatch|workflow_call|schedule|repository_dispatch|release):?",
                line,
            )
            if event:
                result.append(event.group(1))
    return _uniq(result)


def _secret_candidates(text: str) -> list[str]:
    found: list[str] = []

    if re.search(r"\brnd_[A-Za-z0-9_-]{20,}\b", text):
        found.append("render_api_token_literal")
    if "-----BEGIN " in text and "PRIVATE KEY-----" in text:
        found.append("private_key_literal")
    if re.search(r"(?i)jdbc:postgresql://[^\s:/]+:[^\s@/]+@", text):
        found.append("jdbc_url_with_inline_credentials")

    password_patterns = [
        r"(?i)\bpassword\s*=\s*[\"']([^\"'\n${}]{4,})[\"']",
        r"(?i)[\"']password[\"']\s*:\s*[\"']([^\"'\n${}]{4,})[\"']",
        r"(?i)\b(?:DB_PASS|DB_PASSWORD|DATABASE_PASSWORD|ADMIN_PASSWORD)\s*=\s*[\"']([^\"'\n${}]{4,})[\"']",
    ]
    for pattern in password_patterns:
        for match in re.finditer(pattern, text):
            value = match.group(1).strip()
            if (
                value.lower() not in PLACEHOLDER_PASSWORDS
                and not value.startswith("${{")
                and not value.startswith("$")
            ):
                found.append("plaintext_password_literal")
                break

    if re.search(
        r"(?i)\b(?:api[_-]?key|token|secret)\s*[:=]\s*[\"'][A-Za-z0-9_\-./+=]{20,}[\"']",
        text,
    ):
        found.append("generic_secret_literal")

    return _uniq(found)


def scan_text(path: str, text: str) -> Finding:
    lowered = text.lower()
    reasons: list[str] = []

    has_render_reference = any(
        token in lowered
        for token in (
            "api.render.com",
            "render_api_key",
            "render_service_id",
            "render_deploy_hook_url",
            "render deploys ",
        )
    )
    render_write_verb = bool(
        re.search(
            r"(?is)(?:-x\s*(?:post|put|patch|delete)|\b(?:post|put|patch|delete)\b).{0,240}api\.render\.com|api\.render\.com.{0,240}(?:-x\s*(?:post|put|patch|delete))",
            text,
        )
    )
    render_cli_write = bool(
        re.search(
            r"(?i)\brender\s+(?:deploys\s+create|services?\s+(?:suspend|resume|update|delete)|env\b)",
            text,
        )
    )
    render_lifecycle = bool(
        re.search(r"(?i)/services/[^\s\"']+/(?:suspend|resume|deploys|env-vars)", text)
    )
    writes_render = has_render_reference and (
        render_write_verb or render_cli_write or render_lifecycle
    )
    writes_render_env = writes_render and bool(
        re.search(
            r"(?i)(?:/env-vars(?:/|\b)|set_render_var|set_var\s+[\"']?(?:DATABASE_|FLYWAY_|SPRING_|JAVA_OPTS|JPA_|JWT_|SERVER_))",
            text,
        )
    )
    deploys_image = writes_render and bool(
        re.search(
            r"(?i)(?:/deploys\b|render\s+deploys\s+create|render_deploy_hook_url)",
            text,
        )
    )

    runs_flyway = bool(re.search(r"(?i)\bflyway\b", text))
    flyway_migrate = bool(
        re.search(
            r"(?i)(?:\bflyway\b.{0,180}\bmigrate\b|flyway/flyway[^\n]*|\bmigrate\s*$)",
            text,
        )
    )

    sql_mutation = bool(
        re.search(
            r"(?i)\b(insert\s+into|update\s+[a-z_]|delete\s+from|alter\s+table|create\s+table|drop\s+(?:table|schema|database)|truncate\s+|pg_terminate_backend\s*\()",
            text,
        )
    )
    psql = bool(re.search(r"(?i)\bpsql\b", text))
    database_create_api = bool(
        re.search(r"(?i)(?:\"type\"\s*:\s*\"psql\"|/databases\b)", text)
    ) and writes_render
    writes_database = flyway_migrate or (psql and sql_mutation) or database_create_api

    builds_image = bool(
        re.search(
            r"(?i)(?:docker/build-push-action|docker\s+buildx?\s+build|docker\s+build\b)",
            text,
        )
    )
    writes_source = bool(
        re.search(
            r"(?i)contents:\s*write|\bgit\s+push\b|\bgh\s+pr\s+merge\b|\bgh\s+release\s+create\b",
            text,
        )
    )
    uses_production_environment = bool(
        re.search(
            r"(?mi)^\s*environment:\s*(?:[\"']?production[\"']?|\n\s+name:\s*[\"']?production)",
            text,
        )
    )
    reads_production = uses_production_environment or bool(
        re.search(
            r"(?i)(?:PRODUCTION_BASE_URL|PROD_JDBC_URL|PRODUCTION_DATABASE_URL|sanad-backend[^\s]*\.onrender\.com)",
            text,
        )
    )
    secret_candidates = _secret_candidates(text)

    if writes_render:
        reasons.append("render_write_capability")
    if writes_render_env:
        reasons.append("render_environment_mutation")
    if deploys_image:
        reasons.append("render_deploy_capability")
    if writes_database:
        reasons.append("database_mutation_capability")
    if runs_flyway:
        reasons.append("flyway_reference")
    if writes_source:
        reasons.append("source_write_capability")
    if secret_candidates:
        reasons.append("secret_candidate_present")

    critical = bool(secret_candidates) or writes_database or (
        writes_render_env and deploys_image
    )
    high = writes_render or writes_source or deploys_image
    risk = "CRITICAL" if critical else (
        "HIGH" if high else ("MEDIUM" if reads_production or runs_flyway else "LOW")
    )
    classification_hint = (
        "DANGEROUS_OR_REPLACE"
        if risk in {"CRITICAL", "HIGH"}
        else ("REVIEW" if risk == "MEDIUM" else "KEEP_OR_REVIEW")
    )

    return Finding(
        path=path,
        name=_workflow_name(text, path),
        triggers=_triggers(text),
        writes_source=writes_source,
        writes_render=writes_render,
        writes_render_env=writes_render_env,
        writes_database=writes_database,
        runs_flyway=runs_flyway,
        builds_image=builds_image,
        deploys_image=deploys_image,
        reads_production=reads_production,
        uses_production_environment=uses_production_environment,
        secret_candidate_types=secret_candidates,
        risk=risk,
        classification_hint=classification_hint,
        reasons=_uniq(reasons),
    )


def iter_targets(root: pathlib.Path):
    workflow_root = root / ".github" / "workflows"
    if workflow_root.exists():
        for path in sorted(workflow_root.iterdir()):
            if path.is_file() and path.suffix.lower() in {".yml", ".yaml"}:
                yield path

    production_root = root / "scripts" / "production"
    if production_root.exists():
        for path in sorted(production_root.rglob("*")):
            if path.is_file() and path.suffix.lower() in {
                ".sh", ".ps1", ".bat", ".service", ".yml", ".yaml", ".env", ".properties"
            }:
                yield path


def summarize(findings: list[Finding]) -> dict:
    workflows = [
        finding for finding in findings if finding.path.startswith(".github/workflows/")
    ]
    return {
        "scanned_files": len(findings),
        "workflow_count": len(workflows),
        "render_writers": sum(finding.writes_render for finding in findings),
        "render_env_writers": sum(finding.writes_render_env for finding in findings),
        "database_writers": sum(finding.writes_database for finding in findings),
        "flyway_references": sum(finding.runs_flyway for finding in findings),
        "image_builders": sum(finding.builds_image for finding in findings),
        "deploy_writers": sum(finding.deploys_image for finding in findings),
        "source_writers": sum(finding.writes_source for finding in findings),
        "production_readers": sum(finding.reads_production for finding in findings),
        "secret_candidate_files": sum(
            bool(finding.secret_candidate_types) for finding in findings
        ),
        "critical_files": sum(finding.risk == "CRITICAL" for finding in findings),
        "high_files": sum(finding.risk == "HIGH" for finding in findings),
    }


def write_outputs(output_dir: pathlib.Path, findings: list[Finding]):
    output_dir.mkdir(parents=True, exist_ok=True)
    payload = [finding.to_dict() for finding in findings]
    (output_dir / "workflow-inventory.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    summary = summarize(findings)
    (output_dir / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    secret_payload = [
        {"path": finding.path, "candidate_types": finding.secret_candidate_types}
        for finding in findings
        if finding.secret_candidate_types
    ]
    (output_dir / "secret-candidates.json").write_text(
        json.dumps(secret_payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    fields = list(Finding.__dataclass_fields__.keys())
    with (output_dir / "workflow-inventory.csv").open(
        "w", encoding="utf-8", newline=""
    ) as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for finding in findings:
            row = finding.to_dict()
            for key in ("triggers", "secret_candidate_types", "reasons"):
                row[key] = ";".join(row[key])
            writer.writerow(row)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Read-only SNAD workflow and production control-plane audit"
    )
    parser.add_argument("--root", default=".")
    parser.add_argument("--output-dir", default="clean-room-audit")
    parser.add_argument("--fail-on-secret-candidates", action="store_true")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    findings: list[Finding] = []
    for path in iter_targets(root):
        relative = path.relative_to(root).as_posix()
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        findings.append(scan_text(relative, text))

    write_outputs(pathlib.Path(args.output_dir), findings)
    summary = summarize(findings)

    # Counts only: matched secret material is never printed by this tool.
    print(json.dumps(summary, sort_keys=True))
    if args.fail_on_secret_candidates and summary["secret_candidate_files"]:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
