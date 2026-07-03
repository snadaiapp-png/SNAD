# Reliability Readiness

Issue #33 tracks production reliability and availability.

Targets:
- Availability 99.95%
- RTO 60 minutes
- RPO 15 minutes

Required evidence:
- Paid production plans
- No free-tier sleep dependency
- Application restart test
- Database failover test
- Rollback test
- Secondary-region recovery test
- Provider limits and escalation contacts

The first automated baseline verifies that SANAD can restart against the same PostgreSQL database while preserving Flyway schema version 9 and returning healthy readiness status.

This baseline does not close the production gate. Provider-level availability and recovery tests remain mandatory.
