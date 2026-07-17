# SANAD On-Call and Escalation Standard

**Timezone:** Asia/Riyadh  
**Roster source:** `on-call-roster.json`  
**Accountable owner:** Project Owner (`@snadaiapp-png`)

## 1. Coverage

- SEV0 and SEV1: 24×7 duty coverage.
- SEV2: 24×7 acknowledgement when customer impact is active; otherwise business-hours ownership.
- SEV3: Saudi business hours, Sunday–Thursday.

The current interim roster is role-based and assigned to the Project Owner account. This removes ambiguity about ownership but creates a single-person concentration risk. A staffed primary/secondary rotation is required before any external SLA becomes contractual.

## 2. Paging chain

1. Platform Operations Primary.
2. If unacknowledged after 5 minutes: Platform Operations Secondary and Engineering Lead.
3. If unacknowledged after 10 minutes, or any SEV0: Project Owner.
4. Security/privacy/tenant event: Security Lead immediately.
5. Financial or data-integrity event: affected Product Owner and Data/Financial Integrity Lead immediately.
6. Customer-wide SEV0/SEV1: Communications Lead after Incident Commander confirmation.

Escalation timers continue until a human explicitly acknowledges command.

## 3. Authority

The Incident Commander may:

- Stop deployments.
- Roll back a release.
- Disable a feature or integration.
- Isolate a tenant or service where necessary to prevent harm.
- Request emergency access under audited procedures.
- Convene Security, Data, Product and Executive owners.

The Incident Commander may not waive audit, privacy or financial-integrity controls. Emergency access and destructive recovery require recorded approval and evidence.

## 4. Handover

A handover must state:

- Active incidents and severity.
- Services over 50% error-budget consumption.
- Recent risky changes.
- Disabled controls or temporary mitigations.
- Pending customer communications.
- Overdue incident actions.
- Named next primary and secondary.

## 5. Readiness review

The roster is reviewed monthly and after every SEV0/SEV1. Any coverage gap is reported in the monthly service report and escalated to the Project Owner.
