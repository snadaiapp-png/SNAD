#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

BACKEND="${NVD_ACTIVE_BACKEND:?NVD_ACTIVE_BACKEND is required}"
DEPENDENCY_CHECK_VERSION="${DEPENDENCY_CHECK_VERSION:-12.1.0}"
MAX_AGE_HOURS="${NVD_MAX_AGE_HOURS:-48}"
MIN_SIZE_BYTES="${NVD_MIN_SIZE_BYTES:-52428800}"
EVIDENCE_DIR="${EVIDENCE_DIR:-${ROOT_DIR}/artifacts/security/owasp}"
CANONICAL_DIR="${NVD_CANONICAL_DIR:-${ROOT_DIR}/.cache/dependency-check-data}"
CONSUMER_DIR="${RUNNER_TEMP:-/tmp}/snad-nvd-consumer"
VERIFY_DIR="${RUNNER_TEMP:-/tmp}/snad-nvd-verified"
REPORT_DIR="${ROOT_DIR}/apps/sanad-platform/target/dependency-check-report"

mkdir -p "${EVIDENCE_DIR}" "${CONSUMER_DIR}" "${VERIFY_DIR}" "${REPORT_DIR}"
rm -rf "${CONSUMER_DIR:?}/"* "${VERIFY_DIR:?}/"* "${CANONICAL_DIR}" "${REPORT_DIR}"
mkdir -p "${CONSUMER_DIR}" "${VERIFY_DIR}" "${CANONICAL_DIR}" "${REPORT_DIR}"

FAIL_REASONS=()
DECISION=pass

record_failure() {
  DECISION=fail
  FAIL_REASONS+=("$1")
  printf 'GATE_FAILURE: %s\n' "$1" >&2
}

write_terminal_evidence() {
  local parse_file="${EVIDENCE_DIR}/parsed-owasp-result.txt"
  local result="" total_dependencies="" total_vulnerabilities=""
  local high="" critical="" unknown="" analysis_exceptions=""
  if [[ -f "${parse_file}" ]]; then
    result="$(awk -F= '$1=="result"{print $2}' "${parse_file}" | tail -1)"
    total_dependencies="$(awk -F= '$1=="total_dependencies"{print $2}' "${parse_file}" | tail -1)"
    total_vulnerabilities="$(awk -F= '$1=="total_vulnerabilities"{print $2}' "${parse_file}" | tail -1)"
    high="$(awk -F= '$1=="high"{print $2}' "${parse_file}" | tail -1)"
    critical="$(awk -F= '$1=="critical"{print $2}' "${parse_file}" | tail -1)"
    unknown="$(awk -F= '$1=="unknown"{print $2}' "${parse_file}" | tail -1)"
    analysis_exceptions="$(awk -F= '$1=="analysis_exceptions"{print $2}' "${parse_file}" | tail -1)"
  fi
  local reasons_file
  reasons_file="$(mktemp)"
  printf '%s\n' "${FAIL_REASONS[@]:-}" > "${reasons_file}"
  python3 - "${EVIDENCE_DIR}/terminal-enforcement.json" "${DECISION}" "${reasons_file}" <<'PY'
import json
import os
import pathlib
import sys

output = pathlib.Path(sys.argv[1])
decision = sys.argv[2]
reasons = [line.strip() for line in pathlib.Path(sys.argv[3]).read_text(encoding="utf-8").splitlines() if line.strip()]
parse_values = {}
parse_path = output.parent / "parsed-owasp-result.txt"
if parse_path.exists():
    for line in parse_path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            parse_values[key] = value
verification_values = {}
verification_path = output.parent / "snapshot-verification.log"
if verification_path.exists():
    for line in verification_path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            verification_values[key] = value
payload = {
    "decision": decision,
    "reasons": reasons,
    "run_id": os.environ.get("GITHUB_RUN_ID", ""),
    "tested_sha": os.environ.get("GITHUB_SHA", ""),
    "event_name": os.environ.get("GITHUB_EVENT_NAME", ""),
    "backend": os.environ.get("NVD_ACTIVE_BACKEND", ""),
    "snapshot_id": verification_values.get("snapshot_id", ""),
    "snapshot_created_at": verification_values.get("created_at", ""),
    "snapshot_age_hours": verification_values.get("age_hours", ""),
    "database_sha256": verification_values.get("database_sha256", ""),
    "database_size_bytes": verification_values.get("database_size_bytes", ""),
    "parser": parse_values,
}
output.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
PY
  rm -f "${reasons_file}"
}

