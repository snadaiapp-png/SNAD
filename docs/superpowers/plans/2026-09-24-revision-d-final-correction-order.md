# SANAD Unified Authorization / Partner Control Plane — Revision D Final Correction Order

**Date:** 2026-09-24  
**Type:** Normative docs-only correction order / R3 independent-review closure input  
**Applies to branch:** `review/unified-control-plane-revision-c-reconstructed`  
**Applies on top of:** `55046328da9cd390a79d9c981adf75c38dbed349`  
**Scope:** the existing 10 planning/specification documents only; NO product code, migration, workflow, runtime config, or production state may be changed by this correction pass.  
**Precedence:** this Revision D order supersedes every conflicting Revision C sentence in the 10 documents. When the source documents are rewritten, their resulting text MUST be semantically identical to this order.  
**Authorization state:** `WAVE_1_AUTHORIZED=NO`, `PLAN_READY=NO` until an independent re-review of the rewritten Revision D documents passes.

---

## 0. Mission

Produce one coherent Revision D document set from the published Revision C branch without changing the approved architecture. The correction is successful only when all contradictions below are removed from the source documents themselves, the self-review is regenerated from scratch, and a fresh independent review finds no unresolved internal-consistency blocker.

The executor MUST NOT treat this order as permission to implement product code. This is a planning correction only.

## 1. Files that MUST be rewritten in the correction commit

Exactly these 10 existing files are in scope:

1. `docs/superpowers/plans/2026-09-23-self-review-checklist-vs-spec.md`
2. `docs/superpowers/plans/2026-09-23-unified-authorization-partner-control-plane-master-implementation.md`
3. `docs/superpowers/plans/2026-09-23-wave-1-unified-authorization-core.md`
4. `docs/superpowers/plans/2026-09-23-wave-2-partner-delegated-administration.md`
5. `docs/superpowers/plans/2026-09-23-wave-3-commercial-tax-identity-branding.md`
6. `docs/superpowers/plans/2026-09-23-wave-4-partner-tenant-billing.md`
7. `docs/superpowers/plans/2026-09-23-wave-5-partner-settlement-sanad-billing.md`
8. `docs/superpowers/plans/2026-09-23-wave-6-notifications-executive-dashboards.md`
9. `docs/superpowers/plans/2026-09-23-wave-7-cutover-security-release.md`
10. `docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md`

No eleventh path is permitted in the correction commit.

## 2. Global Revision D rules

### D-01 — Revision identity

- Spec, master, self-review, and all seven wave headers MUST identify the effective planning contract as **Revision D / R3 final correction**.
- Historical references to Rev A/B/C may remain only when explicitly describing provenance or a superseded decision.
- Any sentence saying an older revision is "binding" for current execution MUST be changed to Revision D.
- The published reconstructed Revision C SHA `55046328da9cd390a79d9c981adf75c38dbed349` is provenance, not the final planning SHA.

### D-02 — Two task classes; no fabricated RED

The blanket statement "every task is RED → GREEN" is replaced everywhere by this exact doctrine:

1. **Implementation task:** changes tracked product/schema/config/test code and MUST follow test-first RED → minimal implementation → GREEN → affected regression → commit.
2. **Verification / evidence / cutover-stage task:** does not invent a failing test or tracked implementation merely to satisfy a template. It follows PRECONDITION → VERIFY → EVIDENCE. Its evidence is untracked (`snad-evidence/`) or a CI artifact. It MUST NOT create a tracked "evidence commit" after the gate.

Any existing task whose Step 1/2/3 currently says `none` solely because it is an evidence/stage task is rewritten under the second model rather than falsely claiming TDD.

### D-03 — Same-SHA evidence

For every wave and every G7 stage:

- `FINAL_WAVE_SHA` / `STAGE_SHA` is the final tracked implementation/configuration commit being tested.
- `git status --short` must be clean when the gate starts.
- All required verification runs execute against that identical SHA.
- Logs, screenshots, surefire XML, reports, and evidence bundles are untracked or CI artifacts.
- **There is NO tracked evidence commit after a passing gate.**
- Any tracked commit after the gate invalidates the evidence and requires the complete gate to be rerun at the new SHA.

