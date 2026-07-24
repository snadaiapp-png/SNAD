# Remediation Retest and Closure Procedure

## Retest entry criteria

- The remediation is merged, deployed and identified by exact SHA and deployment/configuration version.
- The finding owner supplies a non-sensitive remediation reference and regression scope.
- Critical/high findings are never self-closed by the implementation team.
- The independent assessor confirms the retest target matches the candidate release.

## Required retest outcome

For every remediated finding, the assessor records the original test, remediation reference, retest date, target version, result and evidence IDs. A failed or partial retest returns the finding to `OPEN`. Material regression creates a new finding and blocks closure.

All critical and high findings require `CLOSED` plus `PASS`. Medium/low items may remain only through explicit residual-risk records containing owner, treatment, expiry/review date and the approvals required by the manifest.

## Final decision sequence

1. Reconcile the findings register counts with `assessment-manifest.json`.
2. Complete all coverage cases; unresolved `FAIL` or `NOT_STARTED` cases block closure.
3. Verify every evidence digest or restricted external reference.
4. Run the closure validator on the exact candidate SHA.
5. Obtain separate approvals from the independent assessor, Security Governance and Project Owner.
6. Publish a dated closure decision referencing the exact run, artifact and deployment identifiers.
7. Update all current-status authorities in one governed change.

Until all seven steps pass, the only valid state is:

```text
REM-P0-006: OPEN / NOT_READY
BROAD_COMMERCIAL_GO_LIVE: NOT_APPROVED
```
