# REM-P0-006 Controlled Assessment Trigger

This file is a controlled trigger for the fail-closed root assessment workflow.

The assessment job runs only when a commit that changes this file contains the exact marker `[run-assessment]` in its commit message, or when the workflow is dispatched manually.

Do not use the trigger before the corrective implementation is merged to `main`. Every run freezes the current `main` SHA and invalidates itself if `main` moves during execution.
