# NVD Snapshot Recovery

**Status:** PROPOSED (EXEC-PROMPT-010R12E)
**Date:** 2026-06-26

---

## Recovery Scenarios

### Scenario 1: Corrupt Snapshot Published

**Symptom:** NVD Snapshot Integrity fails with `archive_sha_mismatch` or `contents_invalid`.

**Root Cause:** The publisher published a snapshot whose archive was
corrupted during upload or storage.

**Recovery:**
1. The `latest.json` pointer may already point to the corrupt snapshot.
2. Identify the previous known-good snapshot:
   ```bash
   python3 scripts/security/nvd_snapshot_store.py list --backend $NVD_SNAPSHOT_BACKEND
   ```
3. Manually verify each previous snapshot:
   ```bash
   python3 scripts/security/verify_nvd_snapshot.py <downloaded-archive> \
     --expected-snapshot-id <previous-id>
   ```
4. Once a good snapshot is found, manually update the pointer:
   ```bash
   python3 -c "
   import sys; sys.path.insert(0, '.')
   from scripts.security.nvd_snapshot_store import get_backend
   backend = get_backend('$NVD_SNAPSHOT_BACKEND')
   manifest = backend.download_manifest('<good-snapshot-id>')
   backend.promote_latest_pointer('<good-snapshot-id>', manifest)
   print('Pointer restored to <good-snapshot-id>')
   "
   ```
5. Re-run NVD Snapshot Integrity to confirm.
6. Run the publisher to publish a fresh snapshot.

### Scenario 2: Canonical Directory Corrupted on Runner

**Symptom:** Publisher fails validation with `lock_files_present` or `temp_files_present` or `sha256_mismatch`.

**Root Cause:** The persistent canonical directory on the self-hosted
runner was corrupted (e.g., disk error, interrupted write).

**Recovery:**
1. The LKG backup directory (`/var/lib/snad/dependency-check-data/lkg`) contains the last known-good state.
2. Replace canonical with LKG:
   ```bash
   rm -rf /var/lib/snad/dependency-check-data/canonical
   cp -a /var/lib/snad/dependency-check-data/lkg /var/lib/snad/dependency-check-data/canonical
   ```
3. If LKG is also corrupt, restore from the latest storage snapshot:
   ```bash
   python3 -c "
   import sys, pathlib, tempfile; sys.path.insert(0, '.')
   from scripts.security.nvd_snapshot_store import get_backend
   backend = get_backend('$NVD_SNAPSHOT_BACKEND')
   latest = backend.resolve_latest_verified()
   dest = pathlib.Path(tempfile.mkdtemp())
   archive = backend.download_snapshot(latest['snapshot_id'], dest)
   import subprocess
   subprocess.run(['tar', '-xf', str(archive), '-C', str(dest)], check=True)
   import shutil
   shutil.copytree(dest / 'data', '/var/lib/snad/dependency-check-data/canonical', dirs_exist_ok=True)
   print('Canonical restored from storage snapshot', latest['snapshot_id'])
   "
   ```
4. Re-run the publisher.

### Scenario 3: NVD API Extended Outage

**Symptom:** Publisher repeatedly fails with NVD API 524/503 errors.

**Root Cause:** NVD infrastructure is down for an extended period.

**Recovery:**
1. Consumers (OWASP, R12B) continue to use the last verified snapshot
   as long as it is within the 48-hour freshness window.
2. If the outage exceeds 48 hours, consumers will fail closed with
   `stale` validation.
3. **Do NOT** revert to ephemeral GitHub-hosted builds.
4. **Do NOT** disable the freshness check.
5. Wait for NVD to recover, then the next scheduled publisher run will
   publish a fresh snapshot.
6. If NVD is down > 48 hours and you need to run R12B urgently:
   - Manually extend the max-age-hours in the OWASP workflow (temporary)
   - Document the exception
   - Revert the extension after NVD recovers

### Scenario 4: Storage Backend Unavailable

**Symptom:** Publisher fails at "Publish snapshot to storage" step.

**Root Cause:** S3 bucket deleted, GHCR package inaccessible, or
authentication expired.

**Recovery:**
1. The canonical directory is still valid — only the publish step failed.
2. Fix the storage backend:
   - S3: recreate bucket, verify IAM role, check endpoint
   - GHCR: verify package permissions, check token
3. Re-run the publisher — it will use the existing canonical and
   re-attempt the publish.

### Scenario 5: Self-Hosted Runner Offline

**Symptom:** Publisher jobs stay queued indefinitely.

**Root Cause:** The self-hosted runner is offline or unregistered.

**Recovery:**
1. Do NOT change `runs-on` to `ubuntu-latest` — this would break
   the persistent canonical directory.
2. Bring the runner back online.
3. If the runner is permanently lost:
   a. Provision a new runner with the same labels.
   b. Create `/var/lib/snad/dependency-check-data/`.
   c. Restore canonical from the latest storage snapshot (see Scenario 2).
   d. Re-run the publisher.

## Emergency Contacts

- **Repository Owner:** snadaiapp-png
- **Governance Issue:** #101
- **Security Incident:** #109

## Do NOT List

- Do NOT revert to ephemeral GitHub-hosted NVD builds.
- Do NOT disable the freshness check.
- Do NOT bypass the self-hosted runner requirement.
- Do NOT store `NVD_API_KEY` in consumer workflows.
- Do NOT use GitHub Actions Cache as the canonical snapshot store.
- Do NOT delete the `latest.json` pointer without a replacement.