Therefore the following evidence tasks MUST have their current "Step 6 exact commit" removed/replaced with `NO TRACKED COMMIT — evidence external/untracked; report FINAL_WAVE_SHA/STAGE_SHA`:

- W1 Task 17
- W2 Task 14
- W3 Task 13
- W4 Task 21
- W5 Task 16
- W6 Task 10
- W7 Task 15

The master TDD/evidence protocol and self-review MUST state the same rule.

---

## 3. W2 — canonical capability and session invalidation corrections

### D-10 — `SUBSCRIPTION.MANAGE` is mandatory end-to-end

`SUBSCRIPTION.MANAGE` is the canonical trial-continuation/subscription-administration authority and MUST appear consistently in all of the following:

- spec §6.1 Capability Contract Table;
- W2 Task 5 migration seed list and seed-contract test;
- W2 Global Constraint delegation vocabulary;
- W2 Task 10 static delegation allowlist;
- W2 Task 10 positive tests;
- W2 Task 12 delegated subscription/provisioning surface where the continuation action is consumed;
- W4 continuation API/service tests and UI gating;
- self-review §6.1 / §13 mappings.

The W2 Task 10 allowlist MUST be exactly the approved canonical set including:

```text
TENANT.CREATE
TENANT.ACTIVATE
TENANT.SUSPEND
TENANT.USER.MANAGE
TENANT.AUTHORIZATION.MANAGE
SUBSCRIPTION.CREATE
SUBSCRIPTION.MANAGE
SUBSCRIPTION.UPGRADE
SUBSCRIPTION.DOWNGRADE
SUBSCRIPTION.CANCEL
BILLING.READ
BILLING.MANAGE
```

No parallel `PARTNER.SUBSCRIPTION.*` namespace may be introduced.

### D-11 — immediate invalidation covers four mutation kinds

W2 Task 7 Step 3 MUST implement the same invariant already demanded by its Files/Interfaces/Step 1:

For each of `create-ACTIVE`, `suspend`, `role downgrade`, and `partner transfer`, in the SAME transaction:

1. mutate membership state/role;
2. increment the affected `users.session_version`;
3. call `SessionVersionCache.invalidate(tenantId, userId)`;
4. evict the partner-membership/JWT-claim cache entry keyed by the old session version/context.

The implementation text MUST NOT say "suspension path" only. The test MUST prove the old JWT is rejected on the very next request for all four mutations and the newly minted token reflects the new membership immediately with no sleep/TTL wait.

---

## 4. W3 — `platform_files` isolation and storage-honesty corrections

### D-20 — platform-owned file rows require `app.partner_id IS NULL`

The principal-aware `platform_files` RLS policy has three disjoint branches. They MUST be written and tested as:

```text
TENANT branch:
  tenant_id = app.tenant_id
  AND tenant_id != CONTROL_PLANE_TENANT
  AND partner_id IS NULL

PARTNER branch:
  tenant_id = CONTROL_PLANE_TENANT
  AND partner_id IS NOT NULL
  AND partner_id = app.partner_id

PLATFORM branch:
  tenant_id = CONTROL_PLANE_TENANT
  AND partner_id IS NULL
  AND app.tenant_id = CONTROL_PLANE_TENANT
  AND app.partner_id IS NULL
```

A partner session carrying the control-plane tenant carrier MUST NOT see platform-owned rows. `PlatformFilesForceRlsPostgresTest` MUST include this explicit negative case. The migration MUST first drop the exact existing policy name `platform_files_tenant_isolation`, then leave exactly one effective principal policy, ENABLE+FORCE.

### D-21 — registration-only service cannot claim magic-byte validation

`LogoUploadService.registerStored(principalId, storageReference, mimeType, sizeBytes, checksumSha256)` receives no bytes and therefore MUST NOT claim to validate PNG/JPEG magic bytes or detect an SVG whose caller lies about MIME.

For Revision D registration-only scope, validation is limited to values that the service actually possesses:

