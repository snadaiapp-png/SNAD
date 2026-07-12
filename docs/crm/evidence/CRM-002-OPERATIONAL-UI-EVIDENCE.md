# CRM-002: Operational UI Evidence

## Starting SHA
dd29d0e7c39f4c704b5e6968c24fdafe5b03165e

## Routes Implemented (15 total)
| Route | Type | Status |
|---|---|---|
| /crm | Server redirect → /crm/overview | ✅ |
| /crm/overview | Dashboard with real KPIs | ✅ |
| /crm/accounts | List + create + archive | ✅ |
| /crm/accounts/[accountId] | Customer 360 | ✅ |
| /crm/contacts | List + create + archive | ✅ |
| /crm/contacts/[contactId] | Contact detail | ✅ |
| /crm/leads | List + create + status + convert | ✅ |
| /crm/leads/[leadId] | Lead detail + convert dialog | ✅ |
| /crm/pipelines | List + create + stages | ✅ |
| /crm/opportunities | List + pipeline board | ✅ |
| /crm/opportunities/[opportunityId] | Opportunity detail + stage move | ✅ |
| /crm/activities | List + create + complete | ✅ |
| /crm/imports | Upload + job list + errors | ✅ |
| /crm/settings/custom-fields | Admin UI | ✅ |
| /crm/command-center | Governance shell | ✅ |

## Backend Endpoints Connected (45+)
All /api/v1/crm/* endpoints connected via crmApi client.

## E2E Tests
- apps/web/e2e/crm-operational.spec.ts — Route smoke tests (unauthenticated)
- Classification: CRM Route Smoke — unauthenticated
- Tests: 15 (route rendering, navigation, detail routes)

## Frontend Tests
- 393 tests across 35 files (all passing)
- Includes crm-rbac.test.tsx and crm-routes.test.tsx

## Known Limitations
1. Authenticated E2E requires live backend + seeded data (not available in CI)
2. Tenant isolation E2E requires multi-tenant test environment
3. RBAC integration tests require backend with test users
4. Import mapping UI exists but full mapping workflow needs backend testing
5. Custom field values editing exists but needs authenticated verification

## Test Environment Requirements (for future authenticated E2E)
- PostgreSQL with Flyway migrations
- Spring Boot backend running
- Seeded tenants (A and B) with users
- Different capability sets per user
- Access tokens generated during test

## Next Prompt
EXEC-PROMPT-CRM-003
