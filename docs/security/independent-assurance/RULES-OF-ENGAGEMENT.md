# Independent Security Assessment — Rules of Engagement

**Status:** awaiting named-party approval.

## Authorization boundary

Testing is permitted only after the target allowlist, dates, source addresses, identities and emergency contacts are completed in the signed external engagement record. Repository publication does not itself authorize active testing.

## Permitted activities

- Authenticated and unauthenticated web/API security testing.
- Horizontal and vertical authorization tests using provided test identities.
- Tenant-boundary and object-reference manipulation within isolated test data.
- Session, CSRF, input-validation, workflow, idempotency and business-logic tests.
- Read-only review of approved configuration, dependencies, containers, CI/CD and secret-management controls.
- Controlled proof of impact that stops at the minimum evidence necessary.

## Prohibited without separate written approval

- Denial-of-service, stress, resource-exhaustion or destructive database testing.
- Social engineering, phishing, physical testing or employee targeting.
- Persistence, malware, destructive payloads or modification of real customer data.
- Accessing, downloading or retaining data outside the approved test tenants.
- Testing third-party providers not explicitly listed in the target allowlist.
- Publishing raw secrets, personal data or weaponized exploit details in GitHub.

## Mandatory stop conditions

Stop testing and contact Security Governance immediately upon:

- confirmed cross-tenant access to non-test data;
- discovery of a live credential or signing key;
- service instability or material performance degradation;
- unintended financial, inventory, payroll or external-provider action;
- evidence that the tested deployment differs from the frozen version;
- any activity outside the signed authorization boundary.

## Finding notification targets

- Critical: immediate secure notification; testing pauses pending direction.
- High: notify within four hours.
- Medium/Low: include in the daily or final report unless escalation is warranted.

The assessor records only sanitized references in this repository. Sensitive reproduction material remains in the restricted assessor evidence system and is referenced by immutable external identifier.
