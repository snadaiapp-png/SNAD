# Phase 17: Execution Order with Dependency DAG

**Purpose:** Define the optimal execution sequence and dependency graph for the G7 Implementation Work Packages.

## Execution Sequence

1.  **WP-A (Foundation)** — No dependencies
2.  **WP-B (Local Persistence)** — Depends on WP-A
3.  **WP-C (Mutation Queue)** — Depends on WP-B
4.  **WP-D (Pull Sync)** — Depends on WP-A
5.  **WP-E (Push Sync)** — Depends on WP-A, WP-C
6.  **WP-F (Idempotency)** — Depends on WP-E
7.  **WP-G (Conflict Resolution)** — Depends on WP-E, ADR approval
8.  **WP-H (Delete/Recovery)** — Depends on WP-G
9.  **WP-I (Security)** — Depends on WP-A, WP-B
10. **WP-J (Observability)** — Depends on WP-D, WP-E
11. **WP-K (Testing)** — Depends on all WPs
12. **WP-L (Release)** — Depends on all WPs

## Parallel Tracks

*   **Track 1 (Server):** WP-A -> WP-D -> WP-E -> WP-F -> WP-G -> WP-H
*   **Track 2 (Client):** WP-A -> WP-B -> WP-C
*   **Track 3 (Security):** WP-A, WP-B -> WP-I
*   **Track 4 (Observability):** WP-D, WP-E -> WP-J
*   **Track 5 (Quality):** All -> WP-K -> WP-L

## Critical Path

**WP-A -> WP-D -> WP-E -> WP-G -> WP-K -> WP-L**

---

## Detailed Work Package Specifications

### Work Package A (Foundation)

*   **Entry Criteria:** Access to existing Flyway infrastructure and CRM table definitions.
*   **Dependencies:** None.
*   **Implementation Steps:**
    1.  Define `mobile_sync_metadata` schema.
    2.  Define `mobile_sync_cursor` schema.
    3.  Define `mobile_device_registry` schema.
    4.  Define `mobile_conflict_log` schema.
    5.  Create Flyway migration scripts.
    6.  Implement RLS policies.
*   **Verification Steps:**
    1.  Run Flyway migration.
    2.  Verify table creation.
    3.  Verify RLS enforcement.
*   **Exit Criteria:** All 4 tables created, RLS enforced, existing tests pass.
*   **Next Gate:** WP-B, WP-D, WP-I can begin.

### Work Package B (Local Persistence)

*   **Entry Criteria:** WP-A completed (server schema defined).
*   **Dependencies:** WP-A.
*   **Implementation Steps:**
    1.  Define client-side schema (SQLite/IndexedDB).
    2.  Implement entity models.
    3.  Implement CRUD operations.
    4.  Implement local encryption.
*   **Verification Steps:**
    1.  Verify offline read/write.
    2.  Verify encryption.
*   **Exit Criteria:** Client storage works offline with encryption.
*   **Next Gate:** WP-C, WP-I can begin.

### Work Package C (Mutation Queue)

*   **Entry Criteria:** WP-B completed (local storage available).
*   **Dependencies:** WP-B.
*   **Implementation Steps:**
    1.  Define queue table schema.
    2.  Implement state machine.
    3.  Implement retry logic.
    4.  Implement dead letter queue.
*   **Verification Steps:**
    1.  Verify queue persistence across restarts.
    2.  Verify retry and dead letter logic.
*   **Exit Criteria:** Queue implemented with state machine and retry logic.
*   **Next Gate:** WP-E can begin.

### Work Package D (Pull Sync)

*   **Entry Criteria:** WP-A completed (schema defined), existing CRM APIs available.
*   **Dependencies:** WP-A.
*   **Implementation Steps:**
    1.  Implement `PullSyncController`.
    2.  Implement `PullSyncService`.
    3.  Implement cursor management.
    4.  Implement delta query logic.
*   **Verification Steps:**
    1.  Verify delta pull returns only changes.
    2.  Verify pagination.
    3.  Verify cursor management.
*   **Exit Criteria:** Pull sync functional with tests.
*   **Next Gate:** WP-E, WP-J can begin.

### Work Package E (Push Sync)

*   **Entry Criteria:** WP-A, WP-C, WP-D completed.
*   **Dependencies:** WP-A, WP-C.
*   **Implementation Steps:**
    1.  Implement `PushSyncController`.
    2.  Implement `PushSyncService`.
    3.  Implement batch processing.
    4.  Implement idempotency key handling.
