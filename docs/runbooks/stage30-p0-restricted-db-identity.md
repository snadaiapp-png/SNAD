# Stage 30 P0 — Restricted production PostgreSQL identity

Status: **P0 SECURITY HOLD**; payments **OFF**. This runbook does not authorize a release or a privilege change.

## Confirmed forensic baseline

GitHub Actions run [#38077848579](https://github.com/snadaiapp-png/SNAD/actions/runs/38077848579) authenticated through the configured Production pooler secrets with effective `current_user = postgres`. Sanitized evidence: `sanad=false, postgres=true, owner=true, bypass=true, superuser=false`. The protected RLS gate rejected it correctly. Connection and SSL succeeded.

## Operator-controlled secret cutover (approval required)

1. Confirm **existing** approved Supabase project and session-pooler host/port. Do not create a new project or change `crm_accounts` RLS policy.
2. Using an authorized DBA console, inspect `sanad`: `rolsuper=false`, `rolbypassrls=false`; verify it does not own `public.crm_accounts`, has only required grants, and is not a member of privileged roles. Keep migration owner/admin credentials separate from application runtime.
3. Confirm how Supabase Session Pooler maps its login form to PostgreSQL `current_user`. Often the pooler login is `sanad.<project-ref>` (not the literal `sanad`); **verify this for the actual project**. Do not infer identity from the login string.
4. Prepare a restricted role password, via secret manager/operator only. Do not commit it, transmit it in PR comments, or echo it in logs.
5. Coordinate Render backend `DATABASE_USERNAME`/`DATABASE_PASSWORD` and GitHub **Production** environment `PRODUCTION_DATABASE_USERNAME`/`PRODUCTION_DATABASE_PASSWORD`, keeping the approved JDBC URL host, database, SSL, and pooler mode consistent. Confirm effective **Render** runtime role independently; GitHub results do not certify Render identity. Protect availability with an operator-approved maintenance/rollback plan before any Render credential rotation/restart.
6. Manually dispatch [the protected P0 identity gate](https://github.com/snadaiapp-png/SNAD/actions/workflows/stage30-p0-runtime-identity.yml) on exact `main`, after merge and protected environment approval. The gate requires effective `current_user = sanad` and independently checks ownership, BYPASSRLS, superuser, enabled RLS and strict policy. Never use `SET ROLE` as a shortcut.
7. Execute authorized, isolated Tenant A/B and missing-context negative read/write tests using synthetic test records with cleanup, preserving minimal disclosure; capture job URL/SHA, assertions and reviewer signoff in [issue #1352](https://github.com/snadaiapp-png/SNAD/issues/1352).
8. Complete independent security and risk assessment. **Do not close P0 or enable payments merely because the identity gate passed.**

## Mandatory no-go / rollback

If effective role is `postgres`, any privileged role, unknown role, or Render identity cannot be verified: stop. Do not override the workflow, weaken RLS, force bypass, or deploy the frontend. Restore the last approved backend secret set if the coordinated change affects runtime readiness. Record the failure and keep `P0=HOLD`, `P1=HOLD`, payments OFF.
