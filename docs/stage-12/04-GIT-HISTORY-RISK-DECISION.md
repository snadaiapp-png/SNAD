# Stage 12 — Git History Risk Decision

**Date**: 2026-07-08
**Decision**: Option C — Keep history as-is with token revocation

---

## Background

The exposed token value was committed to the repository in commit `d4b3829`
(PR #372, Stage 11 docs). The token was subsequently removed from the current
tree in PR #374 (commit `9dfdeba`). However, the token value remains in the
git history at commit `d4b3829`.

## Options Considered

### Option A: Accept Historical Risk with Revocation Evidence

Accept that the token exists in git history but rely on external revocation
to mitigate the risk. No history rewrite.

### Option B: Execute History Rewrite

Rewrite git history to remove the token from all commits. Requires:
- Separate governance approval
- Force push to main (disruptive)
- All collaborators re-clone
- Risk of breaking CI/CD, Vercel deployments, and references

### Option C: Keep History As-Is with Revocation and Usage Prevention

Keep the git history unchanged. Rely on:
1. Token revocation by the account owner (external action)
2. Permanent compromise declaration (token treated as invalid regardless)
3. No republishing of the token value
4. Secret scan continues to pass on current tree

## Decision: Option C

**Selected**: Option C — Keep history as-is with revocation and usage prevention.

### Justification

1. **Token is permanently compromised**: Any exposed token must be treated
   as compromised regardless of repository state. History rewrite does not
   change this fact.

2. **External revocation is the real fix**: The token owner revoking the
   token from GitHub settings is the definitive remediation. History rewrite
   is unnecessary if the token is revoked.

3. **History rewrite is disruptive**: Force-pushing to main would:
   - Break Vercel auto-deploy
   - Require all collaborators to re-clone
   - Risk breaking CI/CD references
   - Invalidate existing deployment SHA references

4. **Current tree is clean**: The token is NOT in the current tree. Secret
   scan passes. The only exposure is in historical commits, which are
   immutable unless rewritten.

5. **Risk is accepted**: The project owner accepts the residual risk per
   SANAD-ST08-GOV-AMENDMENT-002.

## Risk Assessment

```
Current tree: CLEAN (token not present)
Git history: Token present in commit d4b3829 (historical)
  - Commit is in the public repository history
  - Anyone with repo access can find it via git log -p
  - However, token is being revoked by owner
  - Token scope was limited (repo collaboration only)
  - Token is not a platform/infrastructure secret

Residual risk: LOW (with token revocation)
  - If token is revoked: risk becomes ZERO
  - If token is not revoked: risk remains LOW (limited scope)
```

## Conditions

1. **Token revocation is mandatory**: The account owner MUST revoke the token.
2. **No republishing**: The token value must not be republished in any artifact.
3. **Secret scan must pass**: Current tree must remain clean.
4. **History rewrite deferred**: History rewrite may be considered in a future
   stage if deemed necessary by separate governance approval.

## Future Option

If a future security audit determines that history rewrite is necessary,
a separate governance amendment (e.g., SANAD-ST08-GOV-AMENDMENT-003) must
be issued to approve the rewrite. Until then, Option C stands.

## Decision Record

```
Decision: Option C — Keep history as-is
Decision maker: snadaiapp-png (Project Owner)
Date: 2026-07-08
Reference: SANAD-ST08-GOV-AMENDMENT-002
Conditions: Token revocation by owner is mandatory
History rewrite: NOT APPROVED at this time
```
