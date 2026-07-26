# Remediation Retest and Closure Procedure

## Retest entry criteria

- The remediation is merged, deployed and identified by exact SHA and deployment/configuration version.
- The finding owner supplies a non-sensitive remediation reference and regression scope.
- Critical/high findings are never self-closed by the implementation team.
- The independent assessor confirms the retest target matches the assessed release.

## Required disposition

- Every critical/high finding must be `CLOSED` with `retest_status=PASS` and independent retest evidence.
- Medium/low findings may remain only as `RESIDUAL_RISK_ACCEPTED` with a named owner, treatment, future review date and at least two approval evidence records.
- A failed or partial retest returns the finding to a non-terminal state.
- Material regressions create a new finding and block closure.

## Final sequence

1. Reconcile the findings register with the manifest summary.
2. Complete every coverage case with evidence.
3. Verify every local evidence digest and restricted external reference.
4. Obtain distinct assessor, Security Governance and Project Owner approvals after assessment completion.
5. Merge the completed package to `main`.
6. Run protected closure validation with the exact assessed release SHA.
7. Publish a dated closure decision and update current-status authorities in one governed change.

Until all seven steps pass, REM-P0-006 remains `OPEN / NOT_READY` and broad commercial go-live remains `NOT_APPROVED`.
