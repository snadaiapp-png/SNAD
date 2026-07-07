#!/usr/bin/env python3
"""
SNAD Frontend Smoke Validator — Fail-Closed
Validates frontend auth route response and generates metadata.
Exit 0 = PASS, Exit 1 = FAIL.
"""
import argparse
import json
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="SNAD Frontend Smoke Validator")
    parser.add_argument("--html-file", required=True)
    parser.add_argument("--metadata-file", required=True)
    parser.add_argument("--http-status", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()

    failure_type = None
    http_status_val = 0
    brand_found = False

    # 1. Validate HTTP status
    try:
        http_status_val = int(args.http_status)
    except (ValueError, TypeError):
        failure_type = "INVALID_HTTP_STATUS"

    # 2. Check not 500
    if failure_type is None and http_status_val >= 500:
        failure_type = "HTTP_5XX"

    # 3. Check 200 or approved redirect (302/307)
    if failure_type is None and http_status_val not in (200, 302, 307):
        failure_type = "UNEXPECTED_HTTP_STATUS"

    # 4. Check response file exists
    if failure_type is None:
        html_path = Path(args.html_file)
        if not html_path.exists():
            failure_type = "MISSING_HTML_FILE"
        else:
            try:
                html_content = html_path.read_text(encoding="utf-8", errors="ignore")
            except IOError:
                failure_type = "HTML_READ_ERROR"
            else:
                # 5. Check SNAD or سند in HTML
                if "snad" in html_content.lower() or "سند" in html_content:
                    brand_found = True
                else:
                    failure_type = "BRAND_IDENTITY_MISSING"

    result = "PASS" if failure_type is None else "FAIL"

    metadata = {
        "port": args.port,
        "url": args.url,
        "processStarted": True,
        "httpStatus": http_status_val,
        "brandNamePresent": brand_found,
        "result": result,
        "failureType": failure_type,
    }

    try:
        Path(args.metadata_file).write_text(json.dumps(metadata, indent=2))
    except IOError as e:
        print(f"FATAL: Cannot write metadata: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"Frontend smoke: {result}")
    if failure_type:
        print(f"  Failure type: {failure_type}")
    print(f"  HTTP status: {http_status_val}")
    print(f"  Brand found: {brand_found}")

    if result == "FAIL":
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()
