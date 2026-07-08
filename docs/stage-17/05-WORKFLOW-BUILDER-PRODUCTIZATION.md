# Stage 17 — Workflow Builder Productization

**Date**: 2026-07-08

---

## Workflow Builder Overview

The Workflow Builder is a visual tool that allows users to create, edit, and
manage automated workflows without coding. It connects triggers, conditions,
actions, and approvals across SNAD modules.

## Builder Features

### 1. Create Workflow from Template

```
Feature: Start from pre-built templates.
Templates:
  - "New Lead Assignment" (CRM → Notification)
  - "Deal Stage Change" (CRM → Workflow → Approval)
  - "Invoice Approval" (Accounting → Approval → Notification)
  - "Employee Onboarding" (HRM → Tasks → Notifications)
  - "Inventory Reorder" (ERP → Purchase Order → Approval)
  - "Support Ticket Escalation" (Support → Routing → Notification)

Process:
  1. User selects template
  2. Template pre-populates workflow steps
  3. User customizes (names, conditions, recipients)
  4. User saves and activates
```

### 2. Edit Workflow Steps

```
Feature: Visually edit workflow steps.
UI: Drag-and-drop canvas
Node types:
  - Trigger (start): Event that starts the workflow
  - Condition: Branch logic (if/else)
  - Action: Execute an operation
  - Approval: Require human approval
  - Notification: Send alert
  - Delay: Wait for time/event
  - AI: AI-powered step (recommendation, analysis)

Editing:
  - Click node to edit properties
  - Drag to rearrange
  - Connect nodes with arrows
  - Delete nodes
  - Add new nodes from palette
  - Real-time validation (detects errors)
```

### 3. Define Approvals

```
Feature: Configure approval steps.
Approval types:
  - Sequential: Approver A → Approver B → Approver C
  - Parallel: Approver A AND Approver B (both must approve)
  - Any-one: Approver A OR Approver B (either can approve)

Configuration:
  - Select approver role (ADMIN, MANAGER, specific user)
  - Set SLA (time to approve)
  - Set escalation (who to notify on timeout)
  - Set fallback action (auto-approve, auto-reject, or hold)

Example:
  Approval step: "Discount > 10%"
  Approvers: ["Sales Manager (sequential)", "VP Sales (if > 20%)"]
  SLA: 48 hours
  Escalation: "Notify COO if not approved in 48h"
  Fallback: "Hold (no auto-action)"
```

### 4. Define Conditions

```
Feature: Add conditional branching.
Condition types:
  - Field comparison (amount > $1000)
  - Role check (user.role == ADMIN)
  - Time check (weekday, business hours)
  - Department check (user.department == "Sales")
  - Custom rule (AI-evaluated)

Example:
  Condition: "Deal value > $50,000"
  If TRUE: → Require CFO approval
  If FALSE: → Auto-approve by Sales Manager
```

### 5. Define Notifications

```
Feature: Configure notification steps.
Channels:
  - In-app notification
  - Email
  - SMS (future)
  - Slack/Teams (future)

Configuration:
  - Recipient (user, role, or custom)
  - Message template (with variables)
  - Priority (normal, urgent)
  - Timing (immediate, delayed, scheduled)

Example:
  Notification: "Deal moved to Closed Won"
  Recipient: "Sales Team"
  Channel: "In-app + Email"
  Message: "Congratulations! {deal_name} ({deal_value}) has been won by {owner}."
  Timing: "Immediate"
```

### 6. Connect to Modules

```
Feature: Link workflows to SNAD modules.
Available triggers by module:
  CRM: New contact, deal stage change, deal won/lost
  ERP: Low stock, order delayed, production complete
  Accounting: Invoice created, payment received, entry flagged
  HRM: New employee, leave request, performance review due
  Support: New ticket, ticket escalated, ticket resolved

Available actions by module:
  CRM: Update contact, change deal stage, assign owner
  ERP: Create purchase order, update inventory, schedule production
  Accounting: Draft entry (requires approval), send invoice, mark paid
  HRM: Create task, assign training, schedule review
  Support: Assign ticket, change priority, send response
```

### 7. Test Before Activation

```
Feature: Test workflow before going live.
Testing modes:
  1. Dry run: Simulate with sample data (no real actions)
  2. Test mode: Execute with test flag (actions marked as test)
  3. Limited activation: Enable for 1 user only (pilot)

Test output:
  - Step-by-step execution log
  - Which conditions were met
  - Which actions would execute
  - Which approvals would be required
  - Any errors or warnings

Validation:
  - All nodes connected (no orphan steps)
  - No infinite loops detected
  - All required fields configured
  - Approval steps have approvers defined
  - Notifications have recipients
```

## Workflow Builder Productization Summary

```
Features defined: 7
  1. Create from template
  2. Edit steps (drag-and-drop)
  3. Define approvals (sequential, parallel, any-one)
  4. Define conditions (branching logic)
  5. Define notifications (multi-channel)
  6. Connect to modules (CRM, ERP, Accounting, HRM, Support)
  7. Test before activation (dry run, test mode, pilot)

UI: Visual drag-and-drop builder
Target users: Admins and managers (no coding required)
Security: All workflows respect RBAC and tenant isolation
AI integration: AI nodes for recommendations and analysis (human-confirmed)

Implementation: DOCUMENTED (ready for development)
Dependencies: Workflow Engine (to be built), AI Gateway (Stage 16)
```
