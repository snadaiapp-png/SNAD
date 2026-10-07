# R0C13 G11 Production Release Authorization

This file is a release-control marker for the protected R13-G11 candidate.

It authorizes deployment only after:
- independent protected review;
- terminal-green exact-head checks;
- squash merge with the exact commit-message token `PRODUCTION-RELEASE-AUTHORIZED`;
- canonical image publication and `production-release.yml` execution with rollback enabled.

Authorized production state:

```text
R0C13_PROVIDER_MODE = DISABLED
LIVE_PAYMENT_COLLECTION = OFF
ROLLBACK_ON_FAILURE = true
```

LIVE payment collection remains outside R0C13 engineering authority.
