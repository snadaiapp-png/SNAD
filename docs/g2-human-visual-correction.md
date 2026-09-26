# G2 Human Visual Correction

This corrective branch exists because human visual review rejected the previous G2 preview despite the automated visual workflow reporting PASS.

Root cause: the visual assertion treated the table empty-state `<tr>` as representative product data. The correction must fail closed unless actual G2 product rows are rendered during HRM Human Preview.

Governance remains unchanged: PostgreSQL Direct only, RLS/RBAC/tenant isolation preserved, no BYPASSRLS, no Flyway history mutation, no manual production deployment, and no release authorization until exact-head CI, independent approval, and human visual acceptance pass.
