# SNAD VISUAL MIGRATION LEDGER

Governance: the SNAD Module Visual Contract (2026-10-02-snad-module-visual-contract.md) is mandatory for every NEW module/surface from Task 6 onward. Legacy modules below migrate via their own future authorized tasks; until migrated, any change touching them must not increase visual drift (no new independent identity, no new hardcoded values, no new shell patterns).

| Module | Route root | Classification (2026-10-02) | Shell | Notes |
|--------|-----------|------------------------------|-------|-------|
| CRM | /crm | COMPLIANT (Task 6) | shared SnadModuleShell primitives migrated from crm-shell.tsx | reference module; visual regression pinned |
| HR | /hr | COMPLIANT (Task 6) | HrWorkspace wraps SnadModuleShell | unified in G3 Task 6 |
| Workflow | /workflow | LEGACY | module-local | no structural change in Task 6; drift-frozen |
| Users | /users | LEGACY | module-local | drift-frozen |
| Subscriptions/SCP | /subscriptions, /scp | LEGACY | module-local | drift-frozen |
| Ecommerce/POS | (none found) | NO_UI | — | no web surfaces at baseline |
| ERP | (none found) | NO_UI | — | no dedicated web module surfaces at baseline |
| Accounting | (none found) | NO_UI | — | no dedicated web module surfaces at baseline |
| Partner | (none found) | NO_UI | — | no web surfaces at baseline |
| Executive/Workspace | /workspace, /executive | LEGACY | shell/ components | cross-module landing shells; drift-frozen |

Inventory method: `apps/web/app/*` top-level route directories enumerated at baseline dc6d36dc5; classification per presence of product shell + styles. NO_UI = no module web route group found.
