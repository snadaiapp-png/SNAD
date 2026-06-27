# NVD Bulk Feed Recovery Runbook

**Status:** ACTIVE
**Date:** 2026-06-27
**Version:** R12L

---

## Recovery Scenarios

### Scenario 1: Feed Mirror Run Interrupted

**Symptom:** Workflow timeout or runner preemption during download.

**Recovery:**
1. Verified files are persisted in the seed draft release.
2. Dispatch a new run — it will resume from the checkpoint.
3. Only missing/changed files will be downloaded.

### Scenario 2: NVD Feed Server Unavailable

**Symptom:** HTTP 503/504 on all feed files.

**Recovery:**
1. If a Last-Known-Good feed release exists, downstream consumers
   continue to operate from it.
2. Wait for NVD to recover.
3. Next scheduled run will attempt download automatically.

### Scenario 3: Checkpoint Corruption

**Symptom:** checkpoint.json schema validation fails.

**Recovery:**
1. Delete the corrupted seed draft release.
2. Start a fresh generation — all files will be re-downloaded.
3. Previous LKG remains available.

### Scenario 4: Digest Mismatch on All Files

**Symptom:** SHA-256 mismatch for multiple files.

**Recovery:**
1. This indicates NVD updated the feeds.
2. Delete the seed and start fresh — new META files will be downloaded.
3. LKG is preserved until new release is verified.

### Scenario 5: Stale Feed (> 7 days)

**Symptom:** No successful feed publication in 7+ days.

**Recovery:**
1. Check NVD availability manually.
2. If NVD is up, dispatch Feed Mirror Publisher manually.
3. If NVD is down, document the outage and wait.
4. Do NOT use stale feed for production acceptance.
