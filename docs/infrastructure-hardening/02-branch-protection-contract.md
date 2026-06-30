# SNAD Branch Protection Contract

Branch: infra/02a-debt-closure | Date: 2026-06-30

## Recommended Settings for `main`

| Setting | Value |
|---|---|
| Require pull request before merging | YES |
| Required approvals | 1 (minimum) |
| Dismiss stale approvals on push | YES |
| Require review from CODEOWNERS | When CODEOWNERS file exists |
| Require status checks | YES |
| Required check name | `quality-gate` |
| Require branches up to date | YES |
| Require conversation resolution | YES |
| Require signed commits | RECOMMENDED |
| Require linear history | RECOMMENDED |
| Allow force pushes | NO |
| Allow deletions | NO |
| Block creations | NO |
| Lock branch | NO |
| Admin bypass | DISABLED or AUDITED |

## Ruleset `min` (existing)

The existing ruleset `min` (id=17903112) requires `required_approving_review_count: 0` (corrected in previous work). This should be reconciled with branch protection settings.

## CODEOWNERS

No CODEOWNERS file exists. Creating one requires knowing GitHub usernames/team names. Recommend creating `.github/CODEOWNERS` when team structure is defined.

## Important Notes

- Do NOT change GitHub settings automatically
- This document is a contract/recommendation only
- Settings should be applied manually after quality-gate is validated on remote
