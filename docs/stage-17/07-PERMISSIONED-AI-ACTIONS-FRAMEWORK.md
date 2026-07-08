# Stage 17 — Permissioned AI Actions Framework

**Date**: 2026-07-08

---

## AI Action Tiers

### 1. Read-Only AI

```
Description: AI reads data and provides insights/summaries.
Actions:
  - Summarize customer history
  - Generate expense report
  - Classify support ticket
  - Translate text
  - Answer natural language query

Permissions:
  - USER and above can use
  - Data scoped to user's permissions
  - No data modification
  - No external communication

Risk: LOW (no data change, no external action)
Approval: NONE required
Audit: Logged (request + output)
```

### 2. Recommend-Only AI

```
Description: AI suggests actions but cannot execute.
Actions:
  - Suggest next best action
  - Recommend lead score
  - Suggest deal stage change
  - Recommend training
  - Suggest approval chain

Permissions:
  - USER and above can use
  - Suggestions displayed with explanation
  - User decides whether to act

Risk: LOW (no automatic action)
Approval: NONE for recommendation
  BUT: If user accepts recommendation and it's high-impact,
       the ACTION requires approval (see tier 4)
Audit: Logged (recommendation + user decision)
```

### 3. Draft Action AI

```
Description: AI drafts content for human review.
Actions:
  - Draft email response
  - Draft journal entry
  - Draft performance review
  - Draft proposal document
  - Draft invoice

Permissions:
  - MANAGER and above (for sensitive drafts)
  - USER for routine drafts (email, notes)
  - Drafts clearly labeled "AI-Generated Draft"
  - Human must review and edit before sending/posting

Risk: MEDIUM (draft could be sent/posted if not reviewed)
Approval: YES — Human must review and explicitly approve draft
Audit: Logged (AI draft + human edits + approval)
```

### 4. Human-Approved Action AI

```
Description: AI prepares action, human approves execution.
Actions:
  - Change deal stage
  - Send customer communication
  - Create task assignment
  - Update inventory
  - Schedule meeting

Permissions:
  - MANAGER and above (depending on action)
  - AI prepares action with full context
  - Human sees: what will happen, why, risk level
  - Human explicitly clicks "Approve" to execute
  - No auto-approval, no timeout auto-approval

Risk: HIGH (action executes)
Approval: YES — Explicit human approval required
Audit: Logged (AI recommendation + human approval + execution)
```

### 5. Blocked Autonomous Action

```
Description: AI CANNOT do these, even with approval.
Actions BLOCKED:
  - Delete production data
  - Execute financial transfers
  - Change user passwords
  - Modify audit logs
  - Disable security controls
  - Access cross-tenant data
  - Train models on tenant data
  - Send communications to all customers
  - Post accounting entries (without authorized human approval)
  - Change user roles/permissions
  - Modify contracts
  - Process payroll

AI can only:
  - RECOMMEND these actions
  - DRAFT the action for human review
  - PROVIDE CONTEXT for human decision

The ACTUAL execution is always done by a human, manually, outside the AI system.

Risk: CRITICAL (if allowed, catastrophic)
Approval: N/A — action is BLOCKED
Audit: Any attempt is logged as security incident
```

## Prevention Rules

```
AI must NOT:
  1. Delete data automatically
     - AI can recommend deletion
     - Human must execute deletion manually
     - Deletion requires confirmation dialog

  2. Execute financial transfers automatically
     - AI can draft transfer
     - Human must review and approve
     - Transfer requires dual approval (maker + checker)

  3. Change permissions automatically
     - AI can suggest role changes
     - Human must execute role change
     - Role change requires admin approval

  4. Send sensitive communications without approval
     - AI can draft email/message
     - Human must review content
     - Human must approve sending
     - Mass communications require manager approval

  5. Execute high-impact workflows without approval
     - AI can recommend workflow execution
     - Human must trigger execution
     - High-impact workflows require approval steps
```

## Enforcement

```
Enforcement layers:
  1. AI Gateway: Validates action type against permissions
  2. Module layer: Module checks if action is allowed
  3. Service layer: Service enforces business rules
  4. Database layer: Database constraints prevent invalid operations

Fail-closed:
  - If any layer is unsure: DENY
  - If permission check fails: DENY
  - If audit log unavailable: DENY (no unaudited actions)

Violation handling:
  - Any attempt to bypass tiers = security incident
  - Immediate AI disable
  - Owner notification
  - Investigation
```

## Permission Matrix

```
Action Type              | USER | MANAGER | ADMIN | OWNER
-------------------------|------|---------|-------|------
Read-Only AI             | YES  | YES     | YES   | YES
Recommend-Only AI        | YES  | YES     | YES   | YES
Draft Action AI          | LIMITED| YES   | YES   | YES
Human-Approved Action AI | NO   | YES     | YES   | YES
Blocked Autonomous       | NO   | NO      | NO    | NO
                         |      |         |       | (AI cannot do, even for owner)
```

## Framework Summary

```
Tiers: 5 (Read-Only, Recommend-Only, Draft, Human-Approved, Blocked)
Prevention rules: 5 (no auto-delete, transfer, permission change, comms, workflow)
Enforcement: 4 layers (Gateway, Module, Service, Database)
Fail-closed: YES (deny on any uncertainty)

MANDATORY RULES:
  - No high-impact AI decision without human confirmation
  - AI must not post accounting entries without authorized human approval
  - No autonomous data deletion
  - No autonomous financial transfers
  - No autonomous permission changes

Implementation: DOCUMENTED (ready for development)
Dependencies: AI Gateway (Stage 16), AI Policy Engine (Stage 16)
```
