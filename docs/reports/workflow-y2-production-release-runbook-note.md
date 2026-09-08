# Workflow Y2 Production Release Runbook Note

The canonical deployment remains `.github/workflows/production-release.yml`. The production orchestrator introduced by this change may only dispatch that canonical workflow after immutable image publication succeeds for the exact current `main` SHA and the merge commit carries the explicit `PRODUCTION-RELEASE-AUTHORIZED` marker.
