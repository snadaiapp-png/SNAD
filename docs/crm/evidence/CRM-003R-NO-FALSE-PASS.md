# CRM-003R No-False-Pass Controls

- No `continue-on-error` is used in the corrective gate.
- Missing test reports fail the workflow.
- Fewer than five executed acceptance tests fail the workflow.
- Any failed, errored, or skipped acceptance test fails the workflow.
- The control issue and CRM-G2 remain open until exact-head verification and merge reconciliation.
