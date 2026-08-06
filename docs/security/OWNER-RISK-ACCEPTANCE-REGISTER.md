# Owner Risk Acceptance Register

## Purpose

Record residual risks that only the Project Owner account may accept.

## Risk acceptance rules

- Critical risks are not accepted for production release unless explicitly documented as emergency owner exception.
- High risks require remediation or owner acceptance with compensating controls.
- Every accepted risk must include expiry and review date.
- **Remediation supersedes risk acceptance:** a finding eliminated by
  engineering changes is recorded as SUPERSEDED and requires no owner
  signature.

## Register

| Risk ID | Source issue | Severity | Risk | Status | Owner decision | Expiry | Evidence |
|---|---|---|---|---|---|---|---|
| RISK-CRM-032-001 | HIGH-01 | HIGH | Test encryption key hardcoded as default in application-local.yml | ✅ SUPERSEDED — remediated by engineering (2026-07-31) | N/A — not accepted; remediated | N/A | `ProductionSecurityGuard.java`, `CrmEncryptionKeyValidator.java`, `application-local.yml`, `ProductionSecurityGuardTest` (8/8), `CrmEncryptionKeyValidatorTest` (8/8) |
| RISK-CRM-032-002 | HIGH-02 | HIGH | No startup guard for RLS, rate limiter, actuator endpoints | ✅ SUPERSEDED — remediated by engineering (2026-07-31) | N/A — not accepted; remediated | N/A | `ProductionSecurityGuard.java`, `META-INF/spring.factories`, `ProductionSecurityGuardTest` (8/8) |

## Risk Acceptance Details

### RISK-CRM-032-001: Test Encryption Key Hardcoded as Default

| Field | Value |
|---|---|
| **Risk ID** | RISK-CRM-032-001 |
| **Source Issue** | HIGH-01 |
| **Severity** | HIGH |
| **Finding Title** | Test Encryption Key Hardcoded as Default |
| **Technical Description** | The `custom-field-encryption-key` property had a hardcoded default value (`AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=`) in the local profile. If the `local` profile accidentally activates in a deployed environment, this test key would be used for encrypting sensitive custom field values. |
| **Resolution** | ✅ REMEDIATED (2026-07-31) — no risk acceptance required |
| **Remediation Evidence** | 1. `ProductionSecurityGuard` refuses prod startup on test/default key. 2. `CrmEncryptionKeyValidator` rejects the known test key and trivially weak keys unconditionally in both crypto paths. 3. Test key default removed from `application-local.yml`; non-prod unconfigured → ephemeral AES-256 key. 4. Prod requires `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`. |
| **Test Evidence** | `ProductionSecurityGuardTest` 8/8 PASS; `CrmEncryptionKeyValidatorTest` 8/8 PASS |
| **Owner** | N/A — remediated, no acceptance required |
| **External Approver** | N/A |
| **Decision** | SUPERSEDED (remediation) |
| **Approval Date** | N/A |
| **Review Date** | N/A |
| **Expiration Date** | N/A |
| **Signature Evidence** | Not required — finding eliminated by engineering change |

### RISK-CRM-032-002: No Startup Guard for Production-Critical Security Features

| Field | Value |
|---|---|
| **Risk ID** | RISK-CRM-032-002 |
| **Source Issue** | HIGH-02 |
| **Severity** | HIGH |
| **Finding Title** | No Startup Guard for Production-Critical Security Features |
| **Technical Description** | No startup validation ensured critical security features are enabled in production: (1) Row-Level Security could be disabled without detection, (2) Rate limiter could remain in-memory without distributed adapter, (3) Actuator endpoints could be over-exposed. |
| **Resolution** | ✅ REMEDIATED (2026-07-31) — no risk acceptance required |
| **Remediation Evidence** | 1. `ProductionSecurityGuard` (EnvironmentPostProcessor, prod profile) validates encryption key, `snad.rls.enabled`, and actuator exposure before any bean is created. 2. Registered in `META-INF/spring.factories`. 3. `SKIP_SECURITY_GUARD=true` escape hatch is audited via WARN log. |
| **Test Evidence** | `ProductionSecurityGuardTest` 8/8 PASS (incl. `blocksOnRlsDisabled`, `blocksOnSensitiveActuatorEndpoint`) |
| **Owner** | N/A — remediated, no acceptance required |
| **External Approver** | N/A |
| **Decision** | SUPERSEDED (remediation) |
| **Approval Date** | N/A |
| **Review Date** | N/A |
| **Expiration Date** | N/A |
| **Signature Evidence** | Not required — finding eliminated by engineering change |

## Owner decision template

| Field | Value |
|---|---|
| Owner account | snadaiapp-png |
| Decision | TBD |
| Release SHA | TBD |
| UTC timestamp | TBD |
| Scope | TBD |
| Review date | TBD |

## Closure

This register is mandatory for any non-remediated High risk or exceptional Critical-risk decision.

**Status:** ✅ CLOSED — Both HIGH findings (HIGH-01, HIGH-02) were remediated
by engineering changes on 2026-07-31 (CRM-032 Engineering Remediation,
Security-by-Design). No owner risk acceptance was required or recorded.
Per SANAD governance, remediation supersedes risk acceptance.
