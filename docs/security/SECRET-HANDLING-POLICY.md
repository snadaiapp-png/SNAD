# SANAD Secret Handling Policy

## Purpose

Prevent recurrence of token exposure and enforce least-privilege access across GitHub, Vercel, OpenAI Platform, cloud accounts, CI, and operational workflows.

## Non-negotiable rules

1. Do not share tokens, credentials, API keys, passwords, connection strings, private keys, recovery codes, or session cookies in chat, issues, pull requests, commits, logs, screenshots, tickets, or workflow output.
2. Use GitHub collaborator access, GitHub Apps, environment secrets, OIDC, or provider-native scoped access instead of shared personal tokens.
3. Use fine-grained, short-lived, repository-scoped credentials only when unavoidable.
4. Store secrets only in approved secret stores or protected CI environments.
5. Rotate immediately when exposure is suspected.
6. Record rotation confirmation without posting the secret value.

## Required access pattern

| Use case | Approved mechanism | Forbidden mechanism |
|---|---|---|
| Repository review | GitHub collaborator or review assignment | Sharing PATs |
| CI cloud access | OIDC or protected environment secrets | Static long-lived cloud keys in code |
| Vercel deployment | Vercel project/team integration | Browser-visible backend secrets |
| OpenAI usage | Environment variable or secret manager | Hard-coded API keys |
| Emergency access | Time-bound, documented, least privilege | Unscoped admin tokens |

## Rotation procedure

1. Revoke the exposed credential.
2. Confirm no active workflow still depends on it.
3. Reissue only if a business need remains.
4. Scope the replacement to the minimum repository, environment, and capability.
5. Set an expiration date.
6. Record the rotation in a security issue without including the secret.
7. Run repository secret scanning and workflow log review.

## Closure evidence

- Secret scanning passes.
- No exposed value appears in issues, PRs, commits, logs, or workflows.
- Rotation issue is closed by owner confirmation.
- Preventive controls are documented and linked to the remediation register.
