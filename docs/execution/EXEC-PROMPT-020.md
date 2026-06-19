# EXEC-PROMPT-020

Membership to User association service and REST operations.

Implemented rules:
- Same-tenant association only.
- Matching normalized email required.
- Same user operation is idempotent.
- Removed memberships and duplicate associations are rejected.
- Association can be removed without deleting membership history.
- User memberships can be listed inside tenant scope.

Authentication and RBAC remain out of scope.
