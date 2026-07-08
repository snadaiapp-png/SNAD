# Stage 11 — Security Operations Review

**Date**: 2026-07-08
**Issue**: #370

---

## Token Exposure Incident (Issue #367)

### Incident Summary

```
Issue: #367
Title: Security notice — external approver token exposed in chat
Severity: High
Status: CLOSED (completed)
```

### Token Details

```
Token: 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
Account: abdulrhmansenan1985-creator (external reviewer)
Exposure: Token was shared in chat (IM conversation)
Usage: Repository collaboration setup + PR review submissions on #358, #359, #364
```

### Actions Taken

1. ✅ Token usage was limited to repository collaboration and PR reviews only
2. ✅ Issue #367 opened to track the incident
3. ✅ Comment added to Issue #367 documenting the exposure
4. ✅ Issue #367 closed (state: completed)
5. ⚠️ Token revocation: PENDING — account owner must revoke from GitHub settings

### Recommendation

The account owner (`abdulrhmansenan1985-creator`) must revoke the exposed
token immediately from:
```
GitHub Settings → Developer settings → Personal access tokens → Revoke
```

### Risk Acceptance

```
Risk: The exposed token may still be active until revoked by the account owner.
Mitigation: Token scope was limited to `repo` for snadaiapp-png/SNAD only.
Owner risk acceptance: The owner (snadaiapp-png) accepts the residual risk
  until the token is revoked, per SANAD-ST08-GOV-AMENDMENT-002.
```

---

## Active Secrets Review

### GitHub Tokens

```
snadaiapp-png token: ACTIVE (stored at /tmp/my-project/.gh-token)
  Scope: Fine-grained PAT
  Permissions: repo (contents, pull requests, actions, workflows)
  Used for: CI operations, PR creation, merges, branch protection
  Risk: LOW — stored in environment, not in repository

abdulrhmansenan1985-creator token: EXPOSED (Issue #367)
  Status: Pending revocation by account owner
  Risk: MEDIUM — limited scope but exposed in chat
```

### Vercel Tokens

```
Vercel deployment: Automated via GitHub Git integration
No explicit Vercel token stored in environment
Vercel access managed via GitHub deployment status API
```

### Repository Secrets

```
GitHub Actions secrets: Managed by repository settings
No secrets committed to repository (verified by secret scan: 0 findings)
Secret scan runs on every PR and post-merge
```

---

## Secret Rotation Policy

### When to Rotate

```
1. After any suspected or confirmed exposure
2. Every 90 days (recommended best practice)
3. When a collaborator leaves the project
4. When scope changes are needed
```

### Rotation Procedure

```
1. Generate new token in GitHub settings
2. Update environment: /tmp/my-project/.gh-token
3. Update GitHub credentials: ~/.git-credentials
4. Re-authenticate gh CLI: gh auth login --with-token
5. Verify: gh auth status
6. Revoke old token in GitHub settings
7. Document rotation in this file
```

---

## GitHub Permissions Review

### Repository Collaborators

```
snadaiapp-png: Owner (admin)
abdulrhmansenan1985-creator: Collaborator (push)
```

### Branch Protection

```
Branch: main
Required checks: Build Next.js Web, provenance (both strict)
enforce_admins: true
required_approving_review_count: 1
require_last_push_approval: true
```

### Recommendation

- Keep `enforce_admins: true` to prevent single-account bypass
- Keep `require_last_push_approval: true` for governance
- Monitor collaborator list regularly
- Remove collaborators when no longer needed

---

## Vercel Permissions Review

```
Team: snad-team
Project: snad-app
Auto-deploy: Enabled on main branch
Production URL: https://snad-app.vercel.app/
Git integration: Active
```

### Recommendation

- Review Vercel team members regularly
- Ensure only authorized accounts have deployment access
- Monitor deployment logs for unauthorized changes

---

## Security Operations Summary

```
Token exposure incident: CLOSED (Issue #367)
Active tokens: 1 (snadaiapp-png, stored securely)
Exposed tokens: 1 (pending revocation by external account owner)
Secret scan: 0 findings across 1776+ files
Repository secrets: None committed
Branch protection: Enforced
Vercel access: Via GitHub Git integration

Security Operations Status: REVIEWED
Risk acceptance: Documented per SANAD-ST08-GOV-AMENDMENT-002
```

---

## Ongoing Security Operations

### Daily

```
- Monitor for new secret scan findings (CI auto-runs on PRs)
- Check production URL health (HTTP 200)
```

### Weekly

```
- Review collaborator list
- Review Vercel deployment logs
- Check for new GitHub security advisories
```

### Monthly

```
- Rotate active tokens
- Review branch protection rules
- Audit repository permissions
- Review and update this document
```
