# CRM Idempotency Reconciliation Review

Final reviewed scope for PR #639:

- Flyway `20260721.2` restores the missing `crm_idempotency_records` relation without modifying migration history.
- `/crm` redirects at the HTTP routing layer before the authenticated application initializes.
- Nullable address and communication filters use explicit JDBC types and PostgreSQL casts for every repeated placeholder.
- The regression suite must pass on PostgreSQL before Production deployment.
- Production verification remains read-only; no Flyway repair or manual schema-history edit is authorized.