*   **Verification Steps:**
    1.  Verify batch push.
    2.  Verify idempotency.
    3.  Verify partial failure handling.
*   **Exit Criteria:** Push sync functional with tests.
*   **Next Gate:** WP-F, WP-G, WP-J can begin.

### Work Package F (Idempotency)

*   **Entry Criteria:** WP-E completed (push sync available).
*   **Dependencies:** WP-E.
*   **Implementation Steps:**
    1.  Extend `IdempotencyService` for sync operations.
    2.  Implement idempotency key validation.
    3.  Implement duplicate mutation handling.
*   **Verification Steps:**
    1.  Verify duplicate mutations return same result.
    2.  Verify idempotency key header handling.
*   **Exit Criteria:** Idempotency verified for all sync operations.
*   **Next Gate:** None (parallel with WP-G).

### Work Package G (Conflict Resolution)

*   **Entry Criteria:** WP-E completed (push sync available), ADR-G7-001 approved.
*   **Dependencies:** WP-E, ADR approval.
*   **Implementation Steps:**
    1.  Implement `ConflictDetectionService`.
    2.  Implement `ConflictResolutionService`.
    3.  Implement conflict API.
    4.  Implement conflict logging.
*   **Verification Steps:**
    1.  Verify all 12 conflict classes.
    2.  Verify conflict detection and resolution.
    3.  Verify conflict logging.
*   **Exit Criteria:** Conflict system functional with tests.
*   **Next Gate:** WP-H can begin.

### Work Package H (Delete/Recovery)

*   **Entry Criteria:** WP-G completed (conflict resolution available).
*   **Dependencies:** WP-G.
*   **Implementation Steps:**
    1.  Implement delete conflict handling.
    2.  Implement full resync logic.
    3.  Implement soft delete handling.
*   **Verification Steps:**
    1.  Verify delete conflicts.
    2.  Verify full resync.
*   **Exit Criteria:** Delete and recovery functional.
*   **Next Gate:** None (parallel with WP-F).

### Work Package I (Security)

*   **Entry Criteria:** WP-A, WP-B completed (RLS and encryption available).
*   **Dependencies:** WP-A, WP-B.
*   **Implementation Steps:**
    1.  Implement `DeviceRegistryService`.
    2.  Implement `MobileAuthService`.
    3.  Implement device registration.
    4.  Implement security hardening.
*   **Verification Steps:**
    1.  Verify security tests for all scenarios.
    2.  Verify device registry.
    3.  Verify mobile auth.
*   **Exit Criteria:** All security requirements met.
*   **Next Gate:** None (parallel with WP-F, WP-G, WP-H).

### Work Package J (Observability)

*   **Entry Criteria:** WP-D, WP-E completed (sync operations available).
*   **Dependencies:** WP-D, WP-E.
*   **Implementation Steps:**
    1.  Implement sync metrics.
    2.  Implement logging.
    3.  Implement alerting.
    4.  Implement dashboards.
*   **Verification Steps:**
    1.  Verify metric collection.
    2.  Verify log accuracy.
    3.  Verify alerting.
*   **Exit Criteria:** Sync operations fully observable.
*   **Next Gate:** None (parallel with WP-F, WP-G, WP-H).

### Work Package K (Testing)

*   **Entry Criteria:** All other work packages completed.
*   **Dependencies:** All WPs.
*   **Implementation Steps:**
    1.  Implement unit tests.
    2.  Implement integration tests.
    3.  Implement end-to-end tests.
    4.  Implement performance tests.
*   **Verification Steps:**
    1.  Verify all 26 tests.
    2.  Verify 100% test coverage.
*   **Exit Criteria:** All tests pass, 100% test coverage for G7.
*   **Next Gate:** WP-L can begin.

### Work Package L (Release)

*   **Entry Criteria:** All other work packages completed.
*   **Dependencies:** All WPs.
*   **Implementation Steps:**
    1.  Create deployment scripts.
    2.  Create documentation.
    3.  Perform security audit.
    4.  Perform load tests.
    5.  Deploy to production.
*   **Verification Steps:**
    1.  Verify migration.
    2.  Verify API documentation.
    3.  Verify production deployment.
*   **Exit Criteria:** Production deployment successful.
*   **Next Gate:** None (final gate).
