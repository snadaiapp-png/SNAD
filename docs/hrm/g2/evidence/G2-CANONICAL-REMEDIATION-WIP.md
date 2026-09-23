# G2 Canonical Remediation — Execution Record

Base candidate: `992f987cab330885a23e8310055b0432394b1925`
Temporary verification branch: `fix/g2-canonical-remediation-992f987`

Scope:
- route authenticated G2 browser traffic through the canonical `apiClient`
- bind SELF operations to the authenticated HR employee identity
- enforce TEAM scope on direct reports
- preserve HR tenant-wide administrative reads only where the canonical capability permits them
- correct leave queue state semantics (`PENDING_MANAGER` / `PENDING_HR`)
- remove user-id-as-employment-id coupling
- make leave Create → Submit a real UI journey
- replace brittle E2E selectors and raw browser-side API fallbacks
- run targeted G2 verification before updating PR #1133 and before full CI

This record is WIP until fresh targeted CI evidence is attached. It is not engineering certification or merge authorization.
