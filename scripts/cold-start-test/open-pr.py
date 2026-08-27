#!/usr/bin/env python3
"""Open PR for perf/cold-start-profiling branch."""
import json
import os
import urllib.request
import urllib.error

with open("/tmp/my-project/.secrets") as f:
    for line in f:
        line = line.strip()
        if line.startswith("export "):
            line = line[7:]
        if "=" in line and not line.startswith("#"):
            k, _, v = line.partition("=")
            v = v.strip()
            # Strip surrounding single or double quotes
            if (v.startswith("'") and v.endswith("'")) or (v.startswith('"') and v.endswith('"')):
                v = v[1:-1]
            os.environ[k.strip()] = v

GH_TOKEN = os.environ["GH_TOKEN"]

with open("/home/z/my-project/.pr-body-cold-start.md") as f:
    body = f.read()

payload = {
    "title": "perf(startup): add BufferingApplicationStartup + lifecycle timeline logger for cold-start profiling",
    "head": "perf/cold-start-profiling",
    "base": "main",
    "body": body,
    "draft": False,
}

req = urllib.request.Request(
    "https://api.github.com/repos/snadaiapp-png/SNAD/pulls",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Authorization": f"token {GH_TOKEN}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
    },
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read().decode("utf-8"))
        print(f"PR #{result.get('number')} created: {result.get('html_url')}")
        print(f"  state: {result.get('state')}")
        print(f"  mergeable: {result.get('mergeable')}")
        print(f"  mergeable_state: {result.get('mergeable_state')}")
except urllib.error.HTTPError as e:
    body = e.read().decode("utf-8")
    print(f"HTTP {e.code}: {body[:500]}")
except Exception as e:
    print(f"Error: {e}")
