# SANAD Stage 08 — Evidence Framework

**Document ID:** `SANAD-ST08-EVIDENCE-001`
**Stage:** 08
**Date:** 2026-07-06

---

## 1. Purpose

Defines what evidence is required for each Gate and Epic completion. Hand-written summaries are NOT a substitute for original evidence.

---

## 2. Evidence Package Contents

For every Gate or completed Epic:

* Release SHA.
* PRs (merged and open).
* Reviews (reviewer, decision, timestamp).
* CI runs (run IDs, results).
* Test results (unit, integration, security, performance).
* Security results (SAST, DAST, dependency, supply-chain).
* Performance results (latency, throughput).
* Architecture decisions (ADRs).
* Screenshots (where applicable).
* API specifications.
* Migration evidence (up + down tested).
* Deployment evidence (deploy logs, smoke tests).
* Rollback evidence (rollback tested in non-prod).
* Known limitations.
* Residual risks.
* Owner approval (with timestamp UTC).

---

## 3. Storage

* Evidence packages stored in `docs/stage-08/evidence/<gate-or-epic>/`.
* Each package has `README.md` index linking all artifacts.
* Original artifacts stored as files (PDF, JSON, HTML) where applicable.

---

## 4. Verification

* QA & Release Owner verifies evidence before gate acceptance.
* Project Manager signs off gate acceptance.
* Evidence retained for 7 years (audit requirement).

---

## 5. Template

```markdown
# Evidence Package: <Gate/Epic>

**Release SHA:** <sha>
**Date:** <date>
**Owner:** <owner>

## PRs
- #<number> <title> <merge-sha>

## CI Runs
- <run-id> <workflow> <result>

## Test Results
- Unit: <pass/fail> <count>
- Integration: <pass/fail> <count>
- Security: <pass/fail> <count>
- Performance: <pass/fail> <metrics>

## Security Results
- SAST: <result>
- DAST: <result>
- Dependency: <result>
- Supply-chain: <result>

## ADRs
- ADR-<number> <title>

## Migration Evidence
- Up: <result>
- Down: <result>

## Deployment Evidence
- Deploy logs: <link>
- Smoke tests: <result>

## Rollback Evidence
- Rollback tested: <yes/no> <result>

## Known Limitations
- <list>

## Residual Risks
- <list>

## Owner Approval
- <owner> APPROVED <timestamp UTC>
```
