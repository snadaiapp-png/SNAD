# CRM-022 — Security Report

| Field | Value |
|-------|-------|
| Changed file | `.github/workflows/ci.yml` (additive `crm` job) |
| Permissions model | Workflow-level `permissions: contents: read` (unchanged) |

## 1. Permission review

- The `crm` job introduces **no new permissions**. It relies solely on the
  workflow-level `permissions: contents: read`.
- No `pull_request_target` trigger is used (the job runs on the safer
  `pull_request` event), so untrusted PR code is checked out and executed in
  the low-privilege context — consistent with the existing `test` job.

## 2. Secret review

- **Zero** `${{ secrets.* }}` expressions are referenced anywhere in the `crm`
  job (or anywhere in `ci.yml`).
- The only `env:` values are `TESTCONTAINERS_RYUK_DISABLED` and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` — non-secret infrastructure flags
  identical to the existing `test` job.

## 3. Supply-chain review

- Actions pinned to major versions (`@v4`) — same as the existing `test` job.
  (Note: `web-ci.yml` uses `@v6` for some actions; CRM-022 intentionally
  matches the **`ci.yml`** `test` job versions for backend-test consistency.)

## 4. Code injection

- No untrusted interpolation into `run:` blocks. `${{ github.* }}` is not used
  in any shell command; all shell content is static.

## 5. Verdict

No security regression, no secret exposure, least-privilege preserved.
