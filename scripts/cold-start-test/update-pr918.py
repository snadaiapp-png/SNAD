#!/usr/bin/env python3
"""Update PR #918 description."""
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
            if (v.startswith("'") and v.endswith("'")) or (v.startswith('"') and v.endswith('"')):
                v = v[1:-1]
            os.environ[k.strip()] = v

GH_TOKEN = os.environ["GH_TOKEN"]

with open("/home/z/my-project/.pr-body-cold-start-v2.md") as f:
    body = f.read()

payload = {"body": body}

req = urllib.request.Request(
    "https://api.github.com/repos/snadaiapp-png/SNAD/pulls/918",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Authorization": f"token {GH_TOKEN}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
    },
    method="PATCH",
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read().decode("utf-8"))
        print(f"PR #{result.get('number')} updated")
        print(f"state: {result.get('state')}")
        print(f"head: {result.get('head',{}).get('sha','')[:12]}")
except urllib.error.HTTPError as e:
    print(f"HTTP {e.code}: {e.read().decode('utf-8')[:300]}")
except Exception as e:
    print(f"Error: {e}")
