# EVIDENCE MANIFEST — SNAD-P0-G1-1462-FINAL-CLOSURE

**Certified SHA:** `f95a30b64e75815d571e582e1b555c01bcd53abe` · **Package date:** 2026-11-02 · **Executor:** independent engineering & security execution platform (this engagement) · **Classification:** internal security evidence — contains **no secrets, no customer data, no token material** (all previously exposed credentials are treated as compromised and are excluded by policy; rotation remains REQUIRED for them).

## Document set (SHA-256 pinned)

| File | SHA-256 |
|---|---|
| 01-LIVE-BASELINE.md | `abb37493b3833e3ee17d7ce6a0da076e4587aecd2330056cb4f98bd314d7014d` |
| 02-POST-MERGE-VERIFICATION.md | `ec423dbf49087b5d1dc4b5850b1964cf17e2f0235fcfe20ee80e6d090c06cc75` |
| 03-SIX-TABLE-RLS-ACCEPTANCE.md | `85d2892c1a0ea96d84b6df371ea61c51183eedcbcca2ec389a7100cfae92522f` |
| 04-AUTH-USERS-SUBSCRIPTIONS-E2E.md | `38dd1bed680592a0b281db00675c3e093203597bec8dcb92e74b7b6a912c1e79` |
| 05-FLYWAY-MIGRATION-VERIFICATION.md | `9eadce4d8723e245256a6a55f07be9f62eb35aaf867d68f1e8756d4facb2479c` |
| 06-BACKUP-RESTORE-CERTIFICATION.md | `a4fb21376fd25c790559b2e39b1ad6abe6fe5c98366e41d8d385e5e9b7fb399d` |
| 07-INDEPENDENT-SECURITY-APPROVAL.md | `5c5d9972c86c9918d39b15b28e43e115d992f5998f1654499d06bf7c48e3afac` |
| 08-PRODUCTION-READINESS.md | `334b20fbe7764e28eaea9c3cd6b307cf225d09b1a49a08b9ab3d3e949b25e6cf` |
| 09-PROTECTED-PRODUCTION-VERIFICATION.md | `cfd4e860c1f6ade3642fbec1703dcf52e22a8f4dc543d8eec92c85d26d8171d7` |
| 10-G1-CERTIFICATION-DECISION.md | `8f401acae63504dab68c357e11cc34cdf5886c01b8239464d3e661d6326bb9ee` |
| 11-ISSUE-1462-FINAL-CLOSURE.md | `af472f615a607f6ab661c7a488e627e1ea261af3f3d98296fe7430eba57127ce` |

(Run `sha256sum docs/security/g1-final-closure/*.md` in the PR branch to re-verify.)

## Raw artifacts (`evidence-raw/`, 57 files — key items)

| Artifact | Contents |
|---|---|
| `repo.json`, `main-head.json`, `main-ref.json`, `main-checkruns.json`, `main-status.json` | live main identity & check state |
| `pr146{5,6,7}.json`, `-files.json`, `-commits.json`, `-reviews.json`, `-checks.json` | merged-PR forensics incl. single-reviewer finding |
| `issue1462.json`, `-comments.json`, `-timeline.json`, `-events.json` | issue state: OPEN, 0/6, untouched checkboxes |
| `run-postmerge-41415645715.json`, `run-postmerge-jobs.json`, `postmerge-jobs-41415592079.txt`, `postmerge-run-detail.json` | post-merge queue semantics + predecessor full-green evidence |
| `run-y2-41415840956.json`, `run-y2-jobs.json`, `y2-job-log.txt` | Y2 refusal forensics (verbatim guard error) |
| `runs-main-recent.json`, `workflows.json`, `releases.json`, `tags.json` | run surface on the SHA; no release published |
| `branch-protection.json`, `codeowners.json` | protection surface (incl. `require_code_owner_reviews=false` gap) |
| `finalclosure-flyway-rehearsal{,-v2,-v3,-v4}.log` | Scenarios A/B/C evolution incl. harness honesty notes |
| `final-B3-new-migrations.txt`, `final-C3-default-migrate.txt`, `final-C3-outoforder-probe.txt` | scenario B/C evidence (incl. verbatim fail-closed refusal) |
| `final-A-key-migration-checksums.txt`, `final-key-migration-sha256.txt`, `final-A-sanad-grants-from-chain.txt` | checksum + chain-only-grant proofs |
| `finalclosure-sixtable-matrix-v2.log` | 57/57 MATRIX=GREEN |
| `finalclosure-e2e-lifecycle-pure-chain.log`, `finalclosure-e2e-lifecycle-F01fix-verification.log` | 36/38 (F-01) and 38/38 (fix contract) |

## Reproduction pointers

- Local tooling: isolated PostgreSQL 17.6.0 + Flyway CLI 11.7.2 (same versions as this evidence), scripts under the executor's `scripts/` (`finalclosure_flyway_rehearsal_v4.sh`, `finalclosure_sixtable_matrix_v2.py`, `finalclosure_e2e_lifecycle.py`).
- Every claim in 01–11 is bound to: SHA, environment, UTC timestamp, command, expected vs actual, and one of `VERIFIED_PASS / VERIFIED_FAIL / BLOCKED / UNVERIFIED / NOT_APPLICABLE_WITH_EVIDENCE` (matrix in 01 §0.2).