- `storageReference` non-null/non-blank;
- declared MIME allowlist: `image/png`, `image/jpeg` only;
- `sizeBytes > 0 && sizeBytes <= 2_097_152`;
- lowercase/normalized SHA-256 format exactly 64 hex chars;
- ownership/principal congruence and FORCE-RLS context;
- atomic `platform_files` row + `brand_profiles` pointer transaction.

Tests MUST reject declared `image/svg+xml`, oversized metadata, blank storage reference, malformed checksum, and cross-principal registration. They MUST NOT assert content-sniffing from metadata.

Real magic-byte/content verification belongs to the future trusted byte-storage adapter/read path. `REAL_BYTE_STORAGE_ADAPTER` remains an explicit out-of-scope dependency and blocks any end-to-end multipart/content-validation claim, but does not block W3 registration scope.

The spec §14.3, W3 Task 9, master risk text, and self-review MUST all use this exact scope distinction.

---

## 5. W4 — credit-note Finance correction model

### D-30 — journal legs/counts must be mathematically correct

Delete every statement claiming "exactly two journal lines" while simultaneously describing two debits and one credit.

Canonical Revision D correction entry:

- `preTaxCorrectionMinor = grossCorrectionMinor - taxMinor`;
- DEBIT `contra-revenue / sales-returns` by `preTaxCorrectionMinor`;
- if `taxMinor > 0`, DEBIT `tax-payable relief` by `taxMinor`;
- CREDIT `accounts-receivable / customer credit balance` by `grossCorrectionMinor`;
- total debits MUST equal total credits;
- no `finance_payments` row is created;
- no existing Finance payment is mutated;
- no negative customer invoice is issued.

Materialized line count is therefore:

```text
taxMinor > 0  => 3 non-zero journal lines
taxMinor = 0  => 2 non-zero journal lines
```

`FinanceCorrectionAccountingPostgresTest` MUST assert account roles, signed amounts, and zero-sum balance, not a hard-coded contradictory count.

### D-31 — paid invoices use remaining creditable gross, not AR open balance

A credit note may apply to an OPEN or already-PAID invoice. Therefore validation MUST NOT be `amount <= open balance`, because a PAID invoice can have AR open balance zero.

Canonical limit:

```text
remainingCreditableGross = originalInvoiceGross
                           - sum(non-void prior applied credit-note gross corrections)

0 < requestedCreditGross <= remainingCreditableGross
```

Finance derives the post-correction receivable/customer-credit position from the authoritative invoice, payments, and corrections. Partner billing does not write that derived status directly.

Void is represented by an additive reversing Finance correction linked idempotently to the original correction; history is never deleted or rewritten.

Spec §19/§31.3, W4 Task 18, W5 collected-cash/correction read semantics, and self-review MUST agree with this model.

---

## 6. W5 — settlement Finance authority

### D-40 — `FinancePrincipalInvoicePort` is the only SETTLEMENT mirror port

W5 MUST identify its effective spec as Revision D.

Delete/replace any W5 global constraint or architecture sentence saying SANAD→Partner SETTLEMENT invoices ride `SubscriptionFinancePort.ensureInvoice(tenantId, …)`.

Canonical authority:

```text
FinancePrincipalInvoicePort.ensurePrincipalInvoice(
    settlementInvoiceId,
    sellerPlatformPrincipalId,
    buyerPartnerPrincipalId,
    amountMinor,
    currencyCode
)
```

The control-plane tenant is only the RLS/storage carrier. No fabricated tenant/subscription UUID may be used to represent the partner principal. `SubscriptionFinancePort` remains the tenant/subscription invoice port and is not used for SETTLEMENT-kind principal invoices.

All collected-cash reads under partner billing/settlement continue through `CollectedCashReadPort`; direct `finance_%` SQL in those packages remains forbidden by architecture tests.

---

## 7. W7 — deterministic backfill, real acceptance implementation, and cutover config

### D-50 — deterministic `authorization_version` backfill

The plan MUST NOT depend on PostgreSQL UPDATE row execution order to assign sequence values.

Canonical deterministic algorithm:

