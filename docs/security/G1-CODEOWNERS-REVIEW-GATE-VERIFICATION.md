# G1 CODEOWNERS review-gate verification (non-production)

This is a verification plan, **not** evidence that branch protection is enabled or that a security reviewer has approved a change.

## Prerequisites

- Administrator enables `dismiss_stale_reviews=true` and `require_code_owner_reviews=true` for `main`, retaining existing mandatory status checks, one required approving review, and enforced administrator protection.
- Record the actual protected-branch API response as evidence. Do not infer the current settings from the historical C3 table in `g1-governance-controls.md`.
- An eligible code owner distinct from the PR author must perform the approval. A self-review is not independent approval.

## Verification sequence

1. Open a PR against `main` that changes a path covered by `.github/CODEOWNERS`, such as this file under `docs/security/`.
2. Verify via GitHub's PR review requests and branch mergeability that the code owner is requested and merging is blocked pending approval. Capture the exact PR head SHA and branch-protection response.
3. Obtain an eligible code-owner approval from someone other than the author; recheck mergeability and all required CI checks. **Do not merge this PR merely to demonstrate the control.**
4. On a separate test PR or an explicitly authorized test update, push an additional harmless commit and verify the previous approval is dismissed as stale. Record the pre- and post-push head SHAs and review states.
5. Confirm branch admins remain subject to enforcement; do not bypass protections for validation.

## Acceptance and evidence

- `CODEOWNERS_REVIEW_REQUIRED`: PASS only when branch protection reports `require_code_owner_reviews=true` **and** a real protected-path PR is blocked for missing eligible owner approval.
- `STALE_APPROVAL_DISMISSED`: PASS only with observable review state change after a new commit.
- `REQUIRED_CHECKS_PRESERVED`: PASS only if the before/after required contexts match the approved baseline.
- `INDEPENDENT_G1_SIGNOFF`: separate human authorization; never inferred from this test.

Until the above observations are documented: **UNVERIFIED**.

Existing safety posture remains `P0_SECURITY_HOLD=ACTIVE`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`.
