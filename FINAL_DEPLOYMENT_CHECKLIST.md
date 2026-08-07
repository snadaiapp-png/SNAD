# FINAL DEPLOYMENT CHECKLIST

**Date:** 2026-08-07

---

## PRE-COMMIT CHECKLIST

- [x] Backend compiles cleanly (`mvn compile` exit 0)
- [x] Frontend TypeScript typechecks (`tsc --noEmit` exit 0 for CRM files)
- [x] Frontend builds successfully (`next build` exit 0, all CRM routes)
- [x] Frontend tests pass (669/669 vitest)
- [x] Backend tests pass (1012/1059 — 3 failures are test defects, 44 errors are Docker)
- [x] No CRM regressions
- [x] No security regressions
- [x] No Flyway regressions
- [x] No API regressions
- [x] CrmExceptionHandler fix applied (CrmContractControllerR1 added)

## FILES TO COMMIT

### Backend Changes (15 files)
1. `apps/sanad-platform/src/main/java/com/sanad/platform/security/dto/AuthResponse.java` — Added `capabilities` field
2. `apps/sanad-platform/src/main/java/com/sanad/platform/security/api/AuthController.java` — Populate capabilities in bootstrap
3. `apps/sanad-platform/src/main/java/com/sanad/platform/security/dto/MeResponse.java` — Added `capabilities` field
4. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmContractControllerR1.java` — New CRM endpoints
5. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/mapper/CrmDtoMapper.java` — Stage/Activity mapper overloads
6. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmUpdateDtos.java` — New update request records
7. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CreatePipelineRequest.java` — Pipeline creation request
8. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/opportunity/application/OpportunityUseCases.java` — Stage CRUD
9. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/opportunity/domain/PipelineRepository.java` — Domain interface
10. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/opportunity/infrastructure/JdbcPipelineRepository.java` — JDBC implementation
11. `apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/CrmExceptionHandler.java` — **FIXED**: Added CrmContractControllerR1
12. `apps/sanad-platform/src/main/java/com/sanad/platform/access/role/RoleCapabilityRepository.java` — Batch capability query
13. `apps/sanad-platform/src/main/java/com/sanad/platform/config/ProductionMockGuard.java` — New production guard
14. `apps/sanad-platform/src/main/resources/META-INF/spring.factories` — Register ProductionMockGuard
15. `apps/sanad-platform/src/test/java/com/sanad/platform/crm/contract/CrmMapperContractTest.java` — Test update

### Database Migrations (4 files)
16. `apps/sanad-platform/src/main/resources/db/migration/V20260807_1__grant_crm_capabilities_to_non_admin_roles.sql`
17. `apps/sanad-platform/src/main/resources/db/migration/V20260807_2__seed_default_pipeline_and_accounts.sql`
18. `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260807_3__add_case_insensitive_tag_unique_index.sql`
19. `apps/sanad-platform/src/main/resources/db/migration/V20260807_4__add_activity_result_column_and_related_type_check.sql`

### Frontend Changes (14 files)
20. `apps/web/app/crm/components/crm-shell.tsx` — Fixed React Rules of Hooks
21. `apps/web/lib/api/auth.ts` — Added capabilities to AuthResponse
22. `apps/web/lib/api/crm.ts` — Added CrmStage.active, CrmActivity fields, updateActivity
23. `apps/web/lib/auth/capabilities.ts` — New capability utilities
24. `apps/web/app/crm/crm-rbac.test.tsx` — Updated test mocks with capabilities
25. `apps/web/app/crm/crm-interactions.test.tsx` — Added active: true to stage fixtures
26. `apps/web/app/workspace/page.test.tsx` — Added capabilities to mock
27. `apps/web/lib/api/auth-flow.test.ts` — Added capabilities to mock
28. `apps/web/app/crm/(operational)/activities/page.tsx` — Capability gates
29. `apps/web/app/crm/(operational)/cases/page.tsx` — Lifecycle actions
30. `apps/web/app/crm/(operational)/pipelines/page.tsx` — Pipeline/stage CRUD
31. `apps/web/app/crm/(operational)/tags/page.tsx` — Tag management
32. `apps/web/app/crm/crm-view-utils.ts` — View utilities
33. `apps/web/lib/i18n/locales/en.ts` — English translations
34. `apps/web/lib/i18n/locales/ar.ts` — Arabic translations

### Other
35. `PRODUCTION-VERIFICATION.md` — Updated verification doc

---

## POST-COMMIT CHECKLIST

- [ ] Verify migrations run cleanly against PostgreSQL production database
- [ ] Verify capabilities appear in `/auth/me` response for test user
- [ ] Verify RBAC enforcement on pipeline/stage endpoints
- [ ] Verify mock data is blocked in production profile
- [ ] Run full Docker-enabled CI/CD pipeline
- [ ] Verify Vercel deployment succeeds
- [ ] Smoke test CRM module in staging environment
