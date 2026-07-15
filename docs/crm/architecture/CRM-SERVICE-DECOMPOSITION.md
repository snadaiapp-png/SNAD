# CRM Service Decomposition

## Before
- CrmExtendedService: 2,042 lines (monolith — all domains)
- CrmService: 222 lines (monolith — account/lead/pipeline/opportunity/activity)
- CrmV2MutationService: 443 lines (atomic mutations)

## After (Phase 2)
- JdbcAccountRepository: Account SQL (was in CrmService/CrmExtendedService)
- JdbcContactRepository: Contact SQL (was in CrmExtendedService)
- JdbcLeadRepository: Lead SQL (was in CrmService/CrmExtendedService)
- JdbcOpportunityRepository: Opportunity SQL (was in CrmService/CrmExtendedService)
- JdbcPipelineRepository: Pipeline SQL (was in CrmService)
- JdbcActivityRepository: Activity SQL (was in CrmService/CrmExtendedService)
- JdbcCustomFieldRepository: Custom Field SQL (was in CrmExtendedService)
- JdbcTimelineProjectionRepository: Timeline SQL (was in CrmService/CrmExtendedService)

## Legacy Services Status
CrmService and CrmExtendedService remain as compatibility facades for v1 endpoints.
They contain NO new logic — all new modular code is in crm.{module} packages.
Removal is tracked for future PRs after v1→v2 migration is complete.
