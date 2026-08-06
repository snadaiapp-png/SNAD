# CRM-022 — Test Report

| Field | Value |
|-------|-------|
| Job | `crm` (CRM Integration Tests) in `.github/workflows/ci.yml` |
| Test framework | JUnit 5 (Jupiter) via Maven Surefire, Spring Boot 3.5.6 |
| Runner | `ubuntu-latest`, JDK 21 (temurin), Docker for Testcontainers |

## 1. Tests in scope

The `crm` job runs all classes matching
`com.sanad.platform.crm.**.*IntegrationTest`.

**Verified selection (16 classes):**

```
com.sanad.platform.crm.integration.RealCommandAdaptersIntegrationTest
com.sanad.platform.crm.intelligence.application.CustomerIntelligenceIntegrationTest
com.sanad.platform.crm.party.AccountUseCasesIntegrationTest
com.sanad.platform.crm.party.AccountV2HttpIntegrationTest
com.sanad.platform.crm.party.AddressCommunicationHttpIntegrationTest
com.sanad.platform.crm.party.AddressCommunicationLifecycleIntegrationTest
com.sanad.platform.crm.party.AddressCommunicationOperationsHttpIntegrationTest
com.sanad.platform.crm.party.CommunicationPolicyHttpIntegrationTest
com.sanad.platform.crm.party.ContactRelationshipHttpIntegrationTest
com.sanad.platform.crm.party.ContactRelationshipImportHttpIntegrationTest
com.sanad.platform.crm.party.CustomerMasterHttpIntegrationTest
com.sanad.platform.crm.party.CustomerMasterMergeIntegrationTest
com.sanad.platform.crm.party.CustomerMasterSecurityIntegrationTest
com.sanad.platform.crm.web.CrmApiIntegrationTest
com.sanad.platform.crm.web.CrmImportAndCustomFieldIntegrationTest
com.sanad.platform.crm.web.CrmXlsxImportIntegrationTest
```

The authoritative spec's "four" classes is a stale count; package-scoped
selection captures the current set and any future additions.

## 2. Test characteristics

- Most classes use `@SpringBootTest`; `AccountUseCasesIntegrationTest` uses **Testcontainers** → the job retains the Docker-availability gate.
- JUnit XML emitted by Surefire (`-Dsurefire.useFile=true` → `target/surefire-reports/TEST-*.xml`) and uploaded as artifact `crm-surefire-reports`.

## 3. Local execution status

| Attempt | Result | Reason |
|---------|--------|--------|
| Live `mvn test -Dtest='com.sanad.platform.crm.**.*IntegrationTest'` | ⏸ **NOT RUN locally** | Docker daemon not running on workstation; Testcontainers tests require Docker. Local JDK is 17 (project targets 21). These run in GitHub Actions `ubuntu-latest` which provides Docker + JDK 21. |
| Test-selection pattern resolution | ✅ **16/16 classes matched** | Verified by enumerating tracked files against the pattern. |
| JUnit XML availability (proof of "if supported") | ✅ | `apps/sanad-platform/target/surefire-reports/TEST-com.sanad.platform.crm.*.xml` present from a prior local build. |

## 4. CI execution

Definitive test results will be produced by GitHub Actions when the PR opens.
The `crm` check will report its pass/fail on the PR. (See CRM-022-CI-REPORT.)
