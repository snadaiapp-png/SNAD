# REM-P0-002 Closure Gate

REM-P0-002 may be closed only when all conditions below are true:

- exact remediation SHA deployed to Production;
- unauthenticated BFF `/api/v1/auth/me` returns `401` consistently;
- protected synthetic identity configured;
- hourly BFF auth/session synthetic passes login, authenticated `/me`, refresh rotation, session restoration, logout and post-logout rejection;
- 72 consecutive hourly successful cycles after the final relevant deployment;
- no unexplained BFF/auth `502`, `503` or `504` in the window;
- lockout and audit evidence attached;
- SLO and error-budget report attached;
- Identity, Operations and Project Owner acceptance recorded;
- REM-P0-001 tunnel dependency removed or formally accepted as residual risk.

Any failed or missing cycle resets the consecutive-success count to zero. The finding remains open until the complete evidence set is recorded in Issue #516.
