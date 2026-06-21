#!/usr/bin/env bash
set -euo pipefail

base="${PRODUCTION_BASE_URL%/}"
status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$base/actuator/health")"
[ "$status" = "200" ]
