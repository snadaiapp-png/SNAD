# Exact Main Invariant

The orchestrator must compare the image publication `head_sha` with `refs/heads/main` immediately before dispatch and fail closed on any mismatch.