trap write_terminal_evidence EXIT

printf 'Downloading verified NVD snapshot from %s...\n' "${BACKEND}"
if ! python3 scripts/security/download_nvd_snapshot.py \
  --backend "${BACKEND}" \
  --destination "${CONSUMER_DIR}" \
  --repository "${GITHUB_REPOSITORY:-}" \
  --max-age-hours "${MAX_AGE_HOURS}" \
  2>&1 | tee "${EVIDENCE_DIR}/snapshot-download.log"; then
  record_failure snapshot_download_failed
  exit 1
fi
cp "${CONSUMER_DIR}/download-evidence.json" "${EVIDENCE_DIR}/download-evidence.json"

ARCHIVE_PATH="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archive_path"])' "${EVIDENCE_DIR}/download-evidence.json")"
MANIFEST_PATH="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["manifest_path"])' "${EVIDENCE_DIR}/download-evidence.json")"
CHECKSUMS_PATH="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["checksums_path"])' "${EVIDENCE_DIR}/download-evidence.json")"
SNAPSHOT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["snapshot_id"])' "${EVIDENCE_DIR}/download-evidence.json")"

printf 'Verifying snapshot contract, checksum, database digest, and age...\n'
if ! python3 scripts/security/verify_nvd_snapshot.py \
  --archive "${ARCHIVE_PATH}" \
  --manifest "${MANIFEST_PATH}" \
  --checksums "${CHECKSUMS_PATH}" \
  --expected-snapshot-id "${SNAPSHOT_ID}" \
  --expected-dc-version "${DEPENDENCY_CHECK_VERSION}" \
  --min-size "${MIN_SIZE_BYTES}" \
  --max-age-hours "${MAX_AGE_HOURS}" \
  --smoke-extract-dir "${VERIFY_DIR}" \
  2>&1 | tee "${EVIDENCE_DIR}/snapshot-verification.log"; then
  record_failure snapshot_verification_failed
  exit 1
fi
if ! grep -q '^verification_result=valid$' "${EVIDENCE_DIR}/snapshot-verification.log"; then
  record_failure snapshot_not_valid
  exit 1
fi

cp -a "${VERIFY_DIR}/data/." "${CANONICAL_DIR}/"
if [[ ! -s "${CANONICAL_DIR}/odc.mv.db" ]]; then
  record_failure canonical_database_missing
  exit 1
fi
find "${CANONICAL_DIR}" -maxdepth 1 -type f -printf '%f %s bytes\n' | sort > "${EVIDENCE_DIR}/canonical-database-files.txt"

printf 'Running OWASP Dependency-Check in offline vulnerability-data mode...\n'
SCAN_LOG="${EVIDENCE_DIR}/owasp-scan.log"
set +e
(
  cd apps/sanad-platform
  mvn --batch-mode --no-transfer-progress \
    org.owasp:dependency-check-maven:${DEPENDENCY_CHECK_VERSION}:check \
    -Powasp-offline-gate \
    -DdataDirectory="${CANONICAL_DIR}" \
    -DautoUpdate=false \
    -DfailOnError=false \
    -DfailBuildOnCVSS=11 \
    -DossIndexAnalyzerEnabled=false \
    -DhostedSuppressionsEnabled=false \
    -DversionCheckEnabled=false \
    -DoutputDirectory=target/dependency-check-report
) > "${SCAN_LOG}" 2>&1
SCAN_EXIT_CODE=$?
set -e
printf 'scan_exit_code=%s\n' "${SCAN_EXIT_CODE}" > "${EVIDENCE_DIR}/scan-execution.env"
tail -100 "${SCAN_LOG}" || true

