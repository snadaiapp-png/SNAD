#!/usr/bin/env bash
# ============================================================
# SANAD Platform — Final Closure Execution Helper
# ============================================================
# This script automates the operator-side execution steps
# required by the executive order (push, PR, merge, run, verify).
#
# The sandbox environment cannot push to GitHub directly;
# this script must be run by the operator with GitHub credentials.
#
# Usage:
#   bash scripts/final-closure.sh
#
# Prerequisites:
#   - gh CLI installed and authenticated (gh auth login)
#   - On the SNAD repository: snadaiapp-png/SNAD
# ============================================================
set -euo pipefail

REPO="snadaiapp-png/SNAD"
HEALTH_BRANCH="hotfix/health-verification-hardening"
HEALTH_COMMIT="60c13974c6fee5c51aca66630f8777209f017eda"
COMMERCIAL_BRANCH="fix/commercial-go-live-hardening-20260705"
COMMERCIAL_HARDENED_COMMIT="c8d90b7d6119c7bfa16baed23f8f4dbd5609920a"
PR_244_NUMBER="244"

echo "============================================================"
echo "SANAD Platform — Final Closure Execution"
echo "============================================================"
echo "Health Branch:     $HEALTH_BRANCH"
echo "Health Commit:     $HEALTH_COMMIT"
echo "Commercial Branch: $COMMERCIAL_BRANCH"
echo "Commercial Commit: $COMMERCIAL_HARDENED_COMMIT"
echo "PR #244 Number:    $PR_244_NUMBER"
echo "============================================================"

# ── Authentication check ────────────────────────────────────
if ! gh auth status >/dev/null 2>&1; then
  echo "❌ GitHub CLI not authenticated."
  echo ""
  echo "Authenticate first:"
  echo "  gh auth login"
  echo ""
  echo "Or set GH_TOKEN env var:"
  echo "  export GH_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx"
  exit 1
fi

# ── Step 1: Push health branch ─────────────────────────────
echo ""
echo "→ [Step 1/8] Pushing $HEALTH_BRANCH..."
git push -u origin "$HEALTH_BRANCH"

