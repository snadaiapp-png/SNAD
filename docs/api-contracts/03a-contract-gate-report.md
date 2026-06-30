# Stage 03A — Contract Gate Report

**Stage:** 03A
**Branch:** `infra/03a-api-contract-enforcement`

## 1. Objective

Replace the placeholder API compatibility script (which emitted `SKIPPED_NO_BASELINE` with exit 0) with a real comparison tool that:
- Uses `oasdiff` v1.21.0 (pinned, downloaded hermetically if absent)
- Compares the runtime-generated OpenAPI against the committed baseline
- FAILS on breaking changes
- FAILS on missing baseline (no `SKIPPED` status)
- Proves the engine works via automated fixtures

## 2. Tooling

### oasdiff (pinned version)

- **Binary:** `oasdiff` v1.21.0
- **Source:** GitHub releases (`https://github.com/oasdiff/oasdiff/releases/download/v1.21.0/oasdiff_1.21.0_linux_amd64.tar.gz`)
- **Install strategy:** If `oasdiff` is not on PATH, the script hermetically downloads it to `~/.cache/oasdiff/1.21.0/` on first use.
- **Verification:** `oasdiff --version` is logged on every run.

### Comparison script

`scripts/ci/check-api-contract-compatibility.sh`:

1. Verifies oasdiff is available (downloads if needed).
2. Verifies the baseline exists and is non-trivial (>1 KB).
3. Verifies the baseline metadata file exists.
4. Verifies the runtime OpenAPI (`build/api-contract/openapi-current.json`) exists.
5. Runs `oasdiff breaking --fail-on=ERR` (baseline → current).
6. Runs the fixture self-test (proves the engine detects breaking changes).
7. Returns `PASS` only if no breaking changes AND all fixtures behave as expected.

## 3. Fixture Self-Test

Located at `tests/contracts/`:

### Breaking fixtures (must FAIL)

| Fixture | Tests |
|---------|-------|
| `breaking/path-removed/` | Path removed → breaking |
| `breaking/type-changed/` | Schema field type changed (string → integer) → breaking |
| `breaking/required-field-added/` | New required request field added → breaking |

### Non-breaking fixtures (must PASS)

| Fixture | Tests |
|---------|-------|
| `non-breaking/optional-field-added/` | Optional response field added → non-breaking |
| `non-breaking/new-endpoint/` | New endpoint added → non-breaking |

Each fixture has `baseline.yaml` and `revision.yaml`. The script runs `oasdiff breaking` on each and verifies the exit code matches the expected outcome (1 for breaking, 0 for non-breaking).

## 4. Python pytest Module

`tests/contracts/test_contract_fixtures.py` (10 tests):
- Verifies fixtures directory exists
- Verifies all required breaking fixtures present
- Verifies all required non-breaking fixtures present
- For each breaking fixture: verifies oasdiff exits 1
- For each non-breaking fixture: verifies oasdiff exits 0
- Verifies baseline metadata file is valid JSON
- Verifies baseline path/schema counts match metadata

## 5. Failure Modes (NO SKIPS ALLOWED)

| Condition | Result |
|-----------|--------|
| Baseline missing | `FAIL_NO_BASELINE` (exit 1) |
| Baseline too small (<1 KB) | `FAIL_BASELINE_TOO_SMALL` (exit 1) |
| Baseline metadata missing | `FAIL` (exit 1) |
| Current runtime spec missing | `FAIL_NO_CURRENT_SPEC` (exit 1) |
| Current spec too small | `FAIL` (exit 1) |
| oasdiff detects breaking change | `FAIL_BREAKING_CHANGE` (exit 1) |
| oasdiff internal error | `FAIL_OASDIFF_ERROR` (exit 1) |
| Any fixture behaves incorrectly | `FAIL_FIXTURES` (exit 1) |

There is NO `SKIPPED_NO_BASELINE` or `SKIPPED_EMPTY_BASELINE` status. Missing baseline always fails.

## 6. CI Integration

The `api-contract` job runs:

```bash
bash scripts/ci/check-api-contract-compatibility.sh
```

If the script exits non-zero, the `api-contract` job fails, which fails the `quality-gate` aggregation job.

## 7. Local Verification

```bash
# Generate runtime OpenAPI (writes to apps/sanad-platform/build/api-contract/openapi-current.json)
cd apps/sanad-platform && ./mvnw -B -ntp test -Dtest="OpenApiContractExportTest"

# Run the contract compatibility check
cd ../..
bash scripts/ci/check-api-contract-compatibility.sh

# Run the Python fixtures test
python3 -m pytest tests/contracts/ -v
```

## 8. Related Debts

- CD-03-P1-001 (mandatory api-contract CI job) — CLOSED
- CD-03-P1-002 (API compatibility script does not compare runtime contracts) — CLOSED
