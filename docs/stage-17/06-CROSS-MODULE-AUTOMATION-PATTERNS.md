# Stage 17 — Cross-Module Automation Patterns

**Date**: 2026-07-08

---

## Automation Patterns

### 1. CRM → ERP

```
Trigger: Deal won in CRM
Action: Create production/order in ERP

Flow:
  1. CRM: Deal stage changes to "Closed Won"
  2. Workflow: Trigger fires
  3. ERP: Create sales order (draft)
  4. Approval: Sales manager approves order
  5. ERP: Order confirmed, production scheduled
  6. Notification: Notify production team

Data involved: Deal details, customer info, product/service details
Approval required: YES (sales manager)
Failure mode: If ERP unavailable, hold in queue, retry on availability
Audit: Full trail from CRM deal to ERP order
```

### 2. ERP → Accounting

```
Trigger: Production complete / goods delivered
Action: Create invoice in Accounting

Flow:
  1. ERP: Production order marked complete
  2. Workflow: Trigger fires
  3. Accounting: Draft invoice (AI-assisted)
  4. Approval: Finance reviews and approves invoice
  5. Accounting: Invoice sent to customer
  6. Notification: Notify sales team (invoice sent)

Data involved: Order details, pricing, customer billing info
Approval required: YES (finance — AI cannot post entries)
Failure mode: If pricing mismatch, flag for manual review
Audit: Full trail from ERP order to accounting invoice
```

### 3. HRM → Payroll

```
Trigger: Timesheet approved / month-end
Action: Generate payroll entries

Flow:
  1. HRM: Timesheets approved for period
  2. Workflow: Trigger fires (month-end or on approval)
  3. Payroll: Calculate salary (base + overtime + deductions)
  4. Approval: HR manager approves payroll
  5. Accounting: Draft payroll journal entry (AI-assisted)
  6. Approval: Finance approves entry
  7. Accounting: Entry posted (after human approval)
  8. Notification: Notify employees (payslip available)

Data involved: Employee hours, salary rates, deductions, tax info
Approval required: YES (HR manager + Finance — double approval)
Failure mode: If data incomplete, hold and notify HR
Audit: Full trail from timesheet to payroll entry
```

### 4. Ecommerce → Inventory

```
Trigger: Online order placed
Action: Reserve inventory + create fulfillment order

Flow:
  1. Ecommerce: Customer places order
  2. Workflow: Trigger fires immediately
  3. ERP: Check inventory availability
  4. If available: Reserve stock, create fulfillment order
  5. If unavailable: Backorder, notify customer
  6. ERP: Create shipping request
  7. Notification: Notify warehouse team

Data involved: Order items, quantities, customer shipping address
Approval required: NO (automated for stock reservation)
  BUT: If order > $10,000, require manager approval
Failure mode: If inventory system unavailable, hold order, alert ops
Audit: Full trail from ecommerce order to inventory reservation
```

### 5. POS → Accounting

```
Trigger: Sale completed at POS
Action: Create sales entry in Accounting

Flow:
  1. POS: Transaction completed (cash/card)
  2. Workflow: Trigger fires (real-time or batch)
  3. Accounting: Draft sales journal entry
  4. If < $1,000: Auto-approve (low risk)
  5. If > $1,000: Require manager approval
  6. Accounting: Entry posted (after approval if needed)
  7. ERP: Update inventory (deduct sold items)
  8. CRM: Update customer purchase history (if customer identified)

Data involved: Transaction amount, payment method, items sold
Approval required: Conditional (based on amount threshold)
Failure mode: If accounting system unavailable, queue entries, batch later
Audit: Full trail from POS transaction to accounting entry
```

### 6. Support → CRM

```
Trigger: Support ticket resolved
Action: Update CRM customer record

Flow:
  1. Support: Ticket marked resolved
  2. Workflow: Trigger fires
  3. CRM: Update customer interaction history
  4. CRM: Update customer health score (AI-assisted)
  5. If satisfaction survey negative: Create follow-up task for account manager
  6. Notification: Notify account manager if health score drops

Data involved: Ticket details, resolution, customer feedback
Approval required: NO (automated update)
Failure mode: If CRM unavailable, queue update, retry later
Audit: Full trail from support ticket to CRM update
```

### 7. Workflow → Notifications

```
Trigger: Any workflow step
Action: Send notification

Flow:
  1. Workflow: Step reached (approval, action, delay)
  2. Workflow: Notification node fires
  3. Notification: Send to configured recipients
  4. Channels: In-app, email, SMS, Slack/Teams
  5. Log: Notification recorded

Data involved: Step context, recipient, message template
Approval required: NO (automated)
Failure mode: If notification channel unavailable, retry or fallback to in-app
Audit: Notification log (who, what, when, channel)
```

### 8. AI → Recommendations

```
Trigger: On-demand or scheduled
Action: AI generates recommendation

Flow:
  1. User triggers AI feature (or scheduled run)
  2. AI Gateway: Process request (tenant-scoped)
  3. AI: Generate recommendation with explanation
  4. UI: Display to user (with "AI Recommendation" label)
  5. User: Reviews, accepts, modifies, or dismisses
  6. If accepted: Action executes (with approval if high-impact)
  7. Audit: Log AI recommendation + user decision

Data involved: Module data, AI context, user permissions
Approval required: YES for high-impact (financial, data deletion, etc.)
  NO for low-impact (summaries, insights)
Failure mode: If AI unavailable, show "AI features unavailable", fall back to manual
Audit: AI request + response + user action
```

## Cross-Module Pattern Summary

```
Patterns defined: 8
  1. CRM → ERP (deal won → sales order)
  2. ERP → Accounting (production → invoice)
  3. HRM → Payroll (timesheet → payroll entry)
  4. Ecommerce → Inventory (order → stock reservation)
  5. POS → Accounting (sale → journal entry)
  6. Support → CRM (ticket → customer update)
  7. Workflow → Notifications (step → alert)
  8. AI → Recommendations (request → suggestion)

Each pattern includes:
  - Trigger ✅
  - Actor (module/user) ✅
  - Data involved ✅
  - Approval required ✅
  - Failure mode ✅
  - Audit record ✅

Implementation: DOCUMENTED (ready for development)
Dependencies: Workflow Engine (to be built), all modules (to be built)
```
