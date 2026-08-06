# Workstream 1 — Branch Protection Remediation

| Field | Value |
|-------|-------|
| Date | 2026-07-30 |
| Author | ZCode Agent |
| Status | COMPLETE |

## Objective

Add the `CRM Integration Tests` job to Required Status Checks on `main` to ensure CRM integration tests must pass before merge.

## Before (Configuration)

```json
{
  "strict": true,
  "contexts": ["Build Next.js Web", "provenance"]
}
```

| Check | Required | App ID |
|-------|----------|--------|
| Build Next.js Web | YES | 15368 |
| provenance | YES | 15368 |

## After (Configuration)

```json
{
  "strict": true,
  "contexts": ["Build Next.js Web", "provenance", "CRM Integration Tests"]
}
```

| Check | Required | App ID |
|-------|----------|--------|
| Build Next.js Web | YES | 15368 |
| provenance | YES | 15368 |
| CRM Integration Tests | YES | 15368 |

## Validation

| Criterion | Status | Evidence |
|-----------|--------|----------|
| `CRM Integration Tests` is now required | ✅ | API response shows it in `contexts` |
| Existing checks unchanged | ✅ | `Build Next.js Web` and `provenance` still present |
| Strict mode enabled | ✅ | `strict: true` |
| Merge blocked if CRM tests fail | ✅ | Required check will fail PR |

## Impact

- **Before:** PR #821 merged despite CRM Integration Tests failure (not required)
- **After:** Any PR with failing CRM Integration Tests cannot merge

## Other Branch Protection Settings (Unchanged)

| Setting | Value |
|---------|-------|
| Enforce admins | false |
| Required approvals | 0 |
| Dismiss stale reviews | true |
| Allow force pushes | false |
| Allow deletions | false |
| Required linear history | false |
| Required conversation resolution | false |
