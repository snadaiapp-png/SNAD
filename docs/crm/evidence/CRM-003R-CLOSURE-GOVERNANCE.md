# CRM-003R Closure Governance

CRM-003R remains open until the final PR head is immutable and all required
checks are `completed/success`. Documentation, branch creation, compilation, or
partial test success cannot independently close CRM-G2.

A final merge must use expected-head protection. After merge, the control issue
must be reconciled with the exact head, merge SHA, workflow run IDs, and explicit
zero values for failures, pending checks, and skipped critical tests.
