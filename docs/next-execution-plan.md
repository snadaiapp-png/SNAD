# Next Execution Plan

## Phase 1: Unblock Production (IMMEDIATE)
1. Set `SANAD_CONTROL_PLANE_TENANT_ID` in Render Dashboard
   - Must be a valid UUID of an existing production tenant
   - Tenant must have ACTIVE status
   - Must be linked to an admin user with Control Plane capabilities
2. Re-run production-release.yml workflow
3. Verify Render deploys exact SHA 95fd84f
4. Verify Vercel Control Plane returns HTTP 200
5. Run authenticated admin smoke test

## Phase 2: Add Forward-Only Infrastructure Migrations (1-2 SPRINTS)
1. Create clean PR from `fix/flyway-forward-migrations-20260703` approach
2. Add V20260703_1 through V20260703_18 forward-only migrations:
   - Audit log, idempotency records, audit chain heads
   - RLS policies for all tenant-owned tables (including CRM)
   - Platform security audit events
   - Runtime role hardening
3. Update `application-prod.yml` to include `classpath:db/migration-pg-only`
4. Add `verify-flyway-artifact.sh` and `FlywayMigrationConsistencyTest`
5. CI must pass: flyway-validation, tenant-isolation, audit-idempotency

## Phase 3: CRM Completion (2-4 SPRINTS)
Priority order:
1. **Tasks** — Create tasks linked to accounts, contacts, opportunities
2. **Notes** — Add notes to any CRM entity
3. **Tags/Labels** — Categorize CRM entities
4. **Advanced Search** — Full-text search across CRM entities
5. **Export** — CSV/Excel export for accounts, contacts, leads
6. **Custom Fields API** — CRUD for custom field definitions + values
7. **Reports** — Basic sales pipeline and activity reports
8. **Notifications** — CRM event notifications
9. **Products/Services** — Product catalog
10. **Quotations** — Quote generation from opportunities
11. **Sales Orders** — Order management
12. **Workflow Automation** — Automated lead assignment, stage transitions
13. **AI Integration** — Lead scoring, activity summarization

## Phase 4: Security Hardening (ONGOING)
1. Merge security denial classification from infra/05a291 branch
2. Add RLS policies for CRM tables (Phase 2 covers this)
3. Audit logging for all CRM mutations
4. Rate limiting for CRM API endpoints
5. Input validation for all CRM endpoints

## Phase 5: Production Monitoring (ONGOING)
1. Set up application metrics dashboards
2. Configure alerting for health check failures
3. Set up audit log monitoring
4. Configure backup verification
5. Set up synthetic monitoring for critical user flows

