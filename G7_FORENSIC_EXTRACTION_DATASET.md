# G7 FORENSIC EXTRACTION DATASET

> **Generated:** 2026-08-12
> **Mode:** EVIDENCE EXTRACTION ONLY — No modifications made
> **Repository:** SNAD (https://github.com/snadaiapp-png/SNAD.git)
> **Branch:** main
> **HEAD:** e13b6a4ca55fe1c1c46040af0506b38b0c00871a

---

## SECTION 1 — REPOSITORY SNAPSHOT

| Field | Value |
|-------|-------|
| Repository | SNAD |
| Branch | main |
| HEAD | e13b6a4ca55fe1c1c46040af0506b38b0c00871a |
| Commit SHA | e13b6a4c |
| Working Tree | C:/Users/SNADA/ZCodeProject/SNAD |
| Modified Files | apps/web/lib/execution/contract-tests.test.ts, apps/web/lib/execution/platform-contract-tests.test.ts |
| Untracked Files (G7) | 34 G7-related documentation files |

### Relevant Modules

| Module | Path | Type |
|--------|------|------|
| Backend | apps/sanad-platform/src/main/java/com/sanad/platform/ | Spring Boot (Java) |
| Frontend/BFF | apps/web/ | Next.js (TypeScript) |
| Database | PostgreSQL with Flyway migrations | V20260716_1 through V20260717_4 |
| Postgres Proxy | apps/postgres-proxy/ | Proxy layer |

---

## SECTION 2 — G7 IDENTITY

### G7_IDENTITY_EVIDENCE

| # | Context | G7 Name | Source | Status | Authority |
|---|---------|---------|--------|--------|-----------|
| 1 | CRM Execution Board (Product) | Mobile Offline Foundation | apps/web/app/crm/crm-execution-data.ts (lines 129-137) | **NOT_STARTED** | Product Execution Board |
| 2 | CRM Enterprise Execution Roadmap | CI/CD Hardening | docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md (lines 510-584) | **DONE** | Roadmap (highest governance) |
| 3 | Quality Gates (SANAD Framework) | Production Smoke Test | QUALITY-GATES.md (lines 125-140) | **PASS** | Execution Framework |
| 4 | CRM Readiness Gate | Product and Backlog | docs/crm/CRM-READINESS-GATE.md (lines 102-113) | **READY FOR REVIEW** | Pre-Implementation Gate |
| 5 | User/Operator Declaration | Central Workflow Engine | G7_WORKFLOW_ENGINE_MASTER_BASELINE.md (line 17) | **BLOCKED** | User authority (Executor #7) |
| 6 | ERP Execution Board | Integration & API | apps/web/app/erp/erp-execution-data.ts (line 129) | **NOT_STARTED** | ERP Product Board |
| 7 | Finance Execution Board | Financial Reporting & Close | apps/web/app/finance/finance-execution-data.ts (line 129) | **NOT_STARTED** | Finance Product Board |

### Naming Conflict Resolution

| Candidate | Description | Resolution |
|-----------|-------------|------------|
| G7-a | CI/CD Pipeline Hardening | Reassigned — DONE, governance concern |
| G7-b | Quality Gates & Automated Testing | Reassigned — PASS, framework concern |
| G7-c | Readiness Gate & Deployment Automation | Reassigned — READY FOR REVIEW |
| G7-d | Central Workflow Engine | Reassigned — BLOCKED, external engine not deployed |
| G7-e | ERP Integration & API | Separate module (ERP), not CRM |
| G7-f | Finance Reporting & Close | Separate module (Finance), not CRM |

**Decision:** All conflicting definitions resolved in favor of **Mobile Offline Foundation** as the canonical CRM G7.

### Conflict Register

| Conflict ID | Source 1 | Source 2 | Type | Severity | Resolution |
|-------------|----------|----------|------|----------|------------|
| G7-CF-001 | CRM Roadmap G7 (CI/CD) | Execution Board G7 (Mobile Offline) | Scope Divergence | HIGH | Different contexts — governance vs product |
| G7-CF-002 | CRM Roadmap G7 (DONE) | Execution Board G7 (NOT_STARTED) | Status Divergence | MEDIUM | Expected — different scopes |
| G7-CF-003 | Execution Board G7 (no tasks) | Execution Board G0-G6 (all with tasks) | Completeness Gap | HIGH | G7 has definition but ZERO task breakdown |
| G7-CF-004 | G7_WORKFLOW_ENGINE (user authority) | G7_MOBILE_FOUNDATION (code authority) | Identity Conflict | CRITICAL | Code authority wins per EXECUTION-MODEL-MAPPING.md |

### Governance Mapping

From `docs/governance/EXECUTION-MODEL-MAPPING.md` (lines 92-94):
> "Roadmap G7-G8 (CI/CD, Quality) are GOVERNANCE concerns that do NOT map to Execution Board G7-G10 (Mobile, Caller ID, AI, QA). Execution Board G7-G10 are FUTURE product phases."

---

## SECTION 3 — G7 SCOPE

### IN_SCOPE

| Item | Source | Evidence |
|------|--------|----------|
| Mobile-optimized CRM entity APIs | crm-execution-data.ts | REST endpoints returning reduced-payload subsets |
| Offline sync schema | Mobile offline gap analysis | Database schema for change tracking, cursors, device registration |
| Client-side offline storage architecture | Mobile offline gap analysis | IndexedDB/SQLite local storage design |
| Sync engine architecture | Mobile offline gap analysis | Delta pull, outbox-based push, conflict detection |
| Mobile-specific auth flow | Security requirements | Short-lived tokens, refresh flow, device binding |
| Offline entity subset | crm-execution-data.ts | Defined subset of CRM entities for offline caching |

### OUT_OF_SCOPE

| Item | Reason | Source |
|------|--------|--------|
| Native mobile app UI | Handled by mobile app team | G7_IDENTITY_FINAL.md |
| Push notifications | Deferred to G8 | G7_IDENTITY_FINAL.md |
| Caller identification | Deferred to G8 | G7_IDENTITY_FINAL.md |
| Real-time collaboration | Out of scope for mobile offline | G7_IDENTITY_FINAL.md |
| Offline-first full database replication | Not required; only entity subset | G7_IDENTITY_FINAL.md |
| Background sync on iOS/Android | Platform-specific; not backend scope | G7_IDENTITY_FINAL.md |

### DEPENDENCIES

| Dependency | Status | Evidence |
|------------|--------|----------|
| G1 — Database & Multi-Tenant Foundation | **COMPLETE** | G7_MASTER_TRUTH_REPORT.md |
| G3 — Core CRM Entities | **COMPLETE** | G7_MASTER_TRUTH_REPORT.md |
| Auth System (JWT) | **COMPLETE** | ServiceJwtProvider.java |
| Tenant Context | **COMPLETE** | TenantRlsDataSource |
| RBAC | **COMPLETE** | @RequireCapability |

---

## SECTION 4 — REQUIREMENTS

### REQUIREMENTS_TOTAL: 39

### Priority Distribution

| Priority | Count | IDs |
|----------|-------|-----|
| P0 BLOCKER | 12 | FR-001, FR-002, FR-003, FR-004, SYNC-001, SYNC-002, SYNC-004, DATA-001, DATA-002, SEC-001, SEC-005, TEST-005 |
| P1 CRITICAL | 13 | FR-005, FR-006, FR-007, FR-008, SYNC-003, SYNC-005, SYNC-006, SYNC-008, NFR-001, SEC-002, SEC-004, DATA-004, DATA-005, TEST-001, TEST-002, TEST-003, TEST-004 |
| P2 HIGH | 9 | FR-009, FR-010, NFR-002, NFR-004, SEC-003, SYNC-007, DATA-003, TEST-006 |
| P3 MEDIUM | 2 | NFR-003, NFR-005 |

### Category Summary

| Category | Count |
|----------|-------|
| Functional (FR) | 10 |
| Non-Functional (NFR) | 5 |
| Security (SEC) | 5 |
| Sync (SYNC) | 8 |
| Data (DATA) | 5 |
| Test (TEST) | 6 |

### Requirements Detail

| ID | Title | Priority | Category | Status | Dependency |
|----|-------|----------|----------|--------|------------|
| FR-001 | Mobile-optimized Entity List API | P0 | Functional | MISSING | G1, G3 |
| FR-002 | Mobile-optimized Entity Detail API | P0 | Functional | MISSING | G1, G3 |
| FR-003 | Delta/Incremental Sync Pull API | P0 | Functional | MISSING | G1, G3, DATA-002, SYNC-004 |
| FR-004 | Sync Push API (Batch Writes) | P0 | Functional | MISSING | G1, G3, SYNC-005, SYNC-006 |
| FR-005 | Sync Status/Cursor API | P1 | Functional | MISSING | G1, DATA-001 |
| FR-006 | Mobile Auth Token Refresh | P1 | Functional | MISSING | G1, SEC-003 |
| FR-007 | Offline Entity Subset Definition | P1 | Functional | MISSING | G3 |
| FR-008 | Conflict Resolution Policy | P1 | Functional | MISSING | G1, G3, SYNC-005 |
| FR-009 | Bulk Sync Endpoint | P2 | Functional | MISSING | FR-001, FR-003, FR-004 |
| FR-010 | Mobile Entity Schema (Reduced Payload) | P2 | Functional | MISSING | G3, FR-007 |
| SYNC-001 | Bidirectional Sync Support | P0 | Sync | MISSING | FR-003, FR-004, SYNC-002, SYNC-004 |
| SYNC-002 | Delta/Incremental Pull | P0 | Sync | MISSING | FR-003, DATA-002, SYNC-004 |
| SYNC-003 | Outbox-Based Push | P1 | Sync | MISSING | FR-004, DATA-001 |
| SYNC-004 | Sync Cursor/Version Tracking | P0 | Sync | MISSING | G1, DATA-001 |
| SYNC-005 | Conflict Detection | P1 | Sync | MISSING | FR-004, SYNC-004, DATA-002 |
| SYNC-006 | Conflict Resolution | P1 | Sync | MISSING | SYNC-005, FR-008 |
| SYNC-007 | Retry with Exponential Backoff | P2 | Sync | MISSING | SYNC-001 |
| SYNC-008 | Idempotent Sync Operations | P1 | Sync | MISSING | FR-004 |
| DATA-001 | Sync Metadata Tables | P0 | Data | MISSING | G1 |
| DATA-002 | Change Tracking Columns | P0 | Data | MISSING | G1, G3 |
| DATA-003 | Mobile Device Registry Table | P2 | Data | MISSING | G1, SEC-003 |
| DATA-004 | Sync Log Table | P1 | Data | MISSING | G1, DATA-001 |
| DATA-005 | Conflict Log Table | P1 | Data | MISSING | G1, SYNC-005, SYNC-006 |
| SEC-001 | Offline Data Encryption at Rest | P0 | Security | MISSING | None |
| SEC-002 | Mobile Auth Token Expiry | P1 | Security | MISSING | FR-006 |
| SEC-003 | Device Registration/Binding | P2 | Security | MISSING | G1, FR-006 |
| SEC-004 | Offline Authorization Enforcement | P1 | Security | MISSING | G1, FR-007 |
| SEC-005 | Tenant Isolation on Sync Operations | P0 | Security | MISSING | G1 |
| NFR-001 | Mobile API Response Time < 200ms | P1 | Non-Functional | MISSING | G1, FR-001, FR-002, FR-003 |
| NFR-002 | Offline Data Retention Policy | P2 | Non-Functional | MISSING | FR-007 |
| NFR-003 | Sync Payload Compression | P3 | Non-Functional | MISSING | FR-003, FR-004 |
| NFR-004 | Offline Storage Size Limit | P2 | Non-Functional | MISSING | FR-007, NFR-002 |
| NFR-005 | Sync Frequency Guidance | P3 | Non-Functional | MISSING | FR-005 |
| TEST-001 | Mobile API Contract Tests | P1 | Test | MISSING | FR-001, FR-002, FR-005, FR-006, FR-009, FR-010 |
| TEST-002 | Sync Integration Tests | P1 | Test | MISSING | SYNC-001 through SYNC-008 |
| TEST-003 | Offline Read/Write Tests | P1 | Test | MISSING | FR-007, NFR-002, NFR-004, SEC-001, SEC-004 |
| TEST-004 | Conflict Resolution Tests | P1 | Test | MISSING | SYNC-005, SYNC-006, FR-008 |
| TEST-005 | Tenant Isolation Sync Tests | P0 | Test | MISSING | SEC-005, G1 |
| TEST-006 | E2E Offline-to-Online Test | P2 | Test | MISSING | All SYNC requirements |

---

## SECTION 5 — EXISTING IMPLEMENTATION

### EXISTING_COMPONENTS: 9

| Component | File | Class | Method | Status | Evidence |
|-----------|------|-------|--------|--------|----------|
| Mobile Self-Registration | security/service/MobileSelfRegistrationService.java | MobileSelfRegistrationService | register() | **IMPLEMENTED** | Rate limiting (3/IP/hour), mobile number normalization, tenant provisioning |
| ETag + If-Match Concurrency | crm/concurrency/ETagService.java | ETagService | etag(), validateIfMatch() | **IMPLEMENTED** | SHA-256 ETags, FOR UPDATE row locking, 428/412 responses |
| Idempotency Service | crm/idempotency/IdempotencyService.java | JdbcIdempotencyService | begin(), complete(), fail() | **IMPLEMENTED** | JDBC-backed, 24h retention, fingerprint validation |
| Retry + Exponential Backoff | scale/config/TimeoutAndRetryPolicyConfig.java | TimeoutAndRetryPolicyConfig | retryRegistry() | **IMPLEMENTED** | Resilience4j: maxAttempts=3, backoff 1s/2s/4s |
| Circuit Breaker | scale/config/CircuitBreakerPolicyConfig.java | CircuitBreakerPolicyConfig | circuitBreakerRegistry() | **IMPLEMENTED** | 5 named breakers (database, redis, aiInference, emailProvider, webhookDelivery) |
| Cursor Pagination | crm/pagination/CursorCodec.java | CursorCodec | encode(), decode() | **IMPLEMENTED** | Base64-URL cursors, tenant hash validation, cross-tenant rejection |
| Transactional Outbox | crm/integration/application/CrmIntegrationOutboxWorker.java | CrmIntegrationOutboxWorker | processOutboxEvents() | **IMPLEMENTED** | Atomic CTE claims, transaction boundary separation, claim expiry recovery |
| Session Versioning | security/filter/SessionVersionCache.java | SessionVersionCache | get(), invalidate() | **IMPLEMENTED** | JWT session_version claim, Caffeine 5s TTL cache, monotonically increasing version |
| Error Catalog | crm/error/CrmErrorCode.java | CrmErrorCode | N/A | **IMPLEMENTED** | CRM_CONCURRENCY_CONFLICT, CRM_IDEMPOTENCY_CONFLICT, retryable flags |

### PARTIAL_COMPONENTS: 2

| Component | File | Status | Notes |
|-----------|------|--------|-------|
| Notification System | security/notification/SecurityNotificationService.java | **PARTIAL** | Security notifications implemented; Team Management notifications NoOp only |
| Optimistic Locking | CRM entity tables | **PARTIAL** | version BIGINT exists but not mobile-optimized |

### MISSING_COMPONENTS: 12

| Component | Status | Evidence |
|-----------|--------|----------|
| Offline Mode | **MISSING** | No offline-first architecture, service worker, or offline cache |
| Local Storage | **MISSING** | No client-side local storage abstraction |
| SQLite / IndexedDB | **MISSING** | Not used; PostgreSQL only |
| Mutation Queue | **MISSING** | No dedicated mutation queue for offline operations |
| Sync Queue | **MISSING** | No sync queue abstraction |
| Sync Engine | **MISSING** | No client-server sync engine |
| Pull / Push (sync primitives) | **MISSING** | Not found as synchronization primitives |
| Delta Sync | **MISSING** | No delta/incremental sync mechanism |
| Resync | **MISSING** | No resync/re-synchronization logic |
| Background Sync | **MISSING** | No BackgroundSync abstraction |
| Device Registration | **MISSING** | No device registration, device token, or device management |
| Merge (conflict resolution) | **MISSING** | No three-way merge or conflict resolution algorithm |

### BROKEN_COMPONENTS: 0

---

## SECTION 6 — DATABASE FORENSICS

### DATABASE OVERVIEW

| Metric | Value |
|--------|-------|
| Migration Source | apps/sanad-platform/src/main/resources/db/migration/ (48 shared SQL files) |
| Vendor PostgreSQL | apps/sanad-platform/src/main/resources/db/vendor/postgresql/ (23 PostgreSQL-only SQL files) |
| Total Tables | ~97 unique tables |
| Tenant Isolation | Every CRM table has tenant_id + FK + PostgreSQL RLS |
| Version Columns | BIGINT default 0 on 33+ CRM tables |
| Audit Columns | created_by, updated_by, created_at, updated_at on nearly all CRM tables |

### EXISTING_TABLES: ~97

#### Platform Foundation (8 tables)

| Table | Migration | Key Columns | Tenant Isolation | Version |
|-------|-----------|-------------|------------------|---------|
| tenants | V1 | id, name, subdomain, status | Root table | N/A |
| organizations | V2 | id, tenant_id, name | ✅ FK | N/A |
| users | V4, V10, V11, V13, V16, V20260629_2 | id, tenant_id, email, session_version, mobile_number | ✅ FK | session_version |
| organization_memberships | V3, V5 | id, tenant_id, organization_id, user_id | ✅ FK | N/A |
| roles | V6 | id, tenant_id, code, name | ✅ FK | N/A |
| access_capabilities | V7 | id, code, name | Global catalog | N/A |
| role_capabilities | V8 | id, tenant_id, role_id, capability_id | ✅ FK | N/A |
| user_role_assignments | V9 | id, tenant_id, user_id, role_id | ✅ FK | N/A |

#### Auth & Security (3 tables)

| Table | Migration | Key Columns | Status |
|-------|-----------|-------------|--------|
| refresh_tokens | V10 | id, tenant_id, user_id, token_hash, expires_at | ACTIVE/USED/REVOKED |
| password_reset_tokens | V12 | id, tenant_id, user_id, token_hash, expires_at | ACTIVE/USED/REVOKED/EXPIRED |
| platform_audit_logs | V17 | id, actor_tenant_id, action, before_state, after_state | Audit trail |

#### SaaS Administration (6 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| saas_plans | V19 | Subscription plans |
| saas_plan_entitlements | V19 | Feature entitlements |
| tenant_subscriptions | V19 | Tenant subscriptions |
| billing_invoices | V19 | Invoices |
| subscription_change_events | V19, V20260711_1 | Change history |
| tenant_quota | V20260706_1 | Quota tracking |

#### CRM Core (13 tables)

| Table | Migration | Key Columns | Version | Audit |
|-------|-----------|-------------|---------|-------|
| crm_accounts | V20260702_1, V20260716_4, V20260722_7 | id, tenant_id, display_name, account_type, lifecycle_status | ✅ version BIGINT | ✅ |
| crm_contacts | V20260702_1, V20260717_1, V20260722_7 | id, tenant_id, given_name, family_name, primary_email | ✅ version BIGINT | ✅ |
| crm_pipelines | V20260702_1, V20260713_2 | id, tenant_id, name, currency_code | ✅ version BIGINT | ✅ |
| crm_pipeline_stages | V20260702_1 | id, tenant_id, pipeline_id, name, sequence | N/A | ✅ |
| crm_leads | V20260702_1, V20260722_7 | id, tenant_id, display_name, status, score | ✅ version BIGINT | ✅ |
| crm_opportunities | V20260702_1, V20260722_7 | id, tenant_id, account_id, pipeline_id, amount, status | ✅ version BIGINT | ✅ |
| crm_activities | V20260702_1, V20260722_7, V20260807_4 | id, tenant_id, activity_type, subject, status | ✅ version BIGINT | ✅ |
| crm_timeline_events | V20260702_1 | id, tenant_id, subject_type, subject_id, event_type | N/A | ✅ |
| crm_import_jobs | V20260702_1 | id, tenant_id, entity_type, status, total_rows | N/A | ✅ |
| crm_import_files | V20260702_3 | id, tenant_id, import_job_id, content_base64 | N/A | ✅ |
| crm_import_errors | V20260702_3 | id, tenant_id, import_job_id, row_number, error_code | N/A | ✅ |
| crm_custom_field_definitions | V20260702_1, V20260804_1 | id, tenant_id, entity_type, field_key, data_type | ✅ version BIGINT | ✅ |
| crm_custom_field_values | V20260702_3 | id, tenant_id, definition_id, entity_type, entity_id | N/A | ✅ |

#### CRM Tasks, Notes, Tags (4 tables)

| Table | Migration | Key Columns | Version |
|-------|-----------|-------------|---------|
| crm_tasks | V20260716_1 | id, tenant_id, title, status, priority, due_at | ✅ version BIGINT |
| crm_notes | V20260716_2 | id, tenant_id, subject_type, subject_id, body | ✅ version BIGINT |
| crm_tags | V20260716_3 | id, tenant_id, name, color | ✅ version BIGINT |
| crm_tag_assignments | V20260716_3 | id, tenant_id, tag_id, subject_type, subject_id | N/A |

#### CRM Enterprise Account / Customer Master (5 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_account_addresses | V20260716_4 | Address management |
| crm_account_identifiers | V20260716_4 | Business identifiers |
| crm_account_relationships | V20260716_4 | Account-to-account relationships |
| crm_account_status_history | V20260716_4 | Status change audit |
| crm_account_merge_history | V20260716_4 | Merge audit trail |

#### CRM Contact Relationship Model (4 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_contact_relationship_roles | V20260717_1 | Role definitions |
| crm_contact_account_relationships | V20260717_1 | Contact-to-account links |
| crm_contact_relationship_history | V20260717_1 | Relationship audit |
| crm_contact_ownership_history | V20260717_1 | Ownership changes |

#### CRM Communication & Address Model (5 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_party_addresses | V20260717_100 | Unified address management |
| crm_party_address_history | V20260717_100 | Address audit |
| crm_communication_policies | V20260717_100 | Per-tenant communication rules |
| crm_communication_methods | V20260717_100 | Email, phone, etc. |
| crm_communication_method_history | V20260717_100 | Communication audit |

#### CRM G1 Extensions (6 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_assignments | V20260717_6 | Entity assignments |
| crm_transfers | V20260717_6 | Ownership transfers |
| crm_audit_logs | V20260717_6 | CRM entity audit |
| crm_reports | V20260717_6 | Report definitions |
| crm_phone_numbers | V20260717_6 | Phone number management |
| crm_contact_lookup_index | V20260717_6 | Contact search index |

#### CRM Sales Teams, Queues, Territories (13 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_sales_teams | V20260722_1 | Team definitions |
| crm_team_memberships | V20260722_1 | Team membership |
| crm_queues | V20260722_2 | Work queues |
| crm_queue_memberships | V20260722_2 | Queue membership |
| crm_territories | V20260722_3 | Territory hierarchy |
| crm_territory_closure | V20260722_3 | Materialized path closure |
| crm_territory_assignments | V20260722_3 | Territory assignments |
| crm_assignment_rules | V20260722_4 | Auto-assignment rules |
| crm_assignment_rule_versions | V20260722_4 | Rule versioning |
| crm_ownership_history | V20260722_5 | Ownership audit |
| crm_transfer_requests | V20260722_6 | Transfer workflow |
| crm_transfer_steps | V20260722_6 | Approval steps |
| crm_assignment_rule_counters | V20260722_9 | Round-robin counters |

#### CRM Integration & Workflow (6 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_integration_requests | V20260723_1 | Integration request tracking |
| crm_integration_outbox | V20260723_1 | Transactional outbox |
| crm_integration_decisions | V20260723_1 | Decision tracking |
| crm_integration_command_executions | V20260724_1 | Command execution ledger |
| crm_integration_command_artifacts | V20260724_2 | Command artifacts |
| service_callback_replay | V20260724_2 | Replay protection |

#### CRM Customer Intelligence (6 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_customer_scores | V20260729_1 | Customer scoring |
| crm_customer_score_history | V20260729_1 | Score change audit |
| crm_customer_segments | V20260729_1 | Segment definitions |
| crm_segment_memberships | V20260729_1 | Segment membership |
| crm_next_best_actions | V20260729_1 | AI recommendations |
| crm_scoring_models | V20260729_1 | Scoring model versions |

#### CRM Staff & Ownership (7 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_shift_templates | V20260804_2 | Shift definitions |
| crm_shift_assignments | V20260804_3 | Shift scheduling |
| crm_staff_availability | V20260804_4 | Availability tracking |
| crm_staff_skills | V20260804_5 | Skill management |
| crm_capacity_plans | V20260804_6 | Capacity planning |
| crm_workload_assignments | V20260804_7 | Workload tracking |
| crm_service_assignments | V20260804_8 | Service assignments |

#### CRM Cases & Email (2 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| crm_cases | V20260804_9 | Case management |
| crm_email_logs | V20260805_1 | Email tracking |

#### Business Process (7 tables)

| Table | Migration | Purpose |
|-------|-----------|---------|
| bp_process_runs | V20260717_4 | Process execution |
| bp_process_steps | V20260717_4 | Step tracking |
| bp_inventory_balances | V20260717_4 | Inventory |
| bp_inventory_movements | V20260717_4 | Inventory movements |
| bp_ledger_entries | V20260717_4 | Financial ledger |
| bp_payment_events | V20260717_4 | Payment tracking |
| bp_workflow_approvals | V20260717_4 | Approval workflow |
| bp_analytics_snapshots | V20260717_4 | Analytics |

#### System Services (1 table)

| Table | Migration | Purpose |
|-------|-----------|---------|
| system_services | V18 | Service registry |

### MISSING_G7_TABLES: 4

| Table | Purpose | Columns Required | Status |
|-------|---------|------------------|--------|
| mobile_device_registry | Device tracking | device_id, user_id, tenant_id, platform, registered_at, last_seen_at, is_revoked | **MISSING** |
| mobile_sync_cursor | Sync state tracking | tenant_id, device_id, cursor, last_sync_at | **MISSING** |
| mobile_sync_log | Sync audit trail | sync_id, device_id, tenant_id, sync_type, started_at, completed_at, status | **MISSING** |
| mobile_conflict_log | Conflict tracking | conflict_id, sync_id, entity_type, entity_id, tenant_id, device_id, server_version, client_version | **MISSING** |

### EXTENDED_TABLES: 0

### PROPOSED_ONLY_TABLES: 0

### RLS POLICY

**File:** V20260730_1__enable_crm_row_level_security.sql, V20260802_1__re_enable_crm_row_level_security.sql

**Pattern:** Permissive-when-NULL, strict-when-set:
```sql
current_setting('app.tenant_id', true) IS NULL OR tenant_id::text = current_setting('app.tenant_id', true)
```

Applied to ALL `crm_*` tables as defense-in-depth on top of application-layer tenant predicate enforcement.

---

## SECTION 7 — CRM ENTITY INVENTORY

| Entity | Table | Repository | Service | Controller | CRUD | Version | ETag | Idempotency | Tenant Isolation |
|--------|-------|------------|---------|------------|------|---------|------|-------------|------------------|
| Account | crm_accounts | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Contact | crm_contacts | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Lead | crm_leads | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Opportunity | crm_opportunities | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Task | crm_tasks | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Activity | crm_activities | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Note | crm_notes | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Pipeline | crm_pipelines | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Tags | crm_tags | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |
| Custom Fields | crm_custom_fields | ✅ | ✅ | ✅ | ✅ Full | ✅ version | ✅ | ✅ | ✅ RLS |

---

## SECTION 8 — API FORENSICS

### API OVERVIEW

| Metric | Value |
|--------|-------|
| Total Controllers | 56 |
| Total Endpoints (estimated) | ~250+ |
| API Versions | V1 (Map-based, snake_case) and V2 (Typed DTOs, camelCase with envelopes) |
| ETag/If-Match | All V2 CRM mutations and CRM-008 ownership mutations |
| Idempotency | All V2 CRM POST creation endpoints and CRM-008 bulk/assignment POSTs |
| Cursor Pagination | All V2 list endpoints |
| Authorization | Capability-based RBAC via @RequireCapability |

### EXISTING_APIS (Mobile-Relevant): 10

| Method | Path | Controller | Auth | ETag | If-Match | Idempotency | Cursor |
|--------|------|------------|------|------|----------|-------------|--------|
| POST | /api/v2/crm/accounts | CrmContractController | JWT + RBAC | ✅ | No | ✅ | No |
| GET | /api/v2/crm/accounts | CrmContractController | JWT + RBAC | No | No | No | ✅ |
| GET | /api/v2/crm/accounts/{id} | CrmContractController | JWT + RBAC | ✅ | No | No | No |
| PATCH | /api/v2/crm/accounts/{id} | CrmContractController | JWT + RBAC | ✅ | ✅ | No | No |
| POST | /api/v2/crm/contacts | CrmContractController | JWT + RBAC | ✅ | No | ✅ | No |
| GET | /api/v2/crm/contacts | CrmContractController | JWT + RBAC | No | No | No | ✅ |
| POST | /api/v2/crm/leads | CrmContractController | JWT + RBAC | ✅ | No | ✅ | No |
| GET | /api/v2/crm/leads | CrmContractController | JWT + RBAC | No | No | No | ✅ |
| POST | /api/v2/crm/opportunities | CrmContractController | JWT + RBAC | ✅ | No | ✅ | No |
| GET | /api/v2/crm/opportunities | CrmContractController | JWT + RBAC | No | No | No | ✅ |

### MISSING_APIS: 9

| Method | Path | Purpose | Priority | Status |
|--------|------|---------|----------|--------|
| GET | /api/v2/mobile/sync/pull | Delta sync pull | P0 | **MISSING** |
| POST | /api/v2/mobile/sync/push | Batch sync push | P0 | **MISSING** |
| GET | /api/v2/mobile/sync/status | Sync status/cursor | P1 | **MISSING** |
| POST | /api/v2/mobile/device/register | Device registration | P2 | **MISSING** |
| GET | /api/v2/mobile/entity/{type}/{id} | Entity detail (reduced) | P1 | **MISSING** |
| GET | /api/v2/mobile/entity/{type} | Entity list (reduced) | P1 | **MISSING** |
| GET | /api/v2/mobile/conflicts | List conflicts | P1 | **MISSING** |
| POST | /api/v2/mobile/conflicts/{id}/resolve | Resolve conflict | P1 | **MISSING** |
| POST | /api/v2/mobile/conflicts/{id}/skip | Skip conflict | P1 | **MISSING** |

### EXTENSION_APIS: 0

### KEYWORD SEARCH RESULTS

| Keyword | Found | Context |
|---------|-------|---------|
| sync | ✅ | Email sync status logic, not dedicated endpoints |
| pull | ❌ | No pull endpoints |
| push | ❌ | No push endpoints |
| batch | ✅ | bulk-reassign under CRM-008 ownership |
| cursor | ✅ | Extensively used in V2 pagination (CursorCodec) |
| delta | ❌ | No delta/differential endpoints |
| device | ❌ | No device-specific endpoints |
| conflict | ✅ | ETagService.validateIfMatch() → CRM_CONCURRENCY_CONFLICT |
| resync | ❌ | No resync endpoints |
| status | ✅ | Domain status fields, workflow status, import status |

---

## SECTION 9 — VERSION / CONCURRENCY

| Mechanism | File | Class | Method | Entity | Behavior | Evidence |
|-----------|------|-------|--------|--------|----------|----------|
| Optimistic Locking | CRM entity tables | JdbcRepository classes | update() | All CRM | WHERE version = expectedVersion | version BIGINT column on all tables |
| ETag | CRM controllers | CrmControllers | GET responses | All CRM | ETag header from version | Implemented in all GET endpoints |
| If-Match | CRM controllers | CrmControllers | PUT/POST | All CRM | Reject if version mismatch | HTTP 412 on mismatch |
| Concurrency Exception | CRM module | ConcurrencyException | N/A | All CRM | CRM_CONCURRENCY_CONFLICT | Typed exception with error code |

---

## SECTION 10 — IDEMPOTENCY

| Component | File | Status | Evidence |
|-----------|------|--------|----------|
| Idempotency Key | IdempotencyService | **IMPLEMENTED** | Server-side idempotency key tracking |
| Idempotency Table | crm_idempotency_records | **IMPLEMENTED** | V20260716_1 migration |
| Unique Constraints | idempotency_key column | **IMPLEMENTED** | UNIQUE constraint on tenant_id + key |
| Replay Behavior | IdempotencyService | **IMPLEMENTED** | Returns cached result on duplicate |
| Expiration | expires_at column | **IMPLEMENTED** | TTL-based cleanup |

### SERVER_IDEMPOTENCY: IMPLEMENTED
### MOBILE_IDEMPOTENCY: MISSING (not extended to mobile sync)

---

## SECTION 11 — SYNC FORENSICS

### EXISTING_SYNC_BEHAVIOR: NONE

| Component | Status | Evidence |
|-----------|--------|----------|
| Pull | **MISSING** | No sync pull endpoint |
| Push | **MISSING** | No sync push endpoint |
| Delta | **MISSING** | No delta/incremental sync |
| Cursor | **MISSING** | No cursor tracking |
| Checkpoint | **MISSING** | No checkpoint mechanism |
| Batch | **MISSING** | No batch sync |
| Retry | **MISSING** | No retry logic for sync |
| Ordering | **MISSING** | No sync ordering |
| Partial Failure | **MISSING** | No partial failure handling |
| Full Resync | **MISSING** | No full resync mechanism |

### PROPOSED_SYNC_CONTRACT

| Field | Value | Source |
|-------|-------|--------|
| Mutation Identity | entity_type + entity_id + operation + idempotency_key | G7_MASTER_TRUTH_REPORT.md |
| Queue | FIFO per entity type | G7_MASTER_TRUTH_REPORT.md |
| State Machine | LOCAL_CHANGE → QUEUED → READY → SENT → ACKNOWLEDGED → APPLIED | G7_MASTER_TRUTH_REPORT.md |
| Pull | Delta sync with cursor | G7_MASTER_TRUTH_REPORT.md |
| Push | Batch with idempotency | G7_MASTER_TRUTH_REPORT.md |
| Retry | Exponential backoff (1s, 2s, 4s, 8s, 16s) | G7_MASTER_TRUTH_REPORT.md |
| Idempotency | SHA-256 fingerprint, 24h retention | G7_MASTER_TRUTH_REPORT.md |
| Conflict | Version-based detection, 12 conflict classes | G7_MASTER_TRUTH_REPORT.md |
| Full Resync | On cursor invalid, token expiry, explicit request | G7_MASTER_TRUTH_REPORT.md |

---

## SECTION 12 — OFFLINE BEHAVIOR

| State | Source | Trigger | Transition | Recovery | Evidence |
|-------|--------|---------|------------|----------|----------|
| ONLINE | PROPOSED | Connectivity available | → OFFLINE on disconnect | Auto-reconnect | Not implemented |
| OFFLINE | PROPOSED | No connectivity | → ONLINE on reconnect | Queue local changes | Not implemented |
| REAUTH_REQUIRED | PROPOSED | Token expired | → ONLINE after refresh | Refresh token flow | Not implemented |
| STALE | PROPOSED | Data older than threshold | → RESYNC_REQUIRED | Full resync | Not implemented |
| RESYNC_REQUIRED | PROPOSED | Cursor invalid or stale | → ONLINE after resync | Full pull | Not implemented |
| QUEUE | PROPOSED | Local change created | → READY on connectivity | Outbox pattern | Not implemented |
| FAILED | PROPOSED | Sync error after retries | → QUEUE for retry | Manual intervention | Not implemented |
| CONFLICT | PROPOSED | Server version mismatch | → RESOLVED after resolution | Conflict resolution | Not implemented |

---

## SECTION 13 — CONFLICT FORENSICS

### EXISTING_CONFLICT_MECHANISMS: NONE (for mobile sync)

| Entity | Conflict Trigger | Version Check | Error | Response | Merge | Resolution |
|--------|------------------|---------------|-------|----------|-------|------------|
| Account | N/A | N/A | N/A | N/A | N/A | N/A |
| Contact | N/A | N/A | N/A | N/A | N/A | N/A |
| Lead | N/A | N/A | N/A | N/A | N/A | N/A |
| Opportunity | N/A | N/A | N/A | N/A | N/A | N/A |
| Task | N/A | N/A | N/A | N/A | N/A | N/A |
| Activity | N/A | N/A | N/A | N/A | N/A | N/A |
| Note | N/A | N/A | N/A | N/A | N/A | N/A |

### PROPOSED_CONFLICT_CLASSES: 12

| Class | Entity | Policy | Source |
|-------|--------|--------|--------|
| Same-field conflict | Account, Contact, Lead | AUTO_MERGE + USER_RESOLUTION | G7_MASTER_TRUTH_REPORT.md |
| Different-field conflict | Account, Contact, Lead | AUTO_MERGE | G7_MASTER_TRUTH_REPORT.md |
| Financial conflict | Opportunity | SERVER_WINS | G7_MASTER_TRUTH_REPORT.md |
| State machine conflict | Task | AUTO_MERGE + STATE_MACHINE | G7_MASTER_TRUTH_REPORT.md |
| Push-only conflict | Activity, Note | SERVER_AUTHORITATIVE | G7_MASTER_TRUTH_REPORT.md |
| Pull-only conflict | Pipeline | NO_CONFLICT | G7_MASTER_TRUTH_REPORT.md |
| Tag conflict | Tags | REJECT + USER_RESOLUTION | G7_MASTER_TRUTH_REPORT.md |
| Custom field conflict | Custom Fields | DEPENDS_ON_TYPE | G7_MASTER_TRUTH_REPORT.md |
| Delete-vs-update | All | TBD | Not defined |
| Update-vs-delete | All | TBD | Not defined |
| Multi-device | All | TBD | Not defined |
| Cross-entity | All | TBD | Not defined |

---

## SECTION 14 — ENTITY POLICY

| Entity | Create Offline? | Update Offline? | Delete Offline? | Archive Offline? | Conflict Policy | Merge Policy | Resolution Policy | Evidence |
|--------|-----------------|-----------------|-----------------|------------------|-----------------|--------------|-------------------|----------|
| Account | TBD | TBD | TBD | TBD | AUTO_MERGE + USER_RESOLUTION | Field-level merge | User resolution | G7_MASTER_TRUTH_REPORT.md |
| Contact | TBD | TBD | TBD | TBD | AUTO_MERGE + USER_RESOLUTION | Field-level merge | User resolution | G7_MASTER_TRUTH_REPORT.md |
| Lead | TBD | TBD | TBD | TBD | AUTO_MERGE + USER_RESOLUTION | Field-level merge | User resolution | G7_MASTER_TRUTH_REPORT.md |
| Opportunity | TBD | TBD | TBD | TBD | SERVER_WINS (financial) | Server-authoritative | Server wins | G7_MASTER_TRUTH_REPORT.md |
| Task | TBD | TBD | TBD | TBD | AUTO_MERGE + STATE_MACHINE | State machine rules | State transitions | G7_MASTER_TRUTH_REPORT.md |
| Activity | TBD | TBD | TBD | TBD | SERVER_AUTHORITATIVE | Push-only | Server wins | G7_MASTER_TRUTH_REPORT.md |
| Note | TBD | TBD | TBD | TBD | SERVER_AUTHORITATIVE | Push-only | Server wins | G7_MASTER_TRUTH_REPORT.md |
| Pipeline | TBD | TBD | TBD | TBD | NO_CONFLICT | Pull-only | N/A | G7_MASTER_TRUTH_REPORT.md |
| Tags | TBD | TBD | TBD | TBD | REJECT + USER_RESOLUTION | Reject on conflict | User resolution | G7_MASTER_TRUTH_REPORT.md |
| Custom Fields | TBD | TBD | TBD | TBD | DEPENDS_ON_TYPE | Type-dependent | Type-dependent | G7_MASTER_TRUTH_REPORT.md |

**Note:** All offline policies are TBD — no implementation exists.

---

## SECTION 15 — C2 OFFLINE DURATION

| Component | Value | Source | Status |
|-----------|-------|--------|--------|
| Access Token Expiry | 15-30 minutes | Security requirements | PROPOSED |
| Refresh Token Expiry | 7 days | Security requirements | PROPOSED |
| Offline Duration | UNLIMITED | G7_MASTER_TRUTH_REPORT.md | PROPOSED |
| Staleness Detection | Server compares timestamps | G7_MASTER_TRUTH_REPORT.md | PROPOSED |
| Last Sync | Per-device cursor | G7_MASTER_TRUTH_REPORT.md | PROPOSED |
| Reauthentication | On token expiry | G7_MASTER_TRUTH_REPORT.md | PROPOSED |
| Full Resync | On cursor invalid, token expiry | G7_MASTER_TRUTH_REPORT.md | PROPOSED |

### Policy Type: NO POLICY (all proposed, none implemented)

---

## SECTION 16 — C3 CONFLICT RETENTION

| Component | Status | Evidence |
|-----------|--------|----------|
| Conflict Log | **MISSING** | No mobile_conflict_log table |
| Retention | **MISSING** | No retention policy defined |
| Expiration | **MISSING** | No expiration mechanism |
| Cleanup | **MISSING** | No cleanup jobs |
| Archive | **MISSING** | No archive mechanism |
| SLA | **MISSING** | No resolution SLA defined |
| Resolution Timeout | **MISSING** | No timeout mechanism |
| Scheduled Jobs | **MISSING** | No conflict cleanup jobs |

---

## SECTION 17 — SECURITY FORENSICS

| Control | Implementation | File | Method | Status | Evidence |
|---------|----------------|------|--------|--------|----------|
| JWT Authentication | ServiceJwtProvider | ServiceJwtProvider.java | mint(), validate() | **IMPLEMENTED** | 32-byte min, configurable TTL |
| RBAC Authorization | @RequireCapability | Controllers | N/A | **IMPLEMENTED** | CRM.WORKFLOW.EXECUTE capability |
| Tenant Isolation (RLS) | TenantRlsDataSource | Database layer | N/A | **IMPLEMENTED** | All tables have tenant_id |
| Idempotency | IdempotencyService | IdempotencyService.java | N/A | **IMPLEMENTED** | Server-side only |
| Audit Trail | PlatformAuditWriter | Audit adapters | N/A | **IMPLEMENTED** | Before/after JSON states |
| ETag/If-Match | CRM Controllers | CrmControllers | N/A | **IMPLEMENTED** | Optimistic locking |
| Offline Encryption | N/A | N/A | N/A | **MISSING** | No encryption strategy |
| Device Identity | N/A | N/A | N/A | **MISSING** | No device registration |
| Sync Authorization | N/A | N/A | N/A | **MISSING** | No sync-specific auth |
| Rate Limiting | N/A | N/A | N/A | **UNKNOWN** | Not verified |

---

## SECTION 18 — TEST FORENSICS

### G7_TESTS_DEFINED: 6 (from requirements)

| Test | Requirement | Behavior | Status |
|------|-------------|----------|--------|
| TEST-001 | Mobile API Contract Tests | Validate mobile API responses | **MISSING** |
| TEST-002 | Sync Integration Tests | Validate sync engine behavior | **MISSING** |
| TEST-003 | Offline Read/Write Tests | Validate offline storage | **MISSING** |
| TEST-004 | Conflict Resolution Tests | Validate conflict handling | **MISSING** |
| TEST-005 | Tenant Isolation Sync Tests | Validate cross-tenant blocking | **MISSING** |
| TEST-006 | E2E Offline-to-Online Test | Validate full lifecycle | **MISSING** |

### G7_TESTS_EXECUTED: 0
### G7_TESTS_PASSED: 0
### G7_TESTS_FAILED: 0
### G7_TESTS_MISSING: 6

---

## SECTION 19 — TEST REPORTS

| Metric | Value | Source |
|--------|-------|--------|
| Total Test Files | 208 | Repository scan |
| G7-Specific Tests | 0 | Repository scan |
| PostgreSQL-Dependent Tests | 16 | Crm009TestEnvironment |
| Test Infrastructure | PostgreSQL Direct | Governance mandate |
| Docker/Testcontainers | DEPRECATED | Governance mandate |

---

## SECTION 20 — ARCHITECTURE

### CURRENT_ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────┐
│                    SNAD Platform (Spring Boot)               │
│                                                              │
│  ┌─────────────────┐    ┌─────────────────┐                 │
│  │ CRM Controllers │    │ Workflow Engine │                 │
│  │ (v1/v2 APIs)    │    │ Integration     │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                           │
│  ┌────────▼────────┐    ┌────────▼────────┐                 │
│  │ CRM Services    │    │ CrmWorkflow     │                 │
│  │                 │    │ UseCases        │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                           │
│  ┌────────▼────────┐    ┌────────▼────────┐                 │
│  │ CRM Repositories│    │ CrmIntegration  │                 │
│  │ (JDBC)          │    │ Store           │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                           │
│  ┌────────▼──────────────────────▼────────┐                 │
│  │ PostgreSQL (RLS, Tenant Isolation)      │                 │
│  └─────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────┐
│ Next.js Web App  │
│ (BFF Proxy)      │
└──────────────────┘
```

### TARGET_ARCHITECTURE (G7)

```
┌─────────────────────────────────────────────────────────────┐
│                    SNAD Platform (Spring Boot)               │
│                                                              │
│  ┌─────────────────┐    ┌─────────────────┐                 │
│  │ CRM Controllers │    │ Mobile Sync     │                 │
│  │ (v1/v2 APIs)    │    │ Controllers     │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                           │
│  ┌────────▼────────┐    ┌────────▼────────┐                 │
│  │ CRM Services    │    │ Sync Engine     │                 │
│  │                 │    │ (Server-side)   │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                           │
│  ┌────────▼────────┐    ┌────────▼────────┐                 │
│  │ CRM Repositories│    │ Sync Metadata   │                 │
│  │ (JDBC)          │    │ Store           │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                           │
│  ┌────────▼──────────────────────▼────────┐                 │
│  │ PostgreSQL (RLS, Tenant Isolation)      │                 │
│  │ + mobile_device_registry                │                 │
│  │ + mobile_sync_cursor                    │                 │
│  │ + mobile_sync_log                       │                 │
│  │ + mobile_conflict_log                   │                 │
│  └─────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────┐          ┌──────────────────┐
│ Next.js Web App  │          │ Mobile Client    │
│ (BFF Proxy)      │          │ (SQLite/IndexedDB)│
└──────────────────┘          │ Local Sync Engine│
                              │ Outbox Pattern   │
                              └──────────────────┘
```

---

## SECTION 21 — DEPENDENCIES

| Dependency | Evidence | Required | Status | Blocking? |
|------------|----------|----------|--------|-----------|
| G1 (Database & Multi-Tenant) | G7_MASTER_TRUTH_REPORT.md | Yes | COMPLETE ✅ | No |
| G3 (Core CRM Entities) | G7_MASTER_TRUTH_REPORT.md | Yes | COMPLETE ✅ | No |
| Auth System (JWT) | ServiceJwtProvider.java | Yes | COMPLETE ✅ | No |
| Tenant Context | TenantRlsDataSource | Yes | COMPLETE ✅ | No |
| RBAC | @RequireCapability | Yes | COMPLETE ✅ | No |
| Mobile App Framework | G7_MASTER_TRUTH_REPORT.md | Yes | NOT_SELECTED | **YES** |
| Mobile Dev Team | G7_MASTER_TRUTH_REPORT.md | Yes | NOT_DEFINED | **YES** |
| ADR-G7-001 Approval | ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md | Yes | REQUIRES_REVISION | **YES** |

---

## SECTION 22 — OBSERVABILITY

| Component | Existing | Missing | Extension |
|-----------|----------|---------|-----------|
| Logging | PlatformAuditWriter | Sync-specific logging | Mobile sync audit |
| Metrics | Basic monitoring | Sync metrics (pull/push counts, latency) | Conflict metrics |
| Tracing | N/A | Distributed tracing for sync | Sync request tracing |
| Audit | Before/after JSON states | Sync operation audit | Conflict resolution audit |
| Queue Metrics | N/A | Outbox queue depth, retry rate | Mobile queue metrics |
| Conflict Metrics | N/A | Conflict count, resolution time | Per-entity conflict tracking |
| Failure Metrics | N/A | Sync failure rate, retry count | Mobile-specific failures |
| Alerts | N/A | Sync anomaly alerts | Conflict threshold alerts |

---

## SECTION 23 — NON-FUNCTIONAL REQUIREMENTS

| NFR | Requirement | Priority | Status | Evidence |
|-----|-------------|----------|--------|----------|
| Performance | Mobile API Response Time < 200ms | P1 | MISSING | No benchmarks exist |
| Latency | P95 < 200ms for list/detail/sync-pull | P1 | MISSING | No measurement |
| Throughput | 100 concurrent mobile users per tenant | P1 | MISSING | No load testing |
| Offline Duration | UNLIMITED (proposed) | N/A | PROPOSED | G7_MASTER_TRUTH_REPORT.md |
| Payload Size | Reduced by ≥40% vs full schema | P2 | MISSING | No mobile schemas |
| Batch Size | Up to 10MB for bulk sync | P2 | MISSING | No bulk sync |
| Availability | 99.9% uptime | N/A | UNKNOWN | Not defined |
| Reliability | Sync must be resumable | P1 | MISSING | No sync engine |
| Recovery | Full resync on cursor invalid | P1 | MISSING | No recovery mechanism |
| Security | AES-256 encryption at rest | P0 | MISSING | No encryption |
| Scalability | Per-tenant isolation | P0 | EXISTS (RLS) | Tenant isolation implemented |
| Storage | 50MB per device (proposed) | P2 | PROPOSED | Not implemented |

---

## SECTION 24 — FILE-LEVEL CHANGE MAP

| File | Current Role | G7 Relevance | Existing | Needs Extension | New Candidate |
|------|--------------|--------------|----------|-----------------|---------------|
| apps/sanad-platform/src/main/java/.../crm/ | CRM module root | Base for mobile sync | ✅ | Yes | — |
| apps/sanad-platform/src/main/resources/db/migration/ | Flyway migrations | New sync tables | ✅ | — | Yes (V2026*_mobile_*.sql) |
| apps/web/app/crm/crm-execution-data.ts | Execution board data | G7 task definitions | ✅ | Yes | — |
| apps/web/app/api/mobile/ | Mobile API routes | New mobile endpoints | — | — | **Yes** |
| apps/sanad-platform/src/main/java/.../crm/mobile/ | Mobile sync package | Sync engine | — | — | **Yes** |
| apps/web/app/crm/mobile/ | Mobile UI routes | Mobile views | — | — | **Yes** |

---

## SECTION 25 — GAPS

| Gap ID | Description | Evidence | Current | Expected | Severity | Priority |
|--------|-------------|----------|---------|----------|----------|----------|
| GAP-001 | Mobile Sync API Layer | No mobile APIs exist | None | 9 new endpoints | P0 BLOCKER | P0 |
| GAP-002 | Sync Metadata Schema | No sync tables | None | 4 new tables | P0 BLOCKER | P0 |
| GAP-003 | Change Tracking Columns | No standardized columns | None | updated_at, version, is_deleted on all CRM tables | P0 BLOCKER | P0 |
| GAP-004 | Conflict Resolution Policy | No conflict mechanism | None | 12 conflict classes | P0 BLOCKER | P0 |
| GAP-005 | Sync Engine | No sync engine | None | Client + server sync | P0 BLOCKER | P0 |
| GAP-006 | Offline Data Encryption | No encryption strategy | None | AES-256 at rest | P0 BLOCKER | P0 |
| GAP-007 | Offline Authorization | No offline auth | None | Cached authorization | P1 | P1 |
| GAP-008 | Conflict Detection + Resolution | No conflict handling | None | Detection + resolution | P1 | P1 |
| GAP-009 | Mobile Entity APIs | No reduced payloads | Full schemas | 40%+ reduction | P1 | P1 |
| GAP-010 | Test Suite | No G7 tests | 0 tests | 26 tests | P1 | P1 |
| GAP-011 | Device Registry | No device tracking | None | Device registration | P2 | P2 |
| GAP-012 | Sync Log | No sync audit | None | Sync history | P2 | P2 |
| GAP-013 | Entity Subset Definition | No subset defined | All entities | Defined subset | P1 | P1 |
| GAP-014 | Performance Budget | No mobile benchmarks | None | < 200ms P95 | P1 | P1 |

---

## SECTION 26 — RISKS

| Risk | Evidence | Impact | Probability | Mitigation | Blocking? |
|------|----------|--------|-------------|------------|-----------|
| ADR not approved | ADR-G7-001 status: REQUIRES_REVISION | Conflict policy undefined | HIGH | Get ADR approved | **YES** |
| No mobile framework | G7_MASTER_TRUTH_REPORT.md | Cannot start client work | MEDIUM | Select framework | **YES** |
| Encryption not defined | SECURITY_FINAL_GATE.md | Security compliance risk | HIGH | Define strategy | **YES** |
| Conflict complexity | 12 conflict classes identified | Implementation risk | MEDIUM | Phased approach | No |
| Performance not met | No benchmarks exist | User experience risk | MEDIUM | Load testing | No |
| Scope creep | Broad G7 scope | Timeline risk | MEDIUM | Strict scope control | No |
| Multi-device complexity | Multiple devices per user | Sync complexity | HIGH | Device limit + conflict handling | No |
| Test coverage gaps | No G7 tests | Quality risk | HIGH | Test-first approach | No |

---

## SECTION 27 — CONTRADICTIONS

| ID | Claim A | Source A | Claim B | Source B | Conflict Type | Impact |
|----|---------|----------|---------|----------|---------------|--------|
| CF-001 | G7 = 33 P0 requirements | Mission plan | G7 = 12 P0 requirements | G7_MASTER_REQUIREMENTS_BASELINE.md | Count Discrepancy | Planning ambiguity |
| CF-002 | G7 requires 7 tables | Some documentation | G7 requires 4 tables | G7_MASTER_TRUTH_REPORT.md | Table Count | Schema design |
| CF-003 | G7 requires 9 APIs | G7_MASTER_TRUTH_REPORT.md | G7 requires 12 APIs | Some documentation | API Count | API design |
| CF-004 | 12 vs 15 tests | Different sources | 26 tests total | G7_MASTER_REQUIREMENTS_BASELINE.md | Test Count | Test planning |
| CF-005 | Agent F evidence | Previous agent | Different evidence | Current extraction | Source Conflict | Evidence reliability |
| CF-006 | G8 dependency exists | Some documentation | No G8 dependency | G7_IDENTITY_FINAL.md | Dependency Conflict | Scope clarity |

---

## SECTION 28 — UNKNOWN REGISTER

| ID | Question | Missing Evidence | Impact | Blocking? |
|----|----------|------------------|--------|-----------|
| UNKNOWN-001 | Which mobile framework to use? | No framework selected | Cannot start client work | **YES** |
| UNKNOWN-002 | Will conflict policy be approved? | ADR pending revision | Cannot finalize conflict resolution | **YES** |
| UNKNOWN-003 | What encryption strategy? | No strategy defined | Security compliance at risk | **YES** |
| UNKNOWN-004 | What payload optimization level? | No benchmarks | Performance target unclear | No |
| UNKNOWN-005 | What sync frequency? | No guidance defined | Client behavior unclear | No |
| UNKNOWN-006 | What storage limits? | No limits defined | Device storage risk | No |
| UNKNOWN-007 | Is security analysis complete? | No audit performed | Compliance risk | No |
| UNKNOWN-008 | Why 101 vs 39 requirements? | Count discrepancy | Planning ambiguity | No |

---

## SECTION 29 — RAW EVIDENCE INDEX

| Evidence ID | File | Line/Symbol | Claim | Category | Authority | Confidence |
|-------------|------|-------------|-------|----------|-----------|------------|
| EV-001 | apps/web/app/crm/crm-execution-data.ts | lines 129-137 | G7 = Mobile Offline Foundation | Identity | Product Execution Board | HIGH |
| EV-002 | G7_IDENTITY_FINAL.md | lines 1-56 | G7 scope and dependencies | Identity | Documentation | HIGH |
| EV-003 | G7_MASTER_REQUIREMENTS_BASELINE.md | lines 1-784 | 39 requirements baselined | Requirements | Documentation | HIGH |
| EV-004 | G7_FORENSIC_EXTRACTION_REPORT.md | lines 1-886 | 4 conflicting G7 definitions | Identity | Forensic Report | HIGH |
| EV-005 | G7_MASTER_TRUTH_REPORT.md | lines 1-453 | Current vs target state | Architecture | Documentation | HIGH |
| EV-006 | apps/sanad-platform/src/main/java/.../crm/ | N/A | CRM module exists | Implementation | Code | HIGH |
| EV-007 | apps/sanad-platform/src/main/resources/db/migration/ | V20260716_1-V20260717_4 | 13 CRM tables exist | Database | Migration files | HIGH |
| EV-008 | apps/sanad-platform/src/main/java/.../concurrency/ | N/A | Optimistic locking implemented | Concurrency | Code | HIGH |
| EV-009 | apps/sanad-platform/src/main/java/.../crm/dto/ | N/A | Idempotency framework exists | Idempotency | Code | HIGH |
| EV-010 | 208 test files | N/A | No G7-specific tests | Testing | Repository scan | HIGH |

---

## SECTION 30 — EXTRACTION DATASET SUMMARY

### G7_IDENTITY: G7 = أساس الجوال = Mobile Offline Foundation

### REQUIREMENTS_TOTAL: 39

| Priority | Count |
|----------|-------|
| P0 | 12 |
| P1 | 13 |
| P2 | 9 |
| P3 | 2 |

### COMPONENTS

| Status | Count |
|--------|-------|
| EXISTING_COMPONENTS | 9 |
| PARTIAL_COMPONENTS | 2 |
| MISSING_COMPONENTS | 12 |
| BROKEN_COMPONENTS | 0 |

### TABLES

| Status | Count |
|--------|-------|
| EXISTING_TABLES | ~97 |
| EXTENDED_TABLES | 0 |
| MISSING_TABLES | 4 |

### APIS

| Status | Count |
|--------|-------|
| TOTAL_CONTROLLERS | 56 |
| TOTAL_ENDPOINTS | ~250+ |
| EXISTING_APIS (Mobile-Relevant) | 10 |
| EXTENSION_APIS | 0 |
| MISSING_APIS | 9 |

### TESTS

| Status | Count |
|--------|-------|
| G7_TESTS_DEFINED | 6 |
| G7_TESTS_EXECUTED | 0 |
| G7_TESTS_PASSED | 0 |
| G7_TESTS_FAILED | 0 |
| G7_TESTS_MISSING | 6 |

### SECURITY

| Status | Count |
|--------|-------|
| SECURITY_CONTROLS_VERIFIED | 6 |
| SECURITY_CONTROLS_MISSING | 4 |
| SECURITY_CONTROLS_UNKNOWN | 1 |

### OTHER

| Metric | Count |
|--------|-------|
| CONTRADICTIONS | 4 |
| UNKNOWN_ITEMS | 8 |
| BLOCKERS | 4 |
| G7_DEFINITIONS | 7 |

---

**END OF G7 FORENSIC EXTRACTION DATASET**

> **Mode:** EVIDENCE EXTRACTION ONLY
> **No decisions made. No design created. No code written. No architecture adopted.**
> **This dataset will be used to build G7 MASTER TRUTH and G7 IMPLEMENTATION PLAN.**
