# Production Credential Exposure Incident — 2026-08-16

## Severity

`CRITICAL`

## Scope

During SNAD Backend Clean-Room R1 forensic review, an executable GitHub Actions workflow was found to contain plaintext production PostgreSQL authentication material and direct production admin/user mutation logic.

The secret values are intentionally not reproduced in this document.

Affected executable path at the forensic baseline:

```text
.github/workflows/direct-db-insert.yml
```

## Verified Risk

The workflow was capable of:

- connecting directly to the production PostgreSQL endpoint;
- authenticating using a credential embedded in repository source;
- updating or creating a privileged application user;
- creating/assigning privileged roles and capabilities;
- committing those changes directly to production data.

The repository is public, therefore the embedded credential must be treated as compromised regardless of whether the workflow was recently executed.

## Immediate Mitigation Performed

- The secret-bearing workflow was removed from `infra/backend-clean-room-v1` instead of being copied into the legacy workflow archive.
- No secret value was copied into Clean-Room documentation, PR comments, or new workflow files.
- No production database mutation was performed by the Clean-Room work.
- No Git history rewrite or force-push was attempted.

## Mandatory External Remediation

Before Green Render service certification:

```text
PRODUCTION_DB_CREDENTIAL_ROTATION=REQUIRED
RENDER_API_TOKEN_ROTATION=REQUIRED_IF_EXPOSED_TO_ANY_NON-SECRET CHANNEL
GREEN_SERVICE_USE_OF_OLD_EXPOSED_CREDENTIALS=FORBIDDEN
```

Deleting the source file is not sufficient because Git history retains prior content. The correct control is credential rotation plus removal from current executable source.

## Follow-up Security Gates

1. Search executable workflows and production scripts for additional plaintext credentials/tokens/private keys.
2. Remove or sanitize every finding from the current tree.
3. Rotate affected credentials at their authoritative provider.
4. Update only protected secret stores; never commit replacement values.
5. Verify old credentials are rejected before Green certification.
6. Run repository secret scanning before R6.

## Status

```text
SECRET_BEARING_WORKFLOW_REMOVED_FROM_CLEAN_ROOM_BRANCH=PASS
MAIN_HEAD_REMEDIATION=PENDING_MERGE
CREDENTIAL_ROTATION=BLOCKED_EXTERNAL_ACTION
HISTORY_REWRITE=NOT_PLANNED
GREEN_CERTIFICATION=BLOCKED_UNTIL_ROTATION
```
