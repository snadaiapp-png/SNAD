# Independent Security Assessment — Rules of Engagement

**Status:** awaiting named-party approval.

Testing is authorized only after signed external records define the target allowlist, dates, source addresses, test identities, emergency contacts and stop conditions. Repository publication does not authorize active testing.

## Permitted after authorization

- Authenticated and unauthenticated web/API testing.
- Horizontal and vertical authorization testing within isolated test data.
- Tenant-boundary and object-reference manipulation.
- Session, CSRF, input-validation, workflow, idempotency and business-logic testing.
- Read-only review of approved configuration, dependencies, containers, CI/CD and secret-management controls.
- Controlled proof of impact limited to the minimum evidence necessary.

## Prohibited without separate written approval

- Denial-of-service, stress, resource exhaustion or destructive database testing.
- Social engineering, phishing, physical testing or employee targeting.
- Persistence, malware or destructive payloads.
- Accessing, downloading or retaining data outside approved test tenants.
- Testing unlisted third-party providers.
- Publishing secrets, personal data or weaponized exploit details in GitHub.

Any suspected real-customer exposure, active compromise, critical production instability or scope ambiguity triggers an immediate stop and escalation to Security Governance.