DIRECT_JSON="${ROOT_DIR}/apps/sanad-platform/target/dependency-check-report.json"
DIRECT_HTML="${ROOT_DIR}/apps/sanad-platform/target/dependency-check-report.html"
JSON_REPORT="${REPORT_DIR}/dependency-check-report.json"
HTML_REPORT="${REPORT_DIR}/dependency-check-report.html"
if [[ ! -f "${JSON_REPORT}" && -f "${DIRECT_JSON}" ]]; then
  mv "${DIRECT_JSON}" "${JSON_REPORT}"
fi
if [[ ! -f "${HTML_REPORT}" && -f "${DIRECT_HTML}" ]]; then
  mv "${DIRECT_HTML}" "${HTML_REPORT}"
fi

if [[ ! -s "${JSON_REPORT}" ]]; then
  record_failure json_report_missing
fi
if [[ ! -s "${HTML_REPORT}" ]]; then
  record_failure html_report_missing
fi
if [[ "${DECISION}" == fail ]]; then
  exit 1
fi
if ! python3 -m json.tool "${JSON_REPORT}" >/dev/null; then
  record_failure json_report_invalid
fi
if ! grep -qi 'Dependency-Check' "${HTML_REPORT}"; then
  record_failure html_report_marker_missing
fi

if grep -Eqi 'services\.nvd\.nist\.gov|nvd\.nist\.gov/developers|ossindex\.sonatype\.org|Sonatype OSS Index API' "${SCAN_LOG}"; then
  record_failure offline_network_violation
fi

if ! python3 scripts/ci/parse_owasp_report.py "${JSON_REPORT}" \
  2>&1 | tee "${EVIDENCE_DIR}/parsed-owasp-result.txt"; then
  record_failure parser_execution_failed
  exit 1
fi

PARSED_RESULT="$(awk -F= '$1=="result"{print $2}' "${EVIDENCE_DIR}/parsed-owasp-result.txt" | tail -1)"
TOTAL_DEPENDENCIES="$(awk -F= '$1=="total_dependencies"{print $2}' "${EVIDENCE_DIR}/parsed-owasp-result.txt" | tail -1)"
HIGH="$(awk -F= '$1=="high"{print $2}' "${EVIDENCE_DIR}/parsed-owasp-result.txt" | tail -1)"
CRITICAL="$(awk -F= '$1=="critical"{print $2}' "${EVIDENCE_DIR}/parsed-owasp-result.txt" | tail -1)"
UNKNOWN="$(awk -F= '$1=="unknown"{print $2}' "${EVIDENCE_DIR}/parsed-owasp-result.txt" | tail -1)"
ANALYSIS_EXCEPTIONS="$(awk -F= '$1=="analysis_exceptions"{print $2}' "${EVIDENCE_DIR}/parsed-owasp-result.txt" | tail -1)"

[[ "${PARSED_RESULT}" == pass ]] || record_failure "parsed_result_${PARSED_RESULT:-missing}"
[[ "${TOTAL_DEPENDENCIES}" =~ ^[0-9]+$ && "${TOTAL_DEPENDENCIES}" -gt 0 ]] || record_failure invalid_dependency_count
[[ "${HIGH}" == 0 ]] || record_failure "high_vulnerabilities_${HIGH:-missing}"
[[ "${CRITICAL}" == 0 ]] || record_failure "critical_vulnerabilities_${CRITICAL:-missing}"
[[ "${UNKNOWN}" == 0 ]] || record_failure "unknown_vulnerabilities_${UNKNOWN:-missing}"
[[ "${ANALYSIS_EXCEPTIONS}" == 0 ]] || record_failure "analysis_exceptions_${ANALYSIS_EXCEPTIONS:-missing}"

write_terminal_evidence
trap - EXIT
if [[ "${DECISION}" != pass ]]; then
  cat "${EVIDENCE_DIR}/terminal-enforcement.json"
  exit 1
fi

printf 'OWASP gate passed for snapshot %s with %s dependencies.\n' "${SNAPSHOT_ID}" "${TOTAL_DEPENDENCIES}"
