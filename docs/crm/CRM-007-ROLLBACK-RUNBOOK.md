# CRM-007 Rollback Runbook

## Objective

Return application traffic to the last verified CRM-006 release without deleting
or reversing CRM-007 data. CRM-007 migrations are additive and forward-only.

## Compatibility contract

CRM-007 deliberately retains and maintains:

- `crm_account_addresses`
- `crm_accounts.primary_email`
- `crm_accounts.primary_phone`
- `crm_contacts.primary_email`
- `crm_contacts.primary_phone`
- `crm_contacts.account_id`

The CRM-006 application can therefore read its existing projections after an
application rollback. New canonical tables remain dormant until CRM-007 is
redeployed.

## Pre-deployment evidence

Capture tenant-scoped counts and checksums for:

```sql
SELECT tenant_id, COUNT(*) FROM crm_account_addresses GROUP BY tenant_id;
SELECT tenant_id, COUNT(*) FROM crm_party_addresses GROUP BY tenant_id;
SELECT tenant_id, COUNT(*) FROM crm_communication_methods GROUP BY tenant_id;
```

For every tenant, verify that every canonical Account address has a matching
`crm_account_addresses` row by `(tenant_id,id)`, and that preferred active Email
and Phone/Mobile values match the corresponding legacy primary projection.

## Rollback procedure

1. Stop CRM-007 writes or place the CRM mutation surface in maintenance mode.
2. Record the CRM-007 deployment ID, Git SHA and database migration version.
3. Promote the last verified CRM-006 application deployment.
4. Do **not** execute Flyway repair, clean, undo, or manual table deletion.
5. Verify `/`, `/crm/accounts`, `/crm/contacts`, backend status and BFF 401.
6. Run the compatibility queries below.
7. Re-enable CRM-006 traffic only after legacy projections and tenant isolation pass.

## Compatibility verification

```sql
-- Canonical Account addresses missing from the CRM-005 projection: must be zero.
SELECT COUNT(*)
FROM crm_party_addresses canonical
LEFT JOIN crm_account_addresses legacy
  ON legacy.tenant_id=canonical.tenant_id AND legacy.id=canonical.id
WHERE canonical.owner_type='ACCOUNT' AND legacy.id IS NULL;

-- Cross-tenant owner references: must be zero.
SELECT COUNT(*)
FROM crm_party_addresses address
LEFT JOIN crm_accounts account
  ON account.tenant_id=address.tenant_id AND account.id=address.account_id
LEFT JOIN crm_contacts contact
  ON contact.tenant_id=address.tenant_id AND contact.id=address.contact_id
WHERE (address.owner_type='ACCOUNT' AND account.id IS NULL)
   OR (address.owner_type='PERSON' AND contact.id IS NULL);

-- Preferred communication projections that do not match legacy fields: must be zero.
SELECT COUNT(*)
FROM crm_communication_methods method
JOIN crm_accounts account
  ON account.tenant_id=method.tenant_id AND account.id=method.account_id
WHERE method.owner_type='ACCOUNT' AND method.preferred=TRUE AND method.status='ACTIVE'
  AND ((method.method_type='EMAIL' AND account.primary_email<>method.display_value)
    OR (method.method_type IN ('PHONE','MOBILE') AND account.primary_phone<>method.display_value));
```

## Redeployment after rollback

Before redeploying CRM-007:

- replay the exact-head CI and PostgreSQL upgrade tests;
- confirm no manual changes were made to Flyway history;
- confirm compatibility mismatch queries remain zero;
- promote only the same verified CRM-007 merge SHA or a newer reviewed fix.

## Prohibited actions

- dropping CRM-007 tables as an emergency rollback;
- restoring an older database snapshot while retaining newer application traffic;
- editing canonical or legacy projections independently;
- using Flyway `clean` or manual `flyway_schema_history` changes in Production.
