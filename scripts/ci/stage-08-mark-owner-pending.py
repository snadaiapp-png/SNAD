#!/usr/bin/env python3
"""
SANAD Stage 08 — Add OWNER ACCOUNT PENDING comment to all open Stage 08 issues.
Per PM Review §7, every issue must be marked OWNER ACCOUNT PENDING until TD-07-007 closed.
"""
import os
import sys
import json
import urllib.request
import urllib.error

REPO = "snadaiapp-png/SNAD"
TOKEN_FILE = "/tmp/gh-token.txt"

def load_token():
    if not os.path.exists(TOKEN_FILE):
        sys.exit("ERROR: /tmp/gh-token.txt not found.")
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

def list_issues(state="open", per_page=100):
    page = 1
    while True:
        code, issues = api("GET", f"issues?state={state}&per_page={per_page}&page={page}")
        if code != 200 or not issues:
            break
        for issue in issues:
            if not issue.get("pull_request"):
                yield issue
        if len(issues) < per_page:
            break
        page += 1

def has_owner_pending_comment(issue_number):
    code, comments = api("GET", f"issues/{issue_number}/comments?per_page=100")
    if code != 200:
        return False
    for c in comments:
        if "OWNER ACCOUNT PENDING" in c.get("body", ""):
            return True
    return False

def add_comment(issue_number, body):
    code, resp = api("POST", f"issues/{issue_number}/comments", {"body": body})
    if code in (200, 201):
        print(f"  [ok] comment on #{issue_number}")
    else:
        print(f"  [ERR] #{issue_number}: {code} {resp}")

def main():
    print("=" * 70)
    print("SANAD Stage 08 — OWNER ACCOUNT PENDING Annotation")
    print("=" * 70)
    print()
    print("Per PM Review §7: every Stage 08 issue must be marked OWNER ACCOUNT")
    print("PENDING until TD-07-007 (Independent Human Approvals) is closed.")
    print()

    comment_body = """## OWNER ACCOUNT PENDING

Per PM Review 2026-07-06 §7, this issue has an Owner recorded in its body but **no GitHub account Assignee**. This is a known limitation tracked as:

- **TD-07-007 — Independent Human Approvals** (Issue #298, OPEN — BLOCKING FINAL CLOSURE)

### Status

```text
Owner:            (as recorded in issue body)
Assignees:        NONE
GitHub Account:   PENDING (single-account limitation)
```

### Remediation

Onboard a second accountable GitHub account (e.g., Security Owner, Infrastructure Owner) and assign this issue. Until then, this issue is **BLOCKED FROM PRODUCTION RELEASE** but may proceed with implementation work in a non-production branch.

### Cross-References

- TD-07-007: https://github.com/snadaiapp-png/SNAD/issues/298
- Stage 07 Debt Register: `docs/technical-debt/STAGE-07-DEFERRED-TECHNICAL-DEBT-REGISTER.md`
- PM Review Decision: `docs/stage-08/acceptance/STAGE-08-SPRINT-0-STATUS-REPORT.md`
"""

    count = 0
    for issue in list_issues(state="open"):
        labels = [l["name"] for l in issue.get("labels", [])]
        if "stage-08" not in labels and "stage-07-debt" not in labels:
            continue
        if has_owner_pending_comment(issue["number"]):
            print(f"  [skip] #{issue['number']} already annotated")
            continue
        add_comment(issue["number"], comment_body)
        count += 1

    print()
    print(f"Annotated {count} issues with OWNER ACCOUNT PENDING.")
    print()

    # Also assign all issues to the current user (snadaiapp-png) as a temporary measure
    # so they appear in "assigned to me" — but with the OWNER ACCOUNT PENDING comment
    # making clear this is temporary.
    print("NOTE: Issues remain unassigned. Assigning requires second account.")
    print("      Current operator (snadaiapp-png) is the implementation account,")
    print("      NOT the accountable Owner per TD-07-007.")

if __name__ == "__main__":
    main()
