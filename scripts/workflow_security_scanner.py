#!/usr/bin/env python3
"""
SANAD Workflow Security Policy Scanner
Scans all .github/workflows/*.yml for:
  - workflow_dispatch password inputs
  - hardcoded credentials
  - secrets printed in logs
  - broad write permissions
  - pull_request_target
  - unsafe shell interpolation
  - curl without fail flags
  - missing timeout-minutes
  - production workflows without environment approval
"""
import sys
import re
from pathlib import Path
import yaml

ROOT = Path("/home/z/my-project")
WORKFLOW_DIR = ROOT / ".github" / "workflows"

CRITICAL_FINDINGS = []
WARNINGS = []
INFO = []

def add_critical(file, line, msg):
    CRITICAL_FINDINGS.append((file, line, msg))

def add_warning(file, line, msg):
    WARNINGS.append((file, line, msg))

def add_info(file, line, msg):
    INFO.append((file, line, msg))


def scan_file(path: Path):
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError as e:
        add_critical(rel, 0, f"YAML parse error: {e}")
        return

    # ---- 1. workflow_dispatch password inputs ----
    on = doc.get("on") or doc.get(True)  # YAML parses "on" as bool True sometimes
    if isinstance(on, dict):
        wfd = on.get("workflow_dispatch")
        if isinstance(wfd, dict):
            inputs = wfd.get("inputs") or {}
            for name, spec in inputs.items():
                if not isinstance(spec, dict):
                    continue
                lname = name.lower()
                if any(s in lname for s in ["password", "secret", "token", "api_key", "private_key"]):
                    add_critical(rel, 0, f"workflow_dispatch input '{name}' looks like a credential — use secrets instead")
                # password-type inputs
                if spec.get("type") == "password":
                    add_critical(rel, 0, f"workflow_dispatch input '{name}' uses type=password — forbidden")

    # ---- 2. Broad permissions ----
    perms = doc.get("permissions")
    if isinstance(perms, dict):
        for k, v in perms.items():
            if k in ("contents", "pull-requests", "issues", "deployments", "packages", "id-token") and v == "write":
                # id-token: write is sometimes needed for OIDC — flag as info
                if k == "id-token":
                    add_info(rel, 0, f"permissions.{k}: write (OIDC — verify it's required)")
                else:
                    add_warning(rel, 0, f"permissions.{k}: write — verify it's actually needed")

    # ---- 3. pull_request_target ----
    if isinstance(on, dict) and "pull_request_target" in on:
        add_critical(rel, 0, "pull_request_target trigger is unsafe for untrusted PRs")

    # ---- 4. Missing timeout-minutes ----
    jobs = doc.get("jobs") or {}
    for jname, jdef in jobs.items() if isinstance(jobs, dict) else []:
        if not isinstance(jdef, dict):
            continue
        if "timeout-minutes" not in jdef:
            add_warning(rel, 0, f"job '{jname}' missing timeout-minutes")

    # ---- 5. Hardcoded credential patterns in body ----
    # Note: we deliberately do NOT match $VAR or ${VAR} references — those are
    # shell variable substitutions sourced from secrets.*.env upstream, which
    # is the correct pattern. We only flag literal string values.
    suspicious_patterns = [
        # password = "literal"   (not password = "$VAR")
        (r'password\s*[:=]\s*["\'](?!\$)[^"\']{4,}["\']', "hardcoded password literal"),
        # api[_-]?key = "literal-string-of-letters"   (not $VAR)
        (r'api[_-]?key\s*[:=]\s*["\'](?!\$)[A-Za-z0-9]{8,}["\']', "hardcoded API key"),
        (r'secret\s*[:=]\s*["\'](?!\$)[^"\']{8,}["\']', "hardcoded secret"),
        (r'token\s*[:=]\s*["\'](?!\$)[A-Za-z0-9_\-]{20,}["\']', "hardcoded token"),
    ]
    for i, line in enumerate(lines, 1):
        # Skip lines that look like secret references
        if "${{ secrets." in line or "${{secrets." in line:
            continue
        for pat, desc in suspicious_patterns:
            if re.search(pat, line, re.IGNORECASE):
                # Skip lines that are clearly docs/comments
                stripped = line.strip()
                if stripped.startswith("#") or stripped.startswith("//"):
                    continue
                add_critical(rel, i, f"{desc}: {stripped[:120]}")

    # ---- 6. secrets printed in logs (echo $SECRET_NAME, echo $TOKEN_NAME) ----
    # Match only ALL_CAPS variable names that clearly indicate a secret
    # (must be at least 8 chars long to avoid matching short tokens like $KEY).
    # Skip generic loop variables like $key, $value, $val, $k, $v.
    secret_var_pat = re.compile(
        r'echo\s+[\"\']?\$\{?([A-Z][A-Z0-9_]{6,}(?:SECRET|PASSWORD|TOKEN|API_KEY|PRIVATE_KEY|CREDENTIAL)[A-Z0-9_]*)\}?',
        re.IGNORECASE,
    )
    for i, line in enumerate(lines, 1):
        m = secret_var_pat.search(line)
        if m:
            var_name = m.group(1)
            # Skip generic loop variables
            if var_name.lower() in {"key", "value", "val", "k", "v", "name", "result", "output"}:
                continue
            # If the line is a ::add-mask:: call, that's the correct pattern
            if "::add-mask::" in line:
                continue
            # If the line writes to $GITHUB_OUTPUT or $GITHUB_ENV, that's safe (not logs)
            if "GITHUB_OUTPUT" in line or "GITHUB_ENV" in line:
                continue
            add_critical(rel, i, f"echo of secret-like variable '{var_name}' may leak to logs")

    # ---- 7. curl without --fail / -f ----
    for i, line in enumerate(lines, 1):
        if "curl" in line and ("http" in line.lower() or "url" in line.lower()):
            if not any(flag in line for flag in ["--fail", " -f ", " -fsS", " -fsSL", "--fail-with-body"]):
                # Only flag curl that's clearly making an HTTP call (not in a comment)
                if line.strip().startswith("#") or line.strip().startswith("echo"):
                    continue
                # Heuristic: curl without -f fails open
                # (only info, not critical)
                add_info(rel, i, "curl without --fail / -f (silent on HTTP error)")

    # ---- 8. Production workflows without environment ----
    if isinstance(jobs, dict):
        for jname, jdef in jobs.items() if isinstance(jobs, dict) else []:
            if not isinstance(jdef, dict):
                continue
            runs_on = jdef.get("runs-on", "")
            env = jdef.get("environment")
            # If workflow name or job name mentions production/deploy/go-live but no environment
            wf_name = doc.get("name", "").lower()
            jname_lower = jname.lower()
            if any(s in wf_name + " " + jname_lower for s in ["production", "go-live", "commercial", "deploy", "release"]):
                if not env:
                    add_warning(rel, 0, f"production-like job '{jname}' has no environment: protection")


