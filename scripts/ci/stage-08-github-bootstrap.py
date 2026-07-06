#!/usr/bin/env python3
"""
SANAD Stage 08 — GitHub Bootstrap Script
Creates: milestones, labels, epic issues, technical debt issues.
Idempotent: re-running skips already-created items.
"""
import os
import sys
import json
import time
import urllib.request
import urllib.error

REPO = "snadaiapp-png/SNAD"
TOKEN_FILE = "/tmp/gh-token.txt"

def load_token():
    if not os.path.exists(TOKEN_FILE):
        sys.exit("ERROR: /tmp/gh-token.txt not found. Complete device flow first.")
    with open(TOKEN_FILE) as f:
        return f.read().strip()

TOKEN = load_token()

def api(method, path, payload=None):
    url = f"https://api.github.com/repos/{REPO}/{path}"
    data = json.dumps(payload).encode() if payload else None
    req = urllib.request.Request(url, method=method, data=data, headers={
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    try:
        with urllib.request.urlopen(req) as r:
            body = r.read().decode()
            return r.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            err = json.loads(body)
        except Exception:
            err = {"raw": body}
        return e.code, err

def create_milestone(title, description, state="open"):
    # Check existing
    code, milestones = api("GET", "milestones?state=all&per_page=100")
    if code == 200:
        for m in milestones:
            if m["title"] == title:
                print(f"  [skip] milestone '{title}' exists (#{m['number']})")
                return m
    code, resp = api("POST", "milestones", {
        "title": title,
        "description": description,
        "state": state,
    })
    if code in (200, 201):
        print(f"  [ok] milestone '{title}' created (#{resp['number']})")
        return resp
    print(f"  [ERR] milestone '{title}': {code} {resp}")
    return None

def create_label(name, color, description=""):
    code, resp = api("GET", f"labels/{urllib.parse.quote(name)}")
    if code == 200:
        print(f"  [skip] label '{name}' exists")
        return
    code, resp = api("POST", "labels", {
        "name": name,
        "color": color,
        "description": description,
    })
    if code in (200, 201):
        print(f"  [ok] label '{name}' created")
    else:
        print(f"  [ERR] label '{name}': {code} {resp}")

def create_issue(title, body, labels, milestone_number=None):
    payload = {
        "title": title,
        "body": body,
        "labels": labels,
    }
    if milestone_number:
        payload["milestone"] = milestone_number
    code, resp = api("POST", "issues", payload)
    if code in (200, 201):
        print(f"  [ok] issue #{resp['number']} '{title}'")
        return resp
    # Check if it already exists (duplicate detection)
    if code == 422:
        # Try to find existing
        q = f"repo:{REPO} in:title {title[:60]}"
        s_url = f"https://api.github.com/search/issues?q={urllib.parse.quote(q)}"
        req = urllib.request.Request(s_url, headers={
            "Authorization": f"token {TOKEN}",
            "Accept": "application/vnd.github+json",
        })
        try:
            with urllib.request.urlopen(req) as r:
                data = json.loads(r.read().decode())
                for item in data.get("items", []):
                    if item["title"] == title:
                        print(f"  [skip] issue #{item['number']} '{title}' already exists")
                        return item
        except Exception:
            pass
    print(f"  [ERR] issue '{title}': {code} {resp}")
    return None

import urllib.parse

def main():
    print("=" * 70)
    print("SANAD Stage 08 — GitHub Bootstrap")
    print("=" * 70)
    print(f"Repo: {REPO}")
    print()

    # Verify token
    req = urllib.request.Request("https://api.github.com/user", headers={
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github+json",
    })
    with urllib.request.urlopen(req) as r:
        user = json.loads(r.read().decode())
        print(f"Authenticated as: {user.get('login')} ({user.get('name')})")
    print()

    # === Milestones ===
    print("--- MILESTONES ---")
    ms_stage08 = create_milestone(
        "SANAD Stage 08 — Scale Phase",
        "Stage 08 — Scale, Growth & Global Expansion. 10 sprints, 20 weeks."
    )
    ms_debt = create_milestone(
        "Stage 07 Deferred Technical Debt",
        "Stage 07 deferred closure debt. Must be closed before final program closure."
    )
    print()

    # === Labels ===
    print("--- LABELS ---")
    labels = [
        ("stage-08", "1f4e79", "Stage 08 work"),
        ("scale", "0e8a16", "Scaling architecture"),
        ("globalization", "5319e7", "Global expansion / localization"),
        ("marketplace", "fbca04", "Marketplace platform"),
        ("industry-pack", "a2eeef", "Industry packs"),
        ("ai-agent", "d93f0b", "AI agent ecosystem"),
        ("enterprise", "006b75", "Enterprise features"),
        ("partner", "c5def5", "Partner ecosystem"),
        ("developer-platform", "0e8a16", "Developer platform"),
        ("growth", "fbca04", "Growth and commercial scaling"),
        ("analytics", "5319e7", "Data and analytics"),
        ("technical-debt", "b60205", "Technical debt"),
        ("stage-07-debt", "996600", "Stage 07 deferred debt"),
        ("security", "d73a4a", "Security"),
        ("architecture", "0052cc", "Architecture"),
        ("backend", "0052cc", "Backend"),
        ("frontend", "0052cc", "Frontend"),
        ("devops", "0052cc", "DevOps"),
        ("data", "0052cc", "Data"),
        ("P0", "b60205", "Priority 0 (critical)"),
        ("P1", "d93f0b", "Priority 1 (high)"),
        ("P2", "fbca04", "Priority 2 (medium)"),
        ("blocked", "b60205", "Blocked"),
        ("evidence-required", "5319e7", "Evidence required before closure"),
    ]
    for name, color, desc in labels:
        create_label(name, color, desc)
    print()

    # === Epic Issues ===
    print("--- EPIC ISSUES ---")
    epics = [
        ("ST8-EPIC-01 — Scale Architecture",
         "Track 8.1 — Scale Architecture and Capacity Platform.\n\nDeliverables: SCALE-ARCHITECTURE.md, CAPACITY-MODEL.md, MULTI-REGION-READINESS.md, RESILIENCE-MODEL.md, COST-SCALING-MODEL.md.\n\nOwner: Infrastructure Owner.\nGate: 8B.",
         ["stage-08", "scale", "architecture", "P0"]),
        ("ST8-EPIC-02 — Global Expansion",
         "Track 8.2 — Global Expansion and Localization Platform.\n\nDeliverables: GLOBALIZATION-ARCHITECTURE.md, COUNTRY-ONBOARDING-FRAMEWORK.md, LOCALIZATION-STANDARDS.md, DATA-RESIDENCY-MATRIX.md, TAX-LOCALIZATION-FRAMEWORK.md.\n\nOwner: System Owner.\nGate: 8B.",
         ["stage-08", "globalization", "P0"]),
        ("ST8-EPIC-03 — Marketplace",
         "Track 8.3 — SANAD Marketplace Platform.\n\nDeliverables: MARKETPLACE-ARCHITECTURE.md, PUBLISHER-GOVERNANCE.md, APP-CERTIFICATION-STANDARD.md, REVENUE-SHARING-MODEL.md, SECURITY-MODEL.md.\n\nOwner: System Owner.\nGate: 8C.",
         ["stage-08", "marketplace", "P1"]),
        ("ST8-EPIC-04 — Industry Packs",
         "Track 8.4 — Industry Packs Platform.\n\nDeliverables: INDUSTRY-PACK-FRAMEWORK.md, INDUSTRY-METADATA-SCHEMA.md, INDUSTRY-PACK-LIFECYCLE.md, INDUSTRY-CERTIFICATION.md.\n\nOwner: System Owner.\nGate: 8C.",
         ["stage-08", "industry-pack", "P1"]),
        ("ST8-EPIC-05 — AI Agent Ecosystem",
         "Track 8.5 — AI Agent Ecosystem.\n\nDeliverables: AGENT-PLATFORM-ARCHITECTURE.md, AGENT-SECURITY-MODEL.md, AGENT-EVALUATION-FRAMEWORK.md, HUMAN-IN-THE-LOOP-POLICY.md, AI-COST-GOVERNANCE.md.\n\nOwner: System Owner.\nGate: 8D.",
         ["stage-08", "ai-agent", "P0"]),
        ("ST8-EPIC-06 — Enterprise Features",
         "Track 8.6 — Enterprise Features Platform.\n\nDeliverables: ENTERPRISE-ARCHITECTURE.md, ENTERPRISE-IDENTITY.md, SEGREGATION-OF-DUTIES.md, PRIVILEGED-ACCESS.md, ENTERPRISE-SLA-MODEL.md.\n\nOwner: Infrastructure Owner.\nGate: 8D.",
         ["stage-08", "enterprise", "P1"]),
        ("ST8-EPIC-07 — Partner Ecosystem",
         "Track 8.7 — Partner Ecosystem Platform.\n\nDeliverables: PARTNER-ECOSYSTEM-ARCHITECTURE.md, PARTNER-TIER-MODEL.md, DEAL-REGISTRATION.md, PARTNER-CERTIFICATION.md, PARTNER-GOVERNANCE.md.\n\nOwner: Project Manager.\nGate: 8E.",
         ["stage-08", "partner", "P2"]),
        ("ST8-EPIC-08 — Developer Platform",
         "Track 8.8 — Developer and Integration Platform.\n\nDeliverables: DEVELOPER-PLATFORM.md, API-GOVERNANCE.md, WEBHOOK-STANDARD.md, SDK-STRATEGY.md, INTEGRATION-CERTIFICATION.md.\n\nOwner: System Owner.\nGate: 8E.",
         ["stage-08", "developer-platform", "P1"]),
        ("ST8-EPIC-09 — Growth Platform",
         "Track 8.9 — Growth and Commercial Scaling Platform.\n\nDeliverables: GROWTH-PLATFORM.md, COMMERCIAL-METRICS.md, PRICING-AND-METERING.md, CUSTOMER-HEALTH-MODEL.md.\n\nOwner: Project Manager.\nGate: 8E.",
         ["stage-08", "growth", "P1"]),
        ("ST8-EPIC-10 — Data and Intelligence",
         "Track 8.10 — Data, Analytics and Intelligence for Scale.\n\nDeliverables: DATA-AND-ANALYTICS-EXPANSION.md, metric catalog, semantic layer, executive dashboards.\n\nOwner: System Owner.\nGate: 8E.",
         ["stage-08", "analytics", "P1"]),
        ("ST8-EPIC-11 — Reliability and Operations",
         "Reliability and Operations platform.\n\nDeliverables: production dashboards, on-call model, incident response runbooks, synthetic uptime monitoring.\n\nOwner: Infrastructure Owner.\nGate: 8F.",
         ["stage-08", "scale", "devops", "P0"]),
        ("ST8-EPIC-12 — Stage 07 Technical Debt Closure",
         "Closure of all Stage 07 deferred technical debt items (TD-07-001 through TD-07-008).\n\nLinked register: docs/technical-debt/STAGE-07-DEFERRED-TECHNICAL-DEBT-REGISTER.md.\n\nOwner: Project Manager.\nGate: 8F (blocking).",
         ["stage-08", "stage-07-debt", "technical-debt", "P0", "evidence-required"]),
    ]
    ms08_num = ms_stage08["number"] if ms_stage08 else None
    for title, body, labels in epics:
        create_issue(title, body, labels, milestone_number=ms08_num)
    print()

    # === Debt Issues ===
    print("--- TECHNICAL DEBT ISSUES ---")
    debts = [
        ("TD-07-001 — OWASP Final Security Assessment",
         "Stage 07 deferred debt. Production SAST, DAST, container CVE, dependency validation, API security, auth/session testing, tenant isolation testing, penetration test, vulnerability register, closure of Critical/High, residual risk acceptance.\n\nLinked register: docs/technical-debt/STAGE-07-DEFERRED-TECHNICAL-DEBT-REGISTER.md.\n\nOwner: Security Owner.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "security", "P0", "evidence-required"]),
        ("TD-07-002 — Production Backup and Restore Validation",
         "Stage 07 deferred debt. Production backup config, encryption, retention, PITR, restore into isolated env, schema validation, Flyway history validation, app startup after restore, data integrity, recovery time, RPO/RTO, runbook, owner approval.\n\nOwner: Infrastructure Owner.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "devops", "P0", "evidence-required"]),
        ("TD-07-003 — Monitoring, Alerting and Incident Response",
         "Stage 07 deferred debt. Production dashboards (infra, app, DB, API, auth, tenant isolation, email, backup, deployment, security), alert routing, escalation matrix, on-call, runbooks, synthetic uptime.\n\nOwner: Infrastructure Owner.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "devops", "P0", "evidence-required"]),
        ("TD-07-004 — Commercial Infrastructure and Paid Production Plan",
         "Stage 07 deferred debt. No Free Tier dependency in production. Paid plan. No Sleep/Cold Start. CPU/Memory allocations. DB production tier. Backup/restore. HA. Autoscaling. Provider SLA. Capacity thresholds. Cost baseline. Financial approval.\n\nResidual risk currently accepted: Render FREE TIER remains in use.\n\nOwner: Infrastructure Owner + Project Manager.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "devops", "P0", "evidence-required"]),
        ("TD-07-005 — Fail-Closed Commercial Workflow Completion",
         "Stage 07 deferred debt. No continue-on-error. Governance failure = workflow failure. GO not published before successful Artifact upload. Artifact upload failure = NO-GO. Release Tag revocation on subsequent failure. Summary records COMPLETED, releaseAuthorized: true, tagShaMatch: PASS, taggedSha. NO-GO path records failedGate. GitHub UI Failure on NO-GO. Regression Policy Check auto-trigger.\n\nOwner: QA & Release Owner.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "devops", "P0", "evidence-required"]),
        ("TD-07-006 — Email Delivery Evidence Hardening",
         "Stage 07 deferred debt. Governance MUST verify: result == PASS, messageId non-empty, createdAt non-empty, deliveryStatus == delivered, releaseSha == expected. Reject: queued, sent, pending, processing, unknown, empty. Password recovery E2E, one-time token, reuse rejection, expired token rejection, session revocation, confirmation notification, unauthorized email-proxy rejection.\n\nOwner: QA & Release Owner.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "P0", "evidence-required"]),
        ("TD-07-007 — Independent Human Approvals",
         "Stage 07 deferred debt. Five distinct approver accounts: Security Owner, Infrastructure Owner, QA & Release Owner, System Owner, Project Manager. Each with: Approver name, role, decision, release SHA, timestamp UTC, accepted residual risks, approval evidence.\n\nResidual risk currently accepted: All five approvals issued by single account.\n\nOwner: Project Manager.\nStatus: OPEN — BLOCKING FINAL CLOSURE.",
         ["stage-07-debt", "technical-debt", "P0", "evidence-required"]),
        ("TD-07-008 — Controlling Issues Evidence Reconciliation",
         "Stage 07 deferred debt. Review issues #29, #101, #109, #150, #173. Reopen any closed before exit criteria met. Per issue: linked evidence, SHA, workflow run, artifact, verification timestamp, fulfilled requirements, residual risks, owner approval, closure AFTER evidence.\n\nOwner: Project Manager.\nStatus: REVIEW REQUIRED.",
         ["stage-07-debt", "technical-debt", "P0", "evidence-required"]),
    ]
    ms_debt_num = ms_debt["number"] if ms_debt else None
    for title, body, labels in debts:
        create_issue(title, body, labels, milestone_number=ms_debt_num)
    print()

    print("=" * 70)
    print("GITHUB BOOTSTRAP COMPLETE")
    print("=" * 70)

if __name__ == "__main__":
    main()
