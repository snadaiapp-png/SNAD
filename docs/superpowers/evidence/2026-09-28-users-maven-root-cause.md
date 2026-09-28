# Users Module Maven root-cause evidence — 2026-09-28

Governing failed run: `36359906833` on SHA `553bd8e8feb60ccbe14a0dfe276e50b764553b40`.

## Proven failures from Surefire

- `PlatformUserServiceTest`: mocked `ControlPlaneAccessGuard` returned Mockito's default `false` for `isControlPlaneTenant(CONTROL_TENANT)`, causing `AccessDeniedException: Control-plane tenant required` before the behavior under test.
- `PlatformRoleServiceTest`: same fixture defect and same fail-closed guard result.
- `PlatformApiCountTest`: stale Executive API contract expected `94` operations while Task 5 intentionally adds 20 Platform IAM operations (13 user/session/role operations + 7 role/capability operations), so the governed Executive count is `114` and the platform total is `959` instead of `939`.

## Applied correction

- Platform user/role service fixtures now explicitly model the authenticated control tenant with `when(guard.isControlPlaneTenant(CONTROL_TENANT)).thenReturn(true)`.
- Production `ControlPlaneAccessGuard` and Platform IAM authorization behavior were not weakened or bypassed.
- API count contract updated to the exact Task 5 surface: Executive `114`, total `959`.

This evidence file exists to trigger and bind the new GitHub Actions verification run to the corrected exact PR head. No PASS/GREEN claim is valid until that run completes successfully.
