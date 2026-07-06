# SANAD Stage 08 — Dependency Matrix

**Matrix ID:** `SANAD-ST08-DEP-001`
**Stage:** 08
**Date:** 2026-07-06

---

## 1. Story Dependencies

| Story ID          | Depends On        | Type        | Notes                                       |
|-------------------|-------------------|-------------|---------------------------------------------|
| ST8-01-F1-S2      | ST8-01-F1-S1      | Data        | Quota enforcement requires metrics          |
| ST8-01-F2-S1      | ST8-01-F1-S1      | Config      | Autoscaling uses capacity baseline          |
| ST8-01-F3-S1      | ST8-01-F1-S1      | Config      | Pool governance uses metrics                |
| ST8-01-F4-S1      | ST8-01-F2-S1      | Config      | Rate limits use autoscaling policy          |
| ST8-01-F5-S1      | ST8-01-F2-S1      | Config      | Breakers per downstream service             |
| ST8-01-F6-S1      | ST8-01-F5-S1      | Runtime     | Backpressure uses breakers                  |
| ST8-01-F7-S1      | ST8-01-F6-S1      | Runtime     | Graceful degradation coordinated with backpressure |
| ST8-01-F8-S1      | ST8-01-F7-S1      | Runtime     | Load-shedding uses degradation signals      |
| ST8-02-F2-S1      | ST8-02-F1-S1      | Data        | Currency model requires locale model        |
| ST8-02-F3-S1      | ST8-02-F1-S1      | Data        | Time-zone requires locale model             |
| ST8-02-F4-S1      | ST8-02-F2-S1      | Data        | Country profile requires currency           |
| ST8-02-F5-S1      | ST8-02-F1-S1      | Runtime     | Localization uses locale model              |
| ST8-02-F6-S1      | ST8-02-F4-S1      | Infra       | Data residency uses country profile         |
| ST8-03-F2-S1      | ST8-03-F1-S1      | Data        | Product submission requires publisher       |
| ST8-03-F3-S1      | ST8-03-F2-S1      | Process     | Security review requires product version    |
| ST8-03-F4-S1      | ST8-03-F3-S1      | Process     | Listing requires certification              |
| ST8-03-F5-S1      | ST8-03-F4-S1      | Runtime     | Install requires published listing          |
| ST8-03-F6-S1      | ST8-03-F5-S1      | Runtime     | Update/rollback requires install            |
| ST8-03-F7-S1      | ST8-03-F5-S1      | Data        | Usage metering requires install             |
| ST8-03-F8-S1      | ST8-03-F4-S1      | Process     | Review moderation requires listings         |
| ST8-03-F9-S1      | ST8-03-F5-S1      | Runtime     | Revocation requires install base            |
| ST8-04-F2-S1      | ST8-04-F1-S1      | Runtime     | Lifecycle requires schema                   |
| ST8-04-F3-S1      | ST8-04-F2-S1      | Content     | Retail pack uses lifecycle                  |
| ST8-04-F4-S1      | ST8-04-F2-S1      | Content     | PS pack uses lifecycle                      |
| ST8-04-F5-S1      | ST8-04-F2-S1      | Content     | Contracting pack uses lifecycle             |
| ST8-05-F2-S1      | ST8-05-F1-S1      | Data        | Skills/tools require registry               |
| ST8-05-F3-S1      | ST8-05-F2-S1      | Runtime     | Execution audit requires tools              |
| ST8-05-F4-S1      | ST8-05-F3-S1      | Process     | Approvals require execution records         |
| ST8-05-F5-S1      | ST8-05-F4-S1      | Process     | Evaluation requires approvals               |
| ST8-05-F6-S1      | ST8-05-F5-S1      | Runtime     | Cost budgets require evaluation             |
| ST8-05-F7-S1      | ST8-05-F6-S1      | Runtime     | Disablement uses cost signals               |
| ST8-06-F2-S1      | ST8-06-F1-S1      | Data        | Delegated admin requires hierarchy          |
| ST8-06-F3-S1      | ST8-06-F2-S1      | Infra       | SSO requires delegated admin                |
| ST8-06-F4-S1      | ST8-06-F3-S1      | Infra       | SCIM requires SSO                           |
| ST8-06-F5-S1      | ST8-06-F1-S1      | Data        | SoD uses hierarchy                          |
| ST8-06-F6-S1      | ST8-06-F5-S1      | Process     | Recertification uses SoD                    |
| ST8-07-F2-S1      | ST8-07-F1-S1      | Data        | Deal registration requires partner          |
| ST8-07-F3-S1      | ST8-07-F2-S1      | UI          | Portal uses deal data                       |
| ST8-07-F4-S1      | ST8-07-F3-S1      | Content     | Certification uses portal                   |
| ST8-08-F2-S1      | ST8-08-F1-S1      | Data        | Keys require portal                         |
| ST8-08-F3-S1      | ST8-08-F2-S1      | Runtime     | Webhooks use keys                           |
| ST8-08-F4-S1      | ST8-08-F3-S1      | Infra       | Sandbox uses webhooks                       |
| ST8-08-F5-S1      | ST8-08-F4-S1      | Data        | Rate limits use sandbox                     |
| ST8-08-F6-S1      | ST8-08-F5-S1      | Process     | Versioning uses limits                      |
| ST8-09-F2-S1      | ST8-09-F1-S1      | Data        | Trial uses metering                         |
| ST8-09-F3-S1      | ST8-09-F2-S1      | Data        | Health uses trial data                      |
| ST8-09-F4-S1      | ST8-09-F3-S1      | Process     | Pricing experiments use health              |
| ST8-10-F2-S1      | ST8-10-F1-S1      | Data        | Catalog uses warehouse                      |
| ST8-10-F3-S1      | ST8-10-F2-S1      | UI          | Dashboards use catalog                      |
| ST8-10-F4-S1      | ST8-10-F3-S1      | Process     | Lineage uses dashboards                     |
| ST8-11-F2-S1      | ST8-11-F1-S1      | Process     | On-call uses dashboards                     |
| ST8-11-F3-S1      | ST8-11-F2-S1      | Process     | Runbooks use on-call                        |
| ST8-11-F4-S1      | ST8-11-F3-S1      | Infra       | Synthetic uses runbooks                     |

---

## 2. Cross-Epic Dependencies

| From Epic         | To Epic           | Reason                                              |
|-------------------|-------------------|-----------------------------------------------------|
| ST8-EPIC-02       | ST8-EPIC-01       | Globalization uses scale foundation                 |
| ST8-EPIC-03       | ST8-EPIC-02       | Marketplace listings localized                      |
| ST8-EPIC-04       | ST8-EPIC-03       | Industry packs distributed via marketplace          |
| ST8-EPIC-05       | ST8-EPIC-01       | AI agents require backpressure and quotas           |
| ST8-EPIC-06       | ST8-EPIC-05       | Enterprise features integrate with AI agents        |
| ST8-EPIC-07       | ST8-EPIC-06       | Partner portal uses enterprise identity             |
| ST8-EPIC-08       | ST8-EPIC-07       | Developer platform exposes partner APIs             |
| ST8-EPIC-09       | ST8-EPIC-08       | Growth uses developer metering                      |
| ST8-EPIC-10       | ST8-EPIC-09       | Analytics feeds growth metrics                      |
| ST8-EPIC-11       | ST8-EPIC-01       | Operations supports scale architecture              |
| ST8-EPIC-12       | All               | Debt closure unblocks final gate                    |
