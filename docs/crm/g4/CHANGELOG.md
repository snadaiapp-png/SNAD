# CHANGELOG

**Date**: 2026-08-06
**HEAD**: ab37bb40

---

## Commits in This Phase

### ab37bb40 — docs(crm): END-TO-END ZERO-TRUST CERTIFICATION — APPROVED
- Added END-TO-END-CERTIFICATION.md

### 029d9580 — fix(crm): remove 10 orphan components and 3 dead test files
- Deleted 10 orphan frontend components (never imported)
- Deleted 3 dead test files (never imported)
- 3,438 lines of dead code removed

### 2eaf556a — docs(crm): G4-CERTIFICATION.md — zero-trust audit, all 50 items verified
- Added G4-CERTIFICATION.md with complete audit evidence

### 09ce5a91 — docs(crm): G4 final certification — all 10 deliverables
- Added 10 G4 certification deliverables

### 7bb72ffe — fix(crm): G4 contract remediation — add POST /pipelines to OpenAPI, remove orphan crm-overview.tsx
- Added POST /pipelines endpoint to OpenAPI spec
- Added CreatePipelineRequest schema
- Added Idempotency-Key header to POST /pipelines
- Updated CrmOpenApiContractTest (EXPECTED_OPERATIONS 180→181)
- Deleted orphan crm-overview.tsx

---

## Files Changed Summary

| Action | Count |
|--------|-------|
| Modified | 2 |
| Deleted | 14 |
| Added | 12 |
| **Total** | **28** |

---

## Lines Changed

| Metric | Value |
|--------|-------|
| Insertions | 1,078 |
| Deletions | 3,596 |
| Net | -2,518 |
