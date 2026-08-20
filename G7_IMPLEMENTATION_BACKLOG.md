# Phase 16: Implementation Work Packages

**Purpose:** Convert requirements into executable work packages with clear inputs, outputs, and dependencies.

## Work Package A — Foundation

*   **ID:** WP-A
*   **Objective:** Establish G7 foundation infrastructure
*   **Requirements:** G7-MOB-DATA-001, G7-MOB-DATA-002
*   **Inputs:** Existing Flyway infrastructure, existing CRM table definitions
*   **Dependencies:** None
*   **Modules:** database/migration
*   **Files:** `V2026MMDD_1__create_mobile_sync_metadata.sql`, `V2026MMDD_2__add_change_tracking.sql`
*   **DB Impact:** 4 new tables, column additions to CRM tables
*   **API Impact:** None
*   **Security Impact:** RLS policies on new tables
*   **Tests:** Schema validation, RLS verification
*   **Acceptance:** All tables created, RLS enforced, existing tests pass
*   **DoD:** Flyway migration committed, schema verified

---

## Work Package B — Local Persistence

*   **ID:** WP-B
*   **Objective:** Client-side offline storage
*   **Requirements:** G7-MOB-SYNC-001 (local storage)
*   **Inputs:** Entity models from server
*   **Dependencies:** WP-A (schema must be defined)
*   **Modules:** mobile/storage
*   **Files:** Local database schema, entity models, CRUD operations
*   **DB Impact:** Client-side SQLite/IndexedDB
*   **API Impact:** None
*   **Security Impact:** Local encryption
*   **Tests:** Local CRUD, offline read/write
*   **Acceptance:** Local storage works offline
*   **DoD:** Client storage implemented with encryption

---

## Work Package C — Mutation Queue

*   **ID:** WP-C
*   **Objective:** Client-side mutation queue
*   **Requirements:** G7-MOB-SYNC-003, G7-MOB-SYNC-007, G7-MOB-SYNC-008
*   **Inputs:** WP-B (local storage)
*   **Dependencies:** WP-B
*   **Modules:** mobile/queue
*   **Files:** Queue implementation, state machine, retry logic
*   **DB Impact:** Queue table in local storage
*   **API Impact:** None
*   **Security Impact:** Queue data encrypted
*   **Tests:** Queue operations, retry, dead letter
*   **Acceptance:** Queue persists across app restarts
*   **DoD:** Queue implemented with state machine

---

## Work Package D — Pull Sync

*   **ID:** WP-D
*   **Objective:** Server-to-client delta sync
*   **Requirements:** G7-MOB-FR-003, G7-MOB-SYNC-002, G7-MOB-SYNC-004
*   **Inputs:** WP-A (schema), existing CRM APIs
*   **Dependencies:** WP-A
*   **Modules:** backend/sync, mobile/sync
*   **Files:** `PullSyncController.java`, `PullSyncService.java`, cursor management
*   **DB Impact:** `mobile_sync_cursor` reads
*   **API Impact:** `GET /api/v2/mobile/sync/pull`
*   **Security Impact:** JWT + RBAC + RLS
*   **Tests:** Delta pull, pagination, cursor management
*   **Acceptance:** Delta pull returns only changes
*   **DoD:** Pull sync functional with tests

---

## Work Package E — Push Sync

*   **ID:** WP-E
*   **Objective:** Client-to-server batch sync
*   **Requirements:** G7-MOB-FR-004, G7-MOB-SYNC-003
*   **Inputs:** WP-A (schema), WP-C (queue), existing CRM APIs
*   **Dependencies:** WP-A, WP-C
*   **Modules:** backend/sync, mobile/sync
*   **Files:** `PushSyncController.java`, `PushSyncService.java`, batch processing
*   **DB Impact:** `mobile_sync_log` writes
*   **API Impact:** `POST /api/v2/mobile/sync/push`
*   **Security Impact:** JWT + RBAC + RLS + Idempotency
*   **Tests:** Batch push, idempotency, partial failure
*   **Acceptance:** Batch push processes correctly
*   **DoD:** Push sync functional with tests

---

## Work Package F — Idempotency