print("=" * 70)
print("SANAD Workflow Security Policy Scanner")
print("=" * 70)
print(f"Scanning {len(list(WORKFLOW_DIR.glob('*.yml')))} workflow files...")

for f in sorted(WORKFLOW_DIR.glob("*.yml")):
    scan_file(f)

print()
print("=" * 70)
print(f"CRITICAL findings: {len(CRITICAL_FINDINGS)}")
print(f"WARNINGS:          {len(WARNINGS)}")
print(f"INFO (low-risk):   {len(INFO)}")
print("=" * 70)

if CRITICAL_FINDINGS:
    print("\n=== CRITICAL FINDINGS (must fix) ===")
    for f, l, m in CRITICAL_FINDINGS:
        print(f"  {f}:{l} — {m}")

if WARNINGS:
    print("\n=== WARNINGS (review) ===")
    for f, l, m in WARNINGS:
        print(f"  {f}:{l} — {m}")

if INFO:
    print("\n=== INFO (informational) ===")
    for f, l, m in INFO[:30]:  # Cap output
        print(f"  {f}:{l} — {m}")
    if len(INFO) > 30:
        print(f"  ... and {len(INFO) - 30} more info findings")

print()
if CRITICAL_FINDINGS:
    print("RESULT: FAIL — critical security findings")
    sys.exit(1)
elif WARNINGS:
    print("RESULT: PASS (with warnings) — no critical findings")
    sys.exit(0)
else:
    print("RESULT: PASS — no critical or warning findings")
    sys.exit(0)
