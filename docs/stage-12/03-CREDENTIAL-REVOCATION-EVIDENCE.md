# Stage 12 — Credential Revocation Evidence

**Date**: 2026-07-08
**Related Issues**: #367 (CLOSED), #373 (CLOSED)

---

## Credential Incident Summary

```
Incident: External reviewer token exposed in chat
Issue: #367 (CLOSED — completed)
Follow-up: #373 (CLOSED — completed)
Classification: Historical non-active exposure (Category 3)
```

## Credential Details (No Value Republished)

```
Credential type: GitHub Personal Access Token (classic)
Owner account: abdulrhmansenan1985-creator (external reviewer)
Scope: Repository collaboration on snadaiapp-png/SNAD
Exposure vector: Shared in IM chat conversation
Usage: PR reviews on #358, #359, #364; collaboration setup
```

**Note**: The actual token value is NOT republished in this document per the
credential exposure rule. The token value has been removed from the repository
current tree (PR #374).

## Revocation Status

```
Repository current tree: REMEDIATED (token removed)
  - PR #374 merged (SHA 9dfdeba)
  - gitleaks scan: PASS (0 findings)
  - SNAD scanner: PASS (0 findings)

External revocation by token owner: PENDING
  - Account: abdulrhmansenan1985-creator
  - Required action: Revoke token from GitHub settings
  - Path: Settings → Developer settings → Personal access tokens → Revoke
  - Status: NOT YET CONFIRMED
```

## Evidence of Remediation

```
1. Issue #367: CLOSED (token exposure tracked)
2. Issue #373: CLOSED (secret scan failure classified and resolved)
3. PR #374: MERGED (token removed from current tree)
4. Secret scan: PASS (confirmed in PR #374 CI)
5. Security baseline: PASS
6. Token value not present in current tree: VERIFIED
```

## Permanent Compromise Rule

```
Any token once exposed is permanently compromised.
Repository removal is necessary but NOT sufficient.
The token MUST be revoked by the account owner regardless of repository state.
The token value MUST NOT be republished in any future artifact.
```

## Residual Risk Acceptance

```
Risk: The exposed token may still be active until revoked by the account owner.
Mitigation:
  1. Token removed from repository current tree
  2. Token scope was limited to repo collaboration
  3. Token was not a platform/infrastructure secret
  4. Token did not have access to production systems

Owner risk acceptance: ACCEPTED per SANAD-ST08-GOV-AMENDMENT-002
```

## Required External Action

```
Action: Revoke the exposed token
Responsible: abdulrhmansenan1985-creator (token owner)
Deadline: IMMEDIATE
Verification: Project owner (snadaiapp-png) must confirm revocation

Until revocation is confirmed:
  - Residual risk: ACCEPTED
  - Security status: CONTROLLED (not fully closed)
```