1. lock the relevant `users` set for the migration;
2. compute `base_version = max(existing authorization_version)`;
3. materialize a ranked mapping for rows still at zero:

```sql
WITH ranked AS MATERIALIZED (
  SELECT id,
         :base_version + ROW_NUMBER() OVER (ORDER BY created_at, id) AS assigned_version
  FROM users
  WHERE authorization_version = 0
)
UPDATE users u
SET authorization_version = r.assigned_version
FROM ranked r
WHERE u.id = r.id;
```

4. create/use `uac_authorization_version_seq` and position it to the maximum assigned/current version with correct `setval(..., is_called)` semantics;
5. every future invalidation bump uses `nextval('uac_authorization_version_seq')`;
6. rerunning the migration changes no non-zero version;
7. tests prove ordering, uniqueness/monotonicity, rerun idempotency, and sequence-next-value greater than every assigned version.

The final SQL may use an equivalent deterministic CTE/temp-table form, but MUST assign the precomputed ranked value, never `nextval()` in an unordered UPDATE and then call the result deterministic.

### D-51 — acceptance tasks implement their own test classes

W7 Task 4 and Task 5 list new acceptance classes under Files. Their "minimal implementation" step MUST explicitly create the listed parameterized test classes and fixtures. It may say no product-code change is expected, but it MUST NOT say `none` while the class itself is absent.

If an acceptance case turns red after the class exists, fix the owning earlier-wave behavior, rerun the owning regression, then rerun the acceptance class at the same candidate SHA.

### D-52 — committed defaults remain OFF/legacy for the entire program

This rule is absolute:

```text
Every committed application*.yml value remains false/legacy.
No G7 stage commits "default true" or "authoritative" into source control.
Stage activation is a deployment-environment change only.
```

Therefore G7-B through G7-G task `Files` / Step 3 text MUST be rewritten so that:

- code/config plumbing is committed once with false/legacy defaults;
- stage transition sets environment values in deployment configuration outside the repository;
- evidence records the deployment environment values and the source SHA;
- rollback changes environment values only;
- `git grep` proves no committed true/authoritative default.

Examples:

```text
G7-B: SANAD_UAC_MODE=authoritative        (deployment env only)
G7-C: SANAD_PARTNER_ENABLED=true          (deployment env only)
G7-D: SANAD_COMMERCIAL_IDENTITY_ENABLED=true
G7-E: SANAD_TRIAL_CONTINUATION_ENABLED=true
      SANAD_PARTNER_BILLING_ENABLED=true
G7-F: SANAD_SETTLEMENT_ENABLED=true
G7-G: SANAD_NOTIFICATIONS_ENABLED=true
      SANAD_DASHBOARDS_ENABLED=true
```

### D-53 — exact PromQL

Every query labeled EXACT MUST be syntactically executable. Counters passed to `rate()`/`increase()` MUST include explicit range vectors (for example `[5m]`, `[24h]`, or a concrete soak window chosen by the runbook). Bare forms such as `rate(metric])` are forbidden.

### D-54 — required-check discovery remains a release gate, not a planning guess

`RELEASE_REQUIRED_CHECKS_DISCOVERY` remains a mandatory pre-release dependency. Candidate job names are not asserted as repository-required checks until authenticated repository-settings evidence is captured in `snad-evidence/required-checks.json` (or equivalent CI artifact) at release time.

Failure to discover the current protected-branch requirements blocks a RELEASE CLAIM, not docs-only planning or implementation of earlier waves.

---

## 8. Self-review regeneration rules

The existing self-review MUST be regenerated from scratch after the source documents are rewritten. Do not mechanically preserve `COVERED` from Rev C.

At minimum it MUST explicitly verify:

