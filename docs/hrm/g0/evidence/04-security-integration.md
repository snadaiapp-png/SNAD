# HRM-G0 — WS4 Security & Integration Foundation Verification Evidence

Task: WS4 Task 10 — Final WS4 verification gate
Directive: SNAD HRM-G0 MASTER MODULE COMPLETION & FINAL CLOSURE DIRECTIVE (Phase A)
Branch: `feat/hrm-g0-foundation` (PR #914 — OPEN / DRAFT / UNMERGED)
Execution mode: STRICT TDD / PostgreSQL Direct / evidence-first / fail-closed

## 1. Scope verified

WS4 Tasks 5–9 implemented on top of the Task 3/4 foundation:

| Task | Deliverable | Commit |
|------|-------------|--------|
| WS4-5 | `SensitiveReadAuditService` + `HrAuthenticatedContext` (fail-closed sensitive-read audit) | `1fd05954` (RED `eaa70c7a`) |
| WS4-6 | `HrOutboxWorker` + `HrAuditDeliveryWorker` + `HrOutboxEventConsumer` port + `V20260904_1` (claim columns on `hr_audit_delivery`) | `f361c257` (RED `4a22e2e1`) |
| WS4-7 | `IamEmploymentAccessPort` + `UserServiceIamEmploymentAccessAdapter` + `HrmIamAccessPolicy` + `HrmIamEventConsumer` | `ebfefc60` (RED `d29bf608`) |
| WS4-8 | `JdbcHrRequestIdempotencyService` (shared `RequestIdempotencyService` over `hr_idempotency_records`) | `7ad81d6f` (RED `425b1bfb`) |
| WS4-9 | `HrModuleBoundaryArchitectureTest` (ArchUnit + cross-module SQL scan) | `dbe5313a` |

## 2. Complete WS4 suite (PostgreSQL Direct, local run at WS4-9 head `dbe5313a`)

| Test class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| HrScopedAuthorizationIntegrationTest | 6 | 0 | 0 |
| HrHistoricalAuthorizationIntegrationTest | 1 | 0 | 0 |
| HrAuditOutboxAtomicityIntegrationTest | 18 | 0 | 0 |
| HrSensitiveReadAuditIntegrationTest | 8 | 0 | 0 |
| HrOutboxDeliveryIntegrationTest | 12 | 0 | 0 |
| HrIamPolicyConsumerIntegrationTest | 10 | 0 | 0 |
| HrIdempotencyIntegrationTest | 12 | 0 | 0 |
| HrModuleBoundaryArchitectureTest | 6 | 0 | 0 |
| **TOTAL (WS4 suite)** | **73** | **0** | **0** |

Supplementary contract verification at the same head:

| Test class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| RoleTemplateProvisionerContractTest (HR_MANAGER exact matrix) | 1 | 0 | 0 |
| Regression (WS4-5 gate): HrAuditOutboxAtomicityIntegrationTest + HrScopedAuthorizationIntegrationTest + HrRlsFailClosedIntegrationTest | 131 | 0 | 0 |

Full backend CI on the exact canonical HEAD (Maven / CRM Integration / PostgreSQL Acceptance jobs)
is recorded separately with the Master Task 4 closure certificate; this document records the
focused WS4 verification runs.

## 3. Verbatim gate results

- `WS4_SECURITY_INTEGRATION = PASS`
- `CROSS_MODULE_DB_ACCESS = NO` — HR production SQL never references
  `crm_*` / `accounting_*` / `erp_*` / `payroll_*` tables (source scan in
  `HrModuleBoundaryArchitectureTest.hrProductionSqlMustNotTouchOtherModulesTables`).
- `CRM_IMPLEMENTATION_DEPENDENCY = NO` — ArchUnit: no `..hr..` class depends on
  `..crm.idempotency..`, `..crm.integration..`, `..accounting.infrastructure..`,
  `..erp.infrastructure..`, `..payroll.infrastructure..` (production classes only).
- `HR_MANAGER_CAPABILITY_MATRIX = UNCHANGED` — `RoleTemplateProvisionerContractTest`
  asserts `HR_MANAGER` retains exactly:
  - `HR.EMPLOYEE.READ`
  - `HR.EMPLOYEE.WRITE`
  - `HR.EMPLOYEE.ARCHIVE`
  - No `HRM.*` capability is bound to `HR_MANAGER` anywhere in WS4 (grep over
    role-template migrations: none).
- `SENSITIVE_READ_AUDIT = PASS` — restricted reads append an identifier-only
  audit row BEFORE restricted data may be returned; audit-append failure throws
  (`HRM_SENSITIVE_READ_AUDIT_APPEND_FAILED`) so the restricted response is never
  returned; no-transaction calls fail closed
  (`HRM_SENSITIVE_READ_AUDIT_NOT_TRANSACTIONAL`); read audits keep
  `before_state`/`after_state` NULL.
- `AUDIT_ATOMICITY = PASS` (re-affirmed) — mutation audit + delivery + outbox
  append in the SAME transaction; any evidence-append failure rolls back the
  canonical mutation; `REQUIRES_NEW` is absent from all critical audit paths.
- `OUTBOX_DELIVERY = PASS` — at-least-once workers with short tenant-scoped
  claim transactions (materialized CTE + `FOR UPDATE SKIP LOCKED` + exclusive
  claim tokens), no DB transaction held during dispatch, exponential backoff on
  `available_at`, `DEAD_LETTER` on exhaustion, stale-claim recovery with token
  rotation, completed deliveries never repeated.
- `IAM_POLICY = PASS` — employment status never equals user-account status;
  only ACTIVE `hr_iam_access_bindings` with `access_mode='HR_MANAGED'` permit
  HR lifecycle to affect IAM; unmanaged users are never disabled by
  termination; another active HR-managed employment preserves access; missing
  binding fails closed; cross-tenant events cannot affect another tenant
  (FORCE RLS); duplicate at-least-once delivery has no duplicate side effect
  (`hr_idempotency_records` consumption claim); HR production code never writes
  IAM/user tables directly (source scan).
- `IDEMPOTENCY = PASS` — durable service over `hr_idempotency_records`: replay
  on same key + same fingerprint + completed; `HRM_IDEMPOTENCY_CONFLICT` on
  fingerprint mismatch; deterministic retry-later while in-flight; expired
  completed records CAS-reclaimed; concurrent `begin()` — exactly one logical
  operation wins; unique violations never silently swallowed.
- `RLS = PASS` (re-affirmed) — `HrRlsFailClosedIntegrationTest` 107/107 at the
  WS4-5 gate; all WS4 tables remain FORCE RLS with tenant isolation; workers
  use transaction-local `SET LOCAL app.tenant_id` so contextless sessions see
  nothing (fail-closed).

## 4. Redaction / fixture scan

- Database-level guards re-affirmed: `ck_hr_audit_ledger_no_raw_secrets`,
  `ck_hr_domain_event_outbox_no_raw_secrets`, `ck_hr_idempotency_no_raw_secrets`
  reject raw `password/secret/token/api_key/authorization/cookie/jwt/national_id/
  iqama/passport/bank_account/bank_iban/encryption_key/blind_index_key` keys in
  all JSON payloads (`HrAuditOutboxAtomicityIntegrationTest`,
  `HrIdempotencyIntegrationTest`, `HrOutboxDeliveryIntegrationTest`).
- Worker dispatch surface: `HrAuditDeliveryWorker` ships identifiers /
  classification / reason / correlation ONLY to `PlatformAuditSink` — ledger
  state snapshots are never shipped; the sentinel-value test proves no raw
  restricted value reaches the sink or the outbox.
- Migration/fixture scan: no raw National ID / passport / token / secret / key
  / bank details in HR migrations or test SQL fixtures.
- New migration `V20260904_1` is additive-only (3 claim columns + partial
  index); CRM migration-version contract tests extended to `20260904.1`.
  `FLYWAY_TERMINAL = 20260904.1`.

## 5. Known boundary decisions (documented root causes)

1. `V20260904_1` claim columns on `hr_audit_delivery`: Task 3 persisted
   delivery state without claim tokens; the WS4-6 workers require exclusive,
   recoverable claims so that two racing workers leave exactly one valid
   claimant. Mirrors the `hr_domain_event_outbox` claim design. Forward-only,
   additive.
2. Outbox/audit workers claim per-tenant with transaction-local
   `app.tenant_id` instead of any RLS bypass: the tables are FORCE RLS, the
   runtime role is `NOSUPERUSER NOBYPASSRLS`, and a contextless session must
   see nothing. Tenant sweep reads the authoritative `tenants` registry (no
   tenant RLS on the registry itself); every HR-table transaction is still
   tenant-scoped.
3. `IN (SELECT ... LIMIT 1 FOR UPDATE SKIP LOCKED)` was replaced with the
   materialized-CTE claim pattern (proven by the CRM integration worker):
   PostgreSQL may flatten the `IN`-subquery form and claim multiple rows,
   breaking claim exclusivity. Verified by `twoWorkersRaceWithoutDoubleDelivery`.

## 6. Security review checklist (WS4 scope)

| Check | Result |
|-------|--------|
| MUTATION_WITHOUT_REQUIRED_AUDIT | NO |
| MUTATION_WITHOUT_REQUIRED_OUTBOX | NO |
| AUDIT_CAN_COMMIT_AFTER_BUSINESS_ROLLBACK | NO |
| RAW_PII_IN_AUDIT | NO |
| RAW_PII_IN_OUTBOX | NO |
| RAW_SECRETS_IN_AUDIT | NO |
| RAW_SECRETS_IN_OUTBOX | NO |
| REQUIRES_NEW_CRITICAL_AUDIT | NO |
| CRM_IMPLEMENTATION_DEPENDENCY | NO |
| CROSS_MODULE_DB_ACCESS | NO |
| TASK_5_CODE_PRESENT (premature) | N/A — Task 5 is now implemented and verified in scope |

## 7. Result

`WS4_SECURITY_INTEGRATION = PASS`