# ── Step 2: Open Health PR ─────────────────────────────────
echo ""
echo "→ [Step 2/8] Opening Health PR..."
HEALTH_PR_URL=$(gh pr create \
  --repo "$REPO" \
  --base main \
  --head "$HEALTH_BRANCH" \
  --title "ci(health): enforce production health contract and audit evidence" \
  --body "Enforces complete Executive Health snapshot validation, Identity B read/write denial, RUN_DIAGNOSTICS response contract, correlation-ID audit verification, concurrency protection, secret-only credentials, and fail-closed production gates.

Scope intentionally isolated from PR #244 (commercial-go-live).

Validation:
- YAML PASS: 45/45 workflow files
- Gitleaks: 0 active credential findings
- Workflow Security Scanner: 0 critical findings on health-production-verification.yml
- git diff --check: PASS
- 5 files only: health-production-verification.yml + SANAD-GO-VERIFICATION-REVIEW.md + 3 scripts")

HEALTH_PR_NUMBER=$(echo "$HEALTH_PR_URL" | grep -oE '[0-9]+' | tail -1)
echo "✅ Health PR #$HEALTH_PR_NUMBER: $HEALTH_PR_URL"

# ── Step 3: Push commercial hardened commit (force-with-lease) ─
echo ""
echo "→ [Step 3/8] Pushing commercial branch with hardened commit..."
git push --force-with-lease origin "$COMMERCIAL_BRANCH"

# ── Step 4: Mark PR #244 ready ──────────────────────────────
echo ""
echo "→ [Step 4/8] Marking PR #$PR_244_NUMBER as ready..."
gh pr ready "$PR_244_NUMBER" --repo "$REPO" || echo "  (already ready or in draft mode)"

# ── Step 5: Watch Health PR checks ─────────────────────────
echo ""
echo "→ [Step 5/8] Watching Health PR checks..."
gh pr checks "$HEALTH_PR_NUMBER" --repo "$REPO" --watch

# ── Step 6: Merge Health PR (squash) ───────────────────────
echo ""
echo "→ [Step 6/8] Merging Health PR (squash)..."
HEALTH_MERGE_SHA=$(gh pr merge "$HEALTH_PR_NUMBER" \
  --repo "$REPO" \
  --squash \
  --delete-branch \
  --json mergeCommit \
  --jq '.mergeCommit.oid')
echo "✅ Health Merge SHA: $HEALTH_MERGE_SHA"

# ── Step 7: Rebase commercial on latest main, re-run checks ─
echo ""
echo "→ [Step 7/8] Rebasing commercial branch on latest main..."
git fetch origin --prune
git checkout "$COMMERCIAL_BRANCH"
git rebase origin/main
git push --force-with-lease origin "$COMMERCIAL_BRANCH"

echo "  Watching commercial PR #$PR_244_NUMBER checks..."
gh pr checks "$PR_244_NUMBER" --repo "$REPO" --watch

# ── Step 8: Merge PR #244 (squash) ─────────────────────────
echo ""
echo "→ [Step 8/8] Merging PR #$PR_244_NUMBER (squash)..."
COMMERCIAL_MERGE_SHA=$(gh pr merge "$PR_244_NUMBER" \
  --repo "$REPO" \
  --squash \
  --delete-branch \
  --json mergeCommit \
  --jq '.mergeCommit.oid')
echo "✅ Commercial Merge SHA: $COMMERCIAL_MERGE_SHA"

# ── Step 9: Capture final main SHA ─────────────────────────
echo ""
git checkout main
git pull --ff-only origin main
FINAL_MAIN_SHA=$(git rev-parse HEAD)
echo "✅ Final Main SHA: $FINAL_MAIN_SHA"

# ── Step 10: Run Executive Health Production Verification ──
echo ""
echo "→ [Step 10] Triggering Executive Health Production Verification..."
gh workflow run "Executive Health Production Verification" \
  --repo "$REPO" \
  --ref main
sleep 10

HEALTH_RUN_ID=$(gh run list \
  --repo "$REPO" \
  --workflow "Executive Health Production Verification" \
  --branch main \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId')
echo "  Health Run ID: $HEALTH_RUN_ID"

echo "  Watching health run..."
gh run watch "$HEALTH_RUN_ID" --repo "$REPO" --exit-status
echo "✅ Health run completed successfully"

# ── Step 11: Run Commercial Go-Live ────────────────────────
echo ""
echo "→ [Step 11] Triggering Commercial Go-Live..."
echo "  ⚠️  WARNING: This will execute production release gates."
echo "  Press Ctrl+C within 10 seconds to cancel..."
sleep 10

gh workflow run "SANAD Commercial Go-Live" \
  --repo "$REPO" \
  --ref main \
  -f confirm="COMMERCIAL-GO-LIVE" \
  -f release_sha="$FINAL_MAIN_SHA"
sleep 10

COMMERCIAL_RUN_ID=$(gh run list \
  --repo "$REPO" \
  --workflow "SANAD Commercial Go-Live" \
  --branch main \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId')
echo "  Commercial Run ID: $COMMERCIAL_RUN_ID"

echo "  Watching commercial run..."
gh run watch "$COMMERCIAL_RUN_ID" --repo "$REPO" --exit-status
echo "✅ Commercial run completed successfully"

# ── Step 12: Download artifacts and review security ────────
echo ""
echo "→ [Step 12] Downloading commercial artifacts for security review..."
mkdir -p /tmp/sanad-commercial-evidence
gh run download "$COMMERCIAL_RUN_ID" \
  --repo "$REPO" \
  --dir /tmp/sanad-commercial-evidence

python3 scripts/review_artifact_security.py /tmp/sanad-commercial-evidence
ARTIFACT_REVIEW=$?

if [ $ARTIFACT_REVIEW -ne 0 ]; then
  echo "❌ Artifact security review FAILED — sensitive data found"
  echo "  Per executive order §16, this requires:"
  echo "  - Artifact deletion"
  echo "  - Credential rotation"
  echo "  - Incident recording"
  echo "  - Workflow correction"
  echo "  - Re-run"
  exit 1
fi
echo "✅ Artifact security review: PASS"

# ── Final Report ───────────────────────────────────────────
echo ""
echo "============================================================"
echo "FINAL CLOSURE EVIDENCE"
echo "============================================================"
echo "Health PR Number:          #$HEALTH_PR_NUMBER"
echo "Health PR URL:             $HEALTH_PR_URL"
echo "Health Merge SHA:          $HEALTH_MERGE_SHA"
echo "Commercial PR Number:      #$PR_244_NUMBER"
echo "Commercial Merge SHA:      $COMMERCIAL_MERGE_SHA"
echo "Final Main SHA:            $FINAL_MAIN_SHA"
echo "Health Verification Run:   $HEALTH_RUN_ID"
echo "Commercial Go-Live Run:    $COMMERCIAL_RUN_ID"
echo "Artifact Security Review:  PASS"
echo "============================================================"
echo ""
echo "Final Decision: GO — CLOSED"
echo ""
echo "Next: Update SANAD-GO-VERIFICATION-REVIEW.md with the above"
echo "       evidence and open the closure-report PR per §18."
