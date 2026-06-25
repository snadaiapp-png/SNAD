# OWASP R11 Failure Root Cause

**Date:** 2026-06-25
**Run ID:** 28203456987
**Job ID:** 83548498527
**Tested SHA:** dbaf954a91591866d3bec02e9614903744041574
**Failure Timestamp:** 2026-06-25T22:42:24Z

---

## Summary

OWASP Dependency-Check failed with exit code 1 due to two distinct errors:

1. **Sonatype OSS Index 401 Unauthorized** — despite `hostedSuppressionsEnabled=false`, the OSS Index analyzer still attempted to contact `ossindex.sonatype.org` and received 401 Unauthorized.

2. **Velocity Template Not Found** — `templates/HTML,JSON.vsl (No such file or directory)` — the `-Dformat=HTML,JSON` parameter syntax is incorrect for Dependency-Check Maven Plugin 12.1.0. The plugin expects separate format specifications, not comma-separated values in a single `-Dformat` parameter.

## Sanitized Error Excerpt

```
[ERROR] caused by HttpResponseException: status code: 401, reason phrase: Unauthorized
[ERROR] AnalysisException: Failed to request component-reports
[ERROR] caused by DownloadFailedException: https://ossindex.sonatype.org/api/v3/component-report - Server status: 401
[ERROR] ReportException: Error generating the report for SANAD Platform
[ERROR] caused by ReportException: Unable to write the report
[ERROR] caused by FileNotFoundException: templates/HTML,JSON.vsl (No such file or directory)
```

## Root Cause

**PRIMARY:** `-Dformat=HTML,JSON` is invalid for dependency-check-maven 12.1.0. The plugin interprets `HTML,JSON` as a single template name rather than two separate formats. This causes `FileNotFoundException` for `templates/HTML,JSON.vsl`.

**CONTRIBUTING:** `hostedSuppressionsEnabled=false` does not fully disable the OSS Index analyzer. The OSS Index analyzer is a separate component that requires `-DossIndexAnalyzerEnabled=false` to fully disable.

## Corrective Architecture (R12)

1. Use `-Dformat=HTML -Dformat=JSON` (repeated parameter) or use the correct syntax
2. Add `-DossIndexAnalyzerEnabled=false` to fully disable OSS Index
3. Separate NVD database maintenance from dependency scanning
4. Use `update-only` goal for NVD database building
5. Use `check` goal with `-DautoUpdate=false` for offline scanning
