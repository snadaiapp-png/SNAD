# CRM-003 Push Instructions — How to get the full implementation onto PR #502

## Situation

- **PR #502** is OPEN in DRAFT mode on GitHub
- The remote branch `crm/003-stable-api-contracts` is at SHA `61a703811a159fee88ef946fe9a9cb2692c8e910`
- That remote branch contains only **2 files** (2 doc files)
- The full implementation (43 files, 6587 insertions) exists as local commit `066b60ee09ac7ca847d5609e580d16cf8a7eeea1`
- This sandbox **cannot push** to GitHub (no credentials)
- A **git bundle** has been created at `download/crm-003-full-implementation.bundle` (64MB)
- A **patch file** has been created at `download/crm-003-full-implementation.patch` (342KB)

## What you need to do (repository owner)

### Option A — Git bundle (recommended, preserves exact SHA)

```bash
# In your local SNAD checkout:
cd /path/to/SNAD

# 1. Make sure your main is up to date
git fetch origin
git checkout main
git reset --hard origin/main
# Verify: git rev-parse HEAD → should be 89761eb9397e922b21917551299e2a2b9d478a86

# 2. Download the bundle file from the sandbox to your local machine
#    (the file is at /home/z/my-project/download/crm-003-full-implementation.bundle)

# 3. Fetch from the bundle
git fetch /path/to/crm-003-full-implementation.bundle crm/003-stable-api-contracts

# 4. Verify the commit arrived
git log --oneline 066b60ee -3
# Should show:
# 066b60ee feat(crm): establish stable API contracts and concurrency controls
# 89761eb9 fix(crm): execute and prove final acceptance gate (#501)

# 5. Update your local branch to point to this commit
git checkout crm/003-stable-api-contracts
git reset --hard 066b60ee09ac7ca847d5609e580d16cf8a7eeea1

# 6. Verify all 43 files are present
git diff --stat origin/main...HEAD | tail -5
# Should show: 43 files changed, 6587 insertions(+), 1 deletion(-)

# 7. Force push (the remote has different history — 2 doc commits — that we're replacing)
git push --force-with-lease origin crm/003-stable-api-contracts

# 8. Verify GitHub now shows the correct SHA
git ls-remote origin refs/heads/crm/003-stable-api-contracts
# Should show: 066b60ee09ac7ca847d5609e580d16cf8a7eeea1
```

### Option B — Patch file (alternative, creates new SHA)

If the bundle approach doesn't work, use the patch file:

```bash
cd /path/to/SNAD
git fetch origin
git checkout crm/003-stable-api-contracts
git reset --hard origin/crm/003-stable-api-contracts

# Apply the patch (will conflict on 2 doc files — take our version)
git am /path/to/crm-003-full-implementation.patch
# If conflicts occur on the 2 doc files:
git checkout --theirs docs/crm/contracts/CRM-API-CONTRACT-INVENTORY.md docs/crm/contracts/CRM-ERROR-CATALOG.md
git add docs/crm/contracts/CRM-API-CONTRACT-INVENTORY.md docs/crm/contracts/CRM-ERROR-CATALOG.md
git am --continue

# Push (regular push, no force needed since we're adding on top)
git push origin crm/003-stable-api-contracts
```

### Option C — If you already have the commit in your local repo

If your local repo already has commit `066b60ee` (e.g., from a previous session):

```bash
cd /path/to/SNAD
git checkout crm/003-stable-api-contracts
git reset --hard 066b60ee09ac7ca847d5609e580d16cf8a7eeea1
git push --force-with-lease origin crm/003-stable-api-contracts
```

## After pushing

1. Verify PR #502 now shows 43 changed files (not just 2)
2. Verify the PR head SHA is `066b60ee09ac7ca847d5609e580d16cf8a7eeea1`
3. Wait for all CI workflows to run on the new head SHA
4. Mark the PR as "Ready for review" (exit DRAFT mode) only after CI is green
5. Do NOT merge until independent verification is complete

## Local validations that PASS on this commit (all verified in sandbox)

| Check | Result |
|---|---|
| Workflow YAML validation (79 files) | PASS |
| API contract governance drift | PASS |
| CRM governance drift | PASS |
| OpenAPI artifact validity (21 paths, 9 schemas) | PASS |
| Generated TypeScript typecheck (tsc --noEmit) | PASS |
| Contract test classes (15 classes, 110 methods) | PASS (compiled) |

## What the full implementation includes

- **8 new Java packages** under `com.sanad.platform.crm`: `api`, `dto`, `mapper`, `error`, `pagination`, `concurrency`, `idempotency`
- **15 contract test classes** under `src/test/java/.../crm/contract/`
- **1 Flyway migration**: `V20260713_1__create_crm_idempotency_records.sql`
- **1 OpenAPI 3.1.0 artifact**: `docs/crm/contracts/openapi/crm-openapi.json`
- **1 generated TypeScript types file**: `apps/web/lib/api/generated/crm-api-types.ts`
- **1 new CI workflow**: `.github/workflows/crm-api-contract-validation.yml`
- **3 contract docs**: inventory, error catalog, versioning policy
- **1 evidence doc**: `docs/crm/evidence/CRM-003-API-CONTRACT-EVIDENCE.md`
- **2 governance scripts**: `api-contract-governance-check.sh`, `generate-crm-api-types.sh`