*   **ID:** WP-F
*   **Objective:** Sync operation idempotency
*   **Requirements:** G7-MOB-SYNC-008
*   **Inputs:** Existing `IdempotencyService`
*   **Dependencies:** WP-E
*   **Modules:** backend/idempotency
*   **Files:** Extend `IdempotencyService` for sync operations
*   **DB Impact:** Uses existing `crm_idempotency_records`
*   **API Impact:** `Idempotency-Key` header on push
*   **Security Impact:** Prevents replay attacks
*   **Tests:** Duplicate mutation handling
*   **Acceptance:** Duplicate mutations return same result
*   **DoD:** Idempotency verified for all sync operations

---

## Work Package G — Conflict Resolution

*   **ID:** WP-G
*   **Objective:** Conflict detection and resolution
*   **Requirements:** G7-MOB-SYNC-005, G7-MOB-SYNC-006, G7-MOB-FR-008
*   **Inputs:** WP-E (push), ADR-G7-001 (policy)
*   **Dependencies:** WP-E, ADR approval
*   **Modules:** backend/conflict, mobile/conflict
*   **Files:** `ConflictDetectionService`, `ConflictResolutionService`, conflict API
*   **DB Impact:** `mobile_conflict_log` writes
*   **API Impact:** `GET/POST /api/v2/mobile/conflicts/*`
*   **Security Impact:** Conflict resolution authorization
*   **Tests:** All 12 conflict classes
*   **Acceptance:** Conflicts detected and resolved correctly
*   **DoD:** Conflict system functional with tests

---

## Work Package H — Delete/Recovery

*   **ID:** WP-H
*   **Objective:** Delete conflict handling and recovery
*   **Requirements:** Conflict classes C3, C4, C12
*   **Inputs:** WP-G (conflict resolution)
*   **Dependencies:** WP-G
*   **Modules:** backend/sync, mobile/sync
*   **Files:** Delete handling, full resync logic
*   **DB Impact:** Soft delete handling
*   **API Impact:** Delete operations in push sync
*   **Security Impact:** Delete authorization
*   **Tests:** Delete conflicts, full resync
*   **Acceptance:** Delete conflicts handled correctly
*   **DoD:** Delete and recovery functional

---

## Work Package I — Security

*   **ID:** WP-I
*   **Objective:** Security hardening for mobile sync
*   **Requirements:** G7-MOB-SEC-001, G7-MOB-SEC-002, G7-MOB-SEC-003, G7-MOB-SEC-004, G7-MOB-SEC-005
*   **Inputs:** Existing security infrastructure
*   **Dependencies:** WP-A (RLS), WP-B (encryption)
*   **Modules:** security/mobile
*   **Files:** `DeviceRegistryService`, `MobileAuthService`, encryption
*   **DB Impact:** `mobile_device_registry`
*   **API Impact:** `POST /api/v2/mobile/device/register`
*   **Security Impact:** Full security hardening
*   **Tests:** Security tests for all scenarios
*   **Acceptance:** All security requirements met
*   **DoD:** Security gate passed

---

## Work Package J — Observability

*   **ID:** WP-J
*   **Objective:** Sync observability and monitoring
*   **Requirements:** G7-MOB-DATA-004
*   **Inputs:** WP-D, WP-E (sync operations)
*   **Dependencies:** WP-D, WP-E
*   **Modules:** backend/observability
*   **Files:** Sync metrics, logging, alerting
*   **DB Impact:** `mobile_sync_log` reads
*   **API Impact:** None
*   **Security Impact:** Log access control
*   **Tests:** Metric collection, log accuracy
*   **Acceptance:** Sync operations fully observable
*   **DoD:** Dashboards and alerts configured

---

## Work Package K — Testing

*   **ID:** WP-K
*   **Objective:** Complete test suite
*   **Requirements:** G7-MOB-TEST-001 through G7-MOB-TEST-006
*   **Inputs:** All other work packages
*   **Dependencies:** All WPs
*   **Modules:** test/*
*   **Files:** All test files
*   **DB Impact:** Test database setup
*   **API Impact:** Test endpoints
*   **Security Impact:** Test security
*   **Tests:** All 26 tests
*   **Acceptance:** All tests pass
*   **DoD:** 100% test coverage for G7

---

## Work Package L — Release

*   **ID:** WP-L
*   **Objective:** Production readiness and release
*   **Requirements:** All
*   **Inputs:** All other work packages
*   **Dependencies:** All WPs
*   **Modules:** deployment
*   **Files:** Deployment scripts, documentation
*   **DB Impact:** Migration verification
*   **API Impact:** API documentation
*   **Security Impact:** Security audit
*   **Tests:** Smoke tests, load tests
*   **Acceptance:** Production deployment successful
*   **DoD:** G7 released to production
