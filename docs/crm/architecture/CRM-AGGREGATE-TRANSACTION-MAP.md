# CRM Aggregate Transaction Map

| Aggregate | Transaction Owner | Repositories | Concurrency | Audit |
|---|---|---|---|---|
| Account | AccountUseCases | AccountRepository | Atomic version check in SQL UPDATE | AuditPort |
| Contact | ContactUseCases | ContactRepository | Atomic version check in SQL UPDATE | AuditPort |
| Lead | LeadUseCases | LeadRepository | Atomic version check in SQL UPDATE | AuditPort |
| Pipeline | OpportunityUseCases | PipelineRepository | Atomic version check in SQL UPDATE | AuditPort |
| Opportunity | OpportunityUseCases | OpportunityRepository | Atomic version check in SQL UPDATE | AuditPort |
| Activity | ActivityUseCases | ActivityRepository | Atomic version check in SQL UPDATE | AuditPort |
| CustomField | ConfigurationUseCases | CustomFieldRepository | Atomic version check in SQL UPDATE | AuditPort |
| Timeline | QueryUseCases (read-only) | TimelineProjectionRepository | N/A (read-only) | N/A |

## Transaction Pattern
All mutations follow: `UPDATE ... SET version=version+1 WHERE tenant_id=:t AND id=:id AND version=:expected`
If affected rows = 0, throw CRM_CONCURRENCY_CONFLICT (HTTP 412).
