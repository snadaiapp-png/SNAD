# REM-P0-006 Controlled Assessment Trigger

This file triggers the fail-closed root assessment only after the corrective implementation is merged to `main`.

Create branch `rem-p0-006-assessment-execution-v2` from the exact accepted `main` SHA, then update this file using a commit whose message contains `[run-assessment]`.

The workflow freezes `main` at startup and invalidates itself if `main` moves before completion.
