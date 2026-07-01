#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FAILURES=0
WARNINGS=0

pass() { printf 'PASS  %s\n' "$1"; }
warn() { printf 'WARN  %s\n' "$1"; WARNINGS=$((WARNINGS + 1)); }
fail() { printf 'FAIL  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }

version_major() {
  printf '%s' "$1" | grep -Eo '[0-9]+' | head -1
}

if command -v docker >/dev/null 2>&1; then
  pass "Docker CLI found: $(docker --version)"
  if docker info >/dev/null 2>&1; then
    pass "Docker engine is reachable"
  else
    fail "Docker engine is not reachable"
  fi
  if docker compose version >/dev/null 2>&1; then
    pass "Docker Compose plugin found: $(docker compose version --short)"
  else
    fail "Docker Compose v2 plugin is required"
  fi
else
  fail "Docker is required"
fi

if command -v node >/dev/null 2>&1; then
  NODE_MAJOR="$(version_major "$(node --version)")"
  if [[ "${NODE_MAJOR}" -eq 24 ]]; then
    pass "Node.js 24 detected"
  else
    warn "Node.js 24 LTS is recommended; detected $(node --version)"
  fi
else
  warn "Node.js is absent; containerized runtime can still run, local web/mobile commands cannot"
fi

if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -1)"
  JAVA_MAJOR="$(printf '%s' "${JAVA_VERSION_OUTPUT}" | grep -Eo '"?[0-9]+' | tr -d '"' | head -1)"
  if [[ "${JAVA_MAJOR}" -ge 21 ]]; then
    pass "Java 21+ detected: ${JAVA_VERSION_OUTPUT}"
  else
    warn "Java 21+ is recommended for local backend work; detected ${JAVA_VERSION_OUTPUT}"
  fi
else
  warn "Java is absent; containerized backend can still run"
fi

if command -v mvn >/dev/null 2>&1; then
  pass "Maven found: $(mvn --version | head -1)"
else
  warn "Maven is absent; containerized backend can still run"
fi

if command -v python3 >/dev/null 2>&1; then
  pass "Python found: $(python3 --version)"
else
  fail "Python 3 is required by bootstrap and repository quality scripts"
fi

if command -v openssl >/dev/null 2>&1; then
  pass "OpenSSL found"
else
  warn "OpenSSL is absent; bootstrap will use Python secrets"
fi

if [[ -f "${ROOT_DIR}/.env" ]]; then
  pass ".env exists"
else
  fail ".env is missing; run make bootstrap"
fi

if [[ -f "${ROOT_DIR}/apps/web/package-lock.json" ]]; then
  pass "Web lockfile exists"
else
  fail "Web package-lock.json is missing"
fi

if [[ -f "${ROOT_DIR}/apps/mobile/package-lock.json" ]]; then
  pass "Mobile lockfile exists"
else
  warn "Mobile lockfile is missing until the mobile foundation is merged"
fi

if [[ -f "${ROOT_DIR}/apps/sanad-platform/pom.xml" ]]; then
  pass "Backend Maven manifest exists"
else
  fail "Backend pom.xml is missing"
fi

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 && [[ -f "${ROOT_DIR}/.env" ]]; then
  if (cd "${ROOT_DIR}" && docker compose --env-file .env config --quiet); then
    pass "Compose configuration is valid"
  else
    fail "Compose configuration validation failed"
  fi
fi

printf '\nResult: %d failure(s), %d warning(s)\n' "${FAILURES}" "${WARNINGS}"
[[ "${FAILURES}" -eq 0 ]]
