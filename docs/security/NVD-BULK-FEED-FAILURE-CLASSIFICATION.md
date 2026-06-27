# NVD Bulk Feed Failure Classification

**Status:** ACTIVE
**Date:** 2026-06-27
**Version:** R12L

---

## Failure Classifications

### EXTERNAL NVD TRANSIENT FAILURE

**Cause:** NVD feed server returns 503/504 or connection resets.

**Impact:**
- Current run fails for affected files.
- Verified files remain in checkpoint.
- LKG release remains available.
- Downstream consumers continue from LKG.

**Action:**
- Do not retry immediately.
- Wait for next scheduled run (daily at 03:17 UTC).
- Or manually dispatch after ≥4 hours backoff.

### CHECKPOINT_CORRUPTION

**Cause:** checkpoint.json is invalid, schema mismatch, or missing.

**Impact:**
- Cannot resume from previous progress.
- All files must be re-downloaded.

**Action:**
- Delete the corrupted seed draft release.
- Start a fresh generation.

### DIGEST_MISMATCH

**Cause:** Downloaded file SHA-256 does not match META SHA-256.

**Impact:**
- File is not promoted to final.
- Retried up to 4 times with backoff.
- If all retries fail, file is marked as FAILED.

**Action:**
- If single file: re-download in next run.
- If all files: NVD likely updated feeds. Delete seed, start fresh.

### META_PARSE_ERROR

**Cause:** META file format is invalid or missing required fields.

**Impact:**
- File cannot be verified.
- Not promoted to final.

**Action:**
- Check NVD META format changes.
- Update parser if needed.

### SEED_RELEASE_ERROR

**Cause:** Cannot create or update GitHub Draft Release for checkpoint.

**Impact:**
- Progress cannot be persisted.
- Run fails.

**Action:**
- Check GitHub API status.
- Check repository permissions.
- Retry on next run.

### GZIP_CORRUPTION

**Cause:** Downloaded .json.gz file is not valid gzip.

**Impact:**
- File rejected.
- Retried with backoff.

**Action:**
- If persistent, NVD may be serving corrupt files.
- Wait for NVD to fix.

### NORMALIZATION_ERROR

**Cause:** Feed file cannot be normalized to Dependency-Check format.

**Impact:**
- Feed release cannot be published.

**Action:**
- Check NVD JSON 2.0 format changes.
- Update normalization logic.

### RELEASE_PUBLISH_ERROR

**Cause:** Cannot create or publish GitHub Release.

**Impact:**
- Feed release not available to consumers.

**Action:**
- Check GitHub API status.
- Check repository permissions.
- Seed checkpoint is preserved for retry.

### LKG_PRESERVED

**Cause:** New feed publication failed, but previous LKG exists.

**Impact:**
- Downstream consumers continue from LKG.
- Freshness warning may apply.

**Action:**
- Monitor for NVD recovery.
- Retry feed publication when NVD is stable.
