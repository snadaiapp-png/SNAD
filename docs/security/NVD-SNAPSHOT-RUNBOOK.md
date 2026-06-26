# NVD Snapshot Runbook

**Status:** PROPOSED (EXEC-PROMPT-010R12E)
**Date:** 2026-06-26

---

## Daily Operations

### Check Snapshot Freshness
```bash
# Resolve latest verified snapshot
python3 scripts/security/nvd_snapshot_store.py latest --backend $NVD_SNAPSHOT_BACKEND

# Verify snapshot integrity
gh workflow run nvd-snapshot-integrity.yml
```

### Force Publish (if snapshot is stale)
```bash
# Dispatch the publisher manually
gh workflow run nvd-snapshot-publisher.yml
```

### Run R12B Acceptance
```bash
# R12B resolves the latest snapshot automatically — no NVD dispatch
gh workflow run r12b-acceptance-orchestrator.yml
```

## Routine Maintenance

### Check Publisher Health
1. Navigate to Actions → NVD Snapshot Publisher
2. Verify the last scheduled run succeeded
3. If failed, inspect logs for:
   - NVD API errors (524, 503)
   - Validation failures
   - Storage upload failures
4. Do NOT manually run the old NVD Database Maintenance workflow

### Check Storage Retention
The publisher automatically retains the last 14 verified snapshots.
To manually prune:
```bash
python3 -c "
import sys; sys.path.insert(0, '.')
from scripts.security.nvd_snapshot_store import get_backend
backend = get_backend('$NVD_SNAPSHOT_BACKEND')
deleted = backend.apply_retention_policy(keep=14)
print(f'Deleted {len(deleted)} old snapshots: {deleted}')
"
```

## Troubleshooting

### Publisher Fails: "self-hosted runner not found"
**Cause:** No runner registered with labels `[self-hosted, linux, x64, snad-nvd-publisher]`.

**Fix:**
1. Provision a Linux x64 machine
2. Install Java 21, Python 3.12, Maven, tar, zstd
3. Create `/var/lib/snad/dependency-check-data/` with write access
4. Register the runner:
   ```bash
   gh actions-runner create --labels self-hosted,linux,x64,snad-nvd-publisher
   ```

### Publisher Fails: "NVD_SNAPSHOT_BACKEND not set"
**Cause:** Repository variable not configured.

**Fix:**
```bash
gh variable set NVD_SNAPSHOT_BACKEND --body "s3"  # or "ghcr"
```

### Publisher Fails: "NVD API 524/503"
**Cause:** NVD infrastructure instability.

**Fix:** Wait for the next scheduled run (every 6 hours). Do NOT retry
manually — the canonical directory and latest pointer are preserved.

### OWASP/R12B Fails: "No verified NVD snapshot available"
**Cause:** No snapshot has been published yet, or all snapshots are > 48 hours old.

**Fix:**
1. Run the NVD Snapshot Publisher
2. Wait for it to complete
3. Retry OWASP or R12B

### OWASP Fails: "snapshot verification failed"
**Cause:** The downloaded snapshot's digest does not match the manifest.

**Fix:**
1. Run NVD Snapshot Integrity to verify independently
2. If integrity also fails, the snapshot is corrupt — run the publisher
3. If integrity passes but OWASP fails, check the download path

## Changing Storage Backend

### From S3 to GHCR
1. Set `NVD_SNAPSHOT_BACKEND=ghcr`
2. Set `NVD_SNAPSHOT_GHCR_OWNER=snadaiapp-png`
3. Set `NVD_SNAPSHOT_GHCR_REPO=snad-nvd-data`
4. Run the publisher — it will bootstrap a new snapshot on GHCR
5. Old S3 snapshots remain accessible until manually deleted

### From GHCR to S3
1. Set `NVD_SNAPSHOT_BACKEND=s3`
2. Set `NVD_SNAPSHOT_BUCKET=<bucket>`
3. Set `NVD_SNAPSHOT_PREFIX=snad-nvd`
4. Set `NVD_SNAPSHOT_REGION=<region>`
5. Run the publisher — it will bootstrap a new snapshot on S3
