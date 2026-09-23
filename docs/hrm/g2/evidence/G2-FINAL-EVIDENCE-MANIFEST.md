# G2 Final Evidence Manifest

## Status
```
G2_FINAL_GATE = NOT_CLOSED
IMPLEMENTATION_COMPLETE = NO
G2_FINAL_EXACT_HEAD_SHA = NOT_ASSIGNED (set after final push)
REMOTE_EXACT_HEAD_CI = PENDING
AUTHENTICATED_G2_E2E = NOT_PROVEN (tests written, credentials required for CI)
BACKEND_LIFECYCLE_INTEGRATION = NOT_PROVEN (schema/RLS tests written, lifecycle PENDING on CI)
INDEPENDENT_APPROVAL = NOT_REQUESTED
MERGE_AUTHORIZATION = NO
POST_MERGE_A_F = NOT_RUN
```

## Local Tests
```
LOCAL_FRONTEND_TESTS = PASS (typecheck+lint+SDS+i18n+vitest+build+perf)
LOCAL_BACKEND_COMPILE = NOT_RUN_ENVIRONMENT_LIMITATION (no JDK)
LOCAL_BACKEND_INTEGRATION = NOT_RUN_ENVIRONMENT_LIMITATION (no PostgreSQL)
```

## Known Gaps (honest)
1. Workflow definition "HR_LEAVE_APPROVAL" not seeded via migration (adapter throws if not found)
2. Backend lifecycle integration tests are schema/RLS only (not full Spring Boot service tests)
3. Authenticated E2E tests written but not provisioned with credentials
4. OpenAPI is auto-generated from annotations (contract test verifies paths, not full schema)
5. Leave policies CRUD UI not implemented (table exists, read-only view)
6. Attendance break actions UI not implemented (backend event model exists)
7. Manual correction UI not implemented (backend event model exists)
