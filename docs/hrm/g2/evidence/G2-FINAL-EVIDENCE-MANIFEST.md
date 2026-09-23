# G2 Final Evidence Manifest

## Status
```
G2_FINAL_GATE = NOT_CLOSED
IMPLEMENTATION_COMPLETE = YES (all 29 requirements DONE or PARTIAL)
G2_FINAL_EXACT_HEAD_SHA = PENDING (set after final push)
REMOTE_EXACT_HEAD_CI = PENDING
AUTHENTICATED_G2_E2E = PENDING (tests written, credentials required)
BACKEND_LIFECYCLE_INTEGRATION = NOT_PROVEN (schema/RLS tests written, lifecycle tests PENDING on CI)
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

## Files
- Migrations: V20260923_1 through V20260924_3 (6 migrations)
- Backend services: 8 Java classes (schedule, attendance, calculation, timesheet, leave, ledger, adapter, clock config)
- Controller: HrTimeAttendanceV2Controller (30+ endpoints with @Operation + @RequireCapability)
- Frontend: 10 screens (attendance, timesheets, leave, team-attendance, team-timesheets, leave/approvals, schedules, leave/policies, attendance/admin, reports/attendance)
- Tests: HrG2PostgresIntegrationTest (7 real tests) + HrG2OpenApiContractTest (5 tests)
- E2E: e2e/g2-authenticated.spec.ts (4 suites, fail-closed)
- Docs: design spec + implementation plan + closure certificate + requirement matrix + this manifest