- `SUBSCRIPTION.MANAGE` is present in W2 seed + allowlist + tests + W4 consumption;
- all four membership mutations invalidate session/cache state immediately;
- `platform_files` platform branch requires `app.partner_id IS NULL`;
- registration-only logo flow makes no magic-byte/content-sniffing claim;
- credit-note journal has mathematically correct 2-or-3-line semantics and uses remaining creditable gross rather than AR open balance;
- W5 SETTLEMENT invoices use `FinancePrincipalInvoicePort`, never `SubscriptionFinancePort`;
- evidence/stage tasks have no fabricated RED and no post-gate tracked evidence commit;
- deterministic authorization-version mapping does not depend on UPDATE execution order;
- W7 committed defaults remain false/legacy and cutover is deployment-env-only;
- all EXACT PromQL is syntactically valid;
- the two external dependencies are represented honestly: real byte transport/content verification and release required-check discovery.

The self-review status is `COVERED` only where the corrected source text and a named test/gate actually support it.

---

## 9. Mechanical final scans

Run these scans over the 10 rewritten files before committing. Any unexpected hit is a correction failure.

```bash
# Revision/header drift or stale authoritative older-revision claims
rg -n "Spec:\*\* Revision B|Spec:\*\* Revision C|Rev B is binding|Rev C is binding" docs/superpowers/plans docs/superpowers/specs

# Missing continuation capability in W2 delegation contract
rg -n "SUBSCRIPTION\.MANAGE" docs/superpowers/plans/2026-09-23-wave-2-partner-delegated-administration.md docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md

# Forbidden metadata-only magic-byte claim
rg -n "magic-byte|%PNG|FF D8 FF|svg.*image/png" docs/superpowers/plans/2026-09-23-wave-3-commercial-tax-identity-branding.md docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md

# Contradictory two-line credit-note wording
rg -n "exactly two.*journal|exactly 2.*balanced" docs/superpowers/plans/2026-09-23-wave-4-partner-tenant-billing.md docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md

# Forbidden settlement mirror port
rg -n "SETTLEMENT.*SubscriptionFinancePort|SANAD.*partner.*SubscriptionFinancePort|settlement invoices ride.*SubscriptionFinancePort" docs/superpowers/plans/2026-09-23-wave-5-partner-settlement-sanad-billing.md docs/superpowers/plans/2026-09-23-unified-authorization-partner-control-plane-master-implementation.md

# Fake evidence commits after gates
rg -n "evidence\): gate run|evidence\): release verification|Step 6: exact commit.*evidence" docs/superpowers/plans

# Forbidden unordered sequence assignment claim
rg -n "UPDATE users SET authorization_version = nextval" docs/superpowers/plans/2026-09-23-wave-7-cutover-security-release.md

# Committed cutover true/authoritative defaults in the plan instructions
rg -n "stage default true|default `?true|config default.*authoritative" docs/superpowers/plans/2026-09-23-wave-7-cutover-security-release.md

# Broken PromQL form previously observed
rg -n "rate\([^)]*\]\)|increase\([^)]*\]\)" docs/superpowers/plans/2026-09-23-wave-7-cutover-security-release.md
```

The executor must review each hit in context; the purpose is to catch stale contradictory instructions, not to ban historical prose that is clearly labelled superseded.

## 10. Commit and publication contract

Before commit:

```bash
git status --short
git diff --name-only
```

The diff MUST contain exactly the 10 paths listed in §1 and no product code.

Commit message:

```text
docs(plan): finalize revision d consistency corrections after independent review r3
```

Push ONLY to:

```text
review/unified-control-plane-revision-c-reconstructed
```

Never push this correction directly to protected `main`.

After push, independently verify the remote branch HEAD and changed-file set through GitHub, then run a fresh independent re-review. Do NOT set `WAVE_1_AUTHORIZED=YES` or `PLAN_READY=YES` in the correction commit itself.

## 11. Exit status

The correction executor may report only:

```text
REVISION_D_CORRECTION_COMMIT=<sha>
REVISION_D_CHANGED_FILES=10/10_DOCS_ONLY
REVISION_D_INTERNAL_SCAN=PASS
REVISION_D_REMOTE_PUSH=VERIFIED
INDEPENDENT_RE_REVIEW=REQUIRED
WAVE_1_AUTHORIZED=NO
PLAN_READY=NO
PRODUCT_IMPLEMENTATION=NOT_STARTED
```

Any unresolved contradiction produces `REVISION_D_CORRECTION_REQUIRED`; partial completion must not be called final.
