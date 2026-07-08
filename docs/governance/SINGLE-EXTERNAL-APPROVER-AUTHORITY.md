# Single External Approver Authority

## Purpose

This document records the Project Owner governance change for the current closure and evidence-approval path.

## Governance decision

For the current project phase, the Project Owner authorizes a single external alternative GitHub account to provide the required external approval on the Pull Request used for governance/evidence closure.

## Authorized approval model

```text
Approval model: single external alternative account
Required external approval count: 1
Owner account: snadaiapp-png
External approver account: abdulrhmansenan1985-creator
Scope: review and approve the current governance/evidence closure Pull Request only
Admin/Owner permissions: not granted
Secret access: not granted
Repository setting changes: not granted
Merge authority: retained by the Project Owner / repository owner
```

## Boundaries

The external approver approval is valid only as a GitHub Pull Request review submitted through GitHub. It must not be replaced by tokens, passwords, chat messages, screenshots, or unaudited manual statements.

The external approver must not receive or post any protected secret value.

This governance change does not claim that technical evidence was produced. Technical gates that require workflow evidence remain dependent on the relevant workflow run and audit trail.

## Approval target

The approver should review the Pull Request created from branch:

```text
governance/single-external-approver-59
```

Decision required from the external approver:

```text
APPROVED
```

## Final rule

Only the Pull Request approval from the authorized external account is accepted as the external approval record for this governance change.
