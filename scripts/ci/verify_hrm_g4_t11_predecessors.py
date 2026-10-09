#!/usr/bin/env python3
"""G4-T11 exact-SHA predecessor certification. Fail closed on missing/failed checks."""
import json
import os
import sys
import urllib.request

REPO = "snadaiapp-png/SNAD"
PREDECESSORS = {
    "G4-T8 Payroll API": ("db4a220950e0d7c682263534c4f1e224c13622b8", ["focused-vitest", "Build Next.js Web"]),
    "G4-T9 Payroll Review UI": ("e6511a7bc1496086fa0f1fbc2c47383f516f7c2f", ["focused-vitest", "Build Next.js Web", "Full-stack HRM human preview"]),
    "G4-T10 Authenticated Acceptance": ("c43bd6e11c590ff49b7547de174e9024a1531b78", ["Build Next.js Web", "Full-stack HRM human preview"]),
}
def checks_for(sha):
    result = []
    page = 1
    while True:
        req = urllib.request.Request(
            f"https://api.github.com/repos/{REPO}/commits/{sha}/check-runs?per_page=100&page={page}",
            headers={"Accept": "application/vnd.github+json",
                     "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
                     "User-Agent": "SNAD-G4-T11"})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.load(response)
        result += data["check_runs"]
        if len(data["check_runs"]) < 100:
            break
        page += 1
    return result

for name, (sha, required) in PREDECESSORS.items():
    rows = checks_for(sha)
    if not rows:
        sys.exit(f"{name}: no checks found for {sha}")
    bad = [f"{x['name']}={x['status']}/{x['conclusion']}" for x in rows
           if x["status"] != "completed" or x["conclusion"] not in ("success", "skipped")]
    if bad:
        sys.exit(f"{name}: non-terminal or failing checks: {bad}")
    successful = {x["name"] for x in rows if x["conclusion"] == "success"}
    missing = [x for x in required if x not in successful]
    if missing:
        sys.exit(f"{name}: required success checks absent: {missing}")
    print(f"{name}: verified exact SHA={sha} checks={len(rows)} success={len(successful)}")
print("G4_T11_PREDECESSOR_EVIDENCE=PASS")
