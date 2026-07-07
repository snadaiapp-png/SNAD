#!/usr/bin/env python3
"""
SNAD Backend Smoke Validator — Fail-Closed
Validates backend health endpoint response and generates metadata.
Exit 0 = PASS, Exit 1 = FAIL.
"""
import argparse
import json
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="SNAD Backend Smoke Validator")
    parser.add_argument("--health-file", required=True)
    parser.add_argument("--metadata-file", required=True)
    parser.add_argument("--http-status", required=True)
    parser.add_argument("--application-port", type=int, required=True)
    parser.add_argument("--management-port", type=int, required=True)
    parser.add_argument("--health-url", required=True)
    args = parser.parse_args()

    failure_type = None
    http_status_val = 0
    health_status = "UNKNOWN"

    # 1. Validate HTTP status is numeric
    try:
        http_status_val = int(args.http_status)
    except (ValueError, TypeError):
        failure_type = "INVALID_HTTP_STATUS"

    # 2. Check HTTP 200
    if failure_type is None and http_status_val != 200:
        failure_type = "HTTP_NOT_200"

    # 3. Read health JSON
    if failure_type is None:
        health_path = Path(args.health_file)
        if not health_path.exists():
            failure_type = "MISSING_HEALTH_FILE"
        else:
            try:
                health_data = json.loads(health_path.read_text())
            except (json.JSONDecodeError, IOError):
                failure_type = "INVALID_HEALTH_JSON"
                health_data = {}
            else:
                health_status = health_data.get("status", "MISSING")
                if health_status != "UP":
                    failure_type = "HEALTH_NOT_UP"

    result = "PASS" if failure_type is None else "FAIL"

    metadata = {
        "applicationPort": args.application_port,
        "managementPort": args.management_port,
        "healthUrl": args.health_url,
        "processStarted": True,
        "httpStatus": http_status_val,
        "healthStatus": health_status,
        "result": result,
        "failureType": failure_type,
    }

    # 5. Write metadata
    try:
        Path(args.metadata_file).write_text(json.dumps(metadata, indent=2))
    except IOError as e:
        print(f"FATAL: Cannot write metadata: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"Backend smoke: {result}")
    if failure_type:
        print(f"  Failure type: {failure_type}")
    print(f"  HTTP status: {http_status_val}")
    print(f"  Health status: {health_status}")

    if result == "FAIL":
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()
