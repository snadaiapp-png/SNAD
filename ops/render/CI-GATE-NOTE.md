# Protected CI Gate Note

`main` protection requires these status contexts before merge/release certification:

```text
Build Next.js Web
provenance
CRM Integration Tests
Maven Test Suite
CRM Deployment Readiness
Post-Merge Verification
Verify 8 tables, 26 indexes, and tenant isolation
```

The Clean-Room branch must not be merged or certified merely because its dedicated control-plane audit is clean. All protected contexts must resolve successfully on the final integration SHA according to GitHub branch protection.

Current Clean-Room dedicated audit evidence:

```text
inventory_outcome=success
unexpected_production_writers=0
secret_candidate_files=0
release_contracts=48_PASS_1_FAIL
remaining_contract_failure=canonical_render_rollback_missing
```

This document does not waive or alter branch protection.
