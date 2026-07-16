# Deprecated NVD Workflow Archive

**Archived:** 2026-07-17  
**Reason:** Deprecated workflow definitions under `.github/workflows` generated failed GitHub Actions runs with `No jobs were run` after ordinary pushes to `main`.

## Removed workflow definitions

### `nvd-database-maintenance.yml`

- Former name: **NVD Database Maintenance (DEPRECATED)**
- Superseded by: `.github/workflows/nvd-snapshot-publisher.yml`
- Direct ephemeral NVD database builds on GitHub-hosted runners remain prohibited.
- Use the approved snapshot publisher on the required self-hosted runner and configured storage backend.

### `nvd-feed-mirror-publisher.yml`

- Former name: **NVD Feed Mirror Publisher (DEPRECATED)**
- Superseded by: `.github/workflows/nvd-bulk-feed-mirror-publisher.yml`
- The legacy feed publisher must not be used for scheduling or acceptance evidence.

## Governance decision

Deprecated workflows must not remain in `.github/workflows` with empty triggers. GitHub still registers files in that directory as workflow definitions, which can produce misleading failed runs and contaminate branch/commit status.

Historical rationale is preserved here. Any future restoration requires a new authorized execution item, active triggers, valid jobs, owner approval, and passing CI evidence.
