# CRM-010 Architecture Blueprint

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Architecture Principles

| Principle | Application |
|-----------|-------------|
| Customer 360 First | Every customer has one unified profile |
| AI Native | Intelligence is built-in, not bolted-on |
| Workflow First | Score thresholds trigger workflows |
| Event Driven | Score changes emit timeline + audit events |
| API First | All capabilities exposed via REST |
| Security by Design | RBAC on every endpoint, fail-closed AI |
| Audit by Default | Every intelligence operation audited |
| Tenant Isolation | All data tenant-scoped |

---

## 2. Layer Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        WEB LAYER                                  │
│                                                                   │
│  Customer360Controller     IntelligenceController                 │
│  GET  /api/v2/crm/customer-360/{accountId}                       │
│  GET  /api/v2/crm/customer-360/{accountId}/intelligence          │
│  GET  /api/v2/crm/customer-360/{accountId}/scores                │
│  GET  /api/v2/crm/customer-360/{accountId}/insights              │
│  POST /api/v2/crm/customer-360/{accountId}/rescore               │
│  GET  /api/v2/crm/customer-360/search                            │
│  GET  /api/v2/crm/customer-360/analytics/segments                │
│  GET  /api/v2/crm/customer-360/analytics/trends                  │
├─────────────────────────────────────────────────────────────────┤
│                     APPLICATION LAYER                             │
│                                                                   │
│  CustomerProfileUseCases    ScoringUseCases                       │
│  InsightUseCases            TimelineAggregatorUseCases            │
│  SegmentUseCases            AnalyticsUseCases                     │
│  RescoreUseCases            NextBestActionUseCases                │
├─────────────────────────────────────────────────────────────────┤
│                       DOMAIN LAYER                                │
│                                                                   │
│  CustomerProfile (aggregate root)                                │
│  HealthScore    CustomerLifetimeValue    EngagementScore          │
│  RiskScore      LoyaltyScore            Segment                  │
│  NextBestAction ScoreSnapshot           ScoringModel              │
│  IntelligenceEnvelope    ScoreComponent                           │
├─────────────────────────────────────────────────────────────────┤
│                    INFRASTRUCTURE LAYER                           │
│                                                                   │
│  JdbcCustomerProfileAdapter     JdbcScoringAdapter                │
│  JdbcTimelineAggregator         JdbcSegmentAdapter                │
│  JdbcAnalyticsAdapter           JdbcScoreHistoryAdapter           │
│  CustomerIntelligenceAiGateway  (delegates to AiGatewayPort)      │
│  ScoringOutboxWorker            InsightOutboxWorker               │
├─────────────────────────────────────────────────────────────────┤
│                PLATFORM INTEGRATION PORTS                         │
│                                                                   │
│  AuditPort    TimelineEventPort    AiGatewayPort                  │
│  WorkflowIntegrationPort    CustomerMasterRepository              │
│  Customer360QueryPort (extended)                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Domain Models

### 3.1 CustomerProfile (Aggregate Root)

```java
record CustomerProfile(
    UUID accountId,           // Primary key (from crm_accounts)
    UUID tenantId,            // Tenant isolation
    String displayName,       // Customer display name
    String accountType,       // CUSTOMER, PROSPECT, PARTNER
    String lifecycleStatus,   // ACTIVE, INACTIVE, CHURNED
    String customerSegment,   // ENTERPRISE, SMB, STARTUP
    String customerTier,      // PLATINUM, GOLD, SILVER, BRONZE
    String riskRating,        // LOW, MEDIUM, HIGH
    CustomerScores scores,    // Embedded scores
    List<SegmentMembership> segments,
    Instant lastScoredAt,
    long version
) {}

record CustomerScores(
    HealthScore healthScore,
    CustomerLifetimeValue lifetimeValue,
    EngagementScore engagementScore,
    RiskScore riskScore,
    LoyaltyScore loyaltyScore
) {}
```

### 3.2 Score Value Objects

```java
record HealthScore(
    double value,              // 0.0 - 100.0
    String band,               // CRITICAL, AT_RISK, HEALTHY, THRIVING
    Instant calculatedAt,
    List<ScoreComponent> components  // Weighted contributing factors
) {}

record CustomerLifetimeValue(
    double predictedValue,     // Currency amount
    double historicalValue,    // Actual revenue to date
    String tier,               // HIGH_VALUE, MID_VALUE, LOW_VALUE
    Instant calculatedAt,
    double confidence          // 0.0 - 1.0
) {}

record EngagementScore(
    double value,              // 0.0 - 100.0
    String band,               // DORMANT, LOW, MODERATE, HIGH
    Instant calculatedAt,
    List<ScoreComponent> components
) {}

record RiskScore(
    double value,              // 0.0 - 100.0
    String band,               // LOW_RISK, MEDIUM_RISK, HIGH_RISK
    Instant calculatedAt,
    List<RiskFactor> riskFactors
) {}

record LoyaltyScore(
    double value,              // 0.0 - 100.0
    String band,               // NEW, GROWING, LOYAL, CHAMPION
    Instant calculatedAt,
    List<ScoreComponent> components
) {}

record ScoreComponent(
    String name,               // e.g., "response_time", "meeting_frequency"
    double weight,             // 0.0 - 1.0
    double rawValue,           // Component's raw score
    double weightedValue       // rawValue * weight
) {}
```

### 3.3 Intelligence Models

```java
record NextBestAction(
    UUID actionId,
    String actionCode,         // SCHEDULE_FOLLOWUP, SEND_CONTENT, etc.
    String description,
    double confidence,         // 0.0 - 1.0
    String reasoning,
    Instant generatedAt,
    Instant expiresAt,
    boolean humanConfirmationRequired
) {}

record ScoreSnapshot(
    UUID snapshotId,
    UUID accountId,
    CustomerScores scores,
    Instant capturedAt,
    String triggerReason       // SCHEDULED, MANUAL, EVENT_DRIVEN
) {}
```

### 3.4 Scoring Model

```java
record ScoringModel(
    UUID modelId,
    String scoreType,          // HEALTH, CLV, ENGAGEMENT, RISK, LOYALTY
    String version,
    Map<String, Double> weights,  // Component name → weight
    boolean active,
    Instant activatedAt
) {}
```

---

## 4. Database Schema

### 4.1 New Tables (Migration V20260729_1)

```sql
-- Customer intelligence scores (separate from crm_accounts for immutability/history)
CREATE TABLE crm_customer_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    score_type VARCHAR(40) NOT NULL,          -- HEALTH, CLV, ENGAGEMENT, RISK, LOYALTY
    score_value DOUBLE PRECISION NOT NULL,
    score_band VARCHAR(40) NOT NULL,
    components JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence DOUBLE PRECISION,
    calculated_at TIMESTAMPTZ NOT NULL,
    trigger_reason VARCHAR(40) NOT NULL,      -- SCHEDULED, MANUAL, EVENT_DRIVEN
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(tenant_id, account_id, score_type, calculated_at)
);

-- Score history (immutable audit trail of score changes)
CREATE TABLE crm_customer_score_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    score_type VARCHAR(40) NOT NULL,
    previous_value DOUBLE PRECISION,
    previous_band VARCHAR(40),
    new_value DOUBLE PRECISION NOT NULL,
    new_band VARCHAR(40) NOT NULL,
    delta DOUBLE PRECISION NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by UUID,
    trigger_reason VARCHAR(40) NOT NULL
);

-- Customer segments
CREATE TABLE crm_customer_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    segment_code VARCHAR(80) NOT NULL,
    segment_name VARCHAR(200) NOT NULL,
    segment_type VARCHAR(40) NOT NULL,        -- MANUAL, RULE_BASED, AI_GENERATED
    description TEXT,
    criteria JSONB,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, segment_code)
);

-- Segment memberships
CREATE TABLE crm_segment_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    segment_id UUID NOT NULL,
    membership_type VARCHAR(40) NOT NULL,     -- MANUAL, AUTO
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE(tenant_id, account_id, segment_id)
);

-- Next Best Actions
CREATE TABLE crm_next_best_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    description TEXT,
    confidence DOUBLE PRECISION NOT NULL,
    reasoning TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED, EXPIRED
    generated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    human_confirmation_required BOOLEAN NOT NULL DEFAULT true,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Scoring models (configurable weights per tenant)
CREATE TABLE crm_scoring_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    score_type VARCHAR(40) NOT NULL,
    version VARCHAR(40) NOT NULL,
    weights JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, score_type, version)
);
```

### 4.2 Indexes

```sql
CREATE INDEX crm_customer_scores_tenant_account_idx
    ON crm_customer_scores(tenant_id, account_id, score_type, calculated_at DESC);
CREATE INDEX crm_customer_score_history_tenant_account_idx
    ON crm_customer_score_history(tenant_id, account_id, changed_at DESC);
CREATE INDEX crm_segment_memberships_tenant_account_idx
    ON crm_segment_memberships(tenant_id, account_id, active);
CREATE INDEX crm_next_best_actions_tenant_account_idx
    ON crm_next_best_actions(tenant_id, account_id, status, generated_at DESC);
```

---

## 5. Port/Adapter Design

### 5.1 New Ports

```java
// Customer intelligence query port (extends existing Customer360QueryPort)
public interface CustomerIntelligenceQueryPort {
    Optional<CustomerScores> findScores(UUID tenantId, UUID accountId);
    List<ScoreSnapshot> findScoreHistory(UUID tenantId, UUID accountId, String scoreType, int limit);
    List<NextBestAction> findNextBestActions(UUID tenantId, UUID accountId);
    List<SegmentMembership> findSegments(UUID tenantId, UUID accountId);
}

// Scoring port for write operations
public interface ScoringPort {
    CustomerScores saveScores(UUID tenantId, UUID accountId, CustomerScores scores, String triggerReason);
    ScoreSnapshot recordScoreChange(UUID tenantId, UUID accountId, CustomerScores scores, String triggerReason, UUID actorId);
    ScoringModel getActiveModel(UUID tenantId, String scoreType);
}

// Segment port
public interface SegmentPort {
    SegmentMembership assignSegment(UUID tenantId, UUID accountId, UUID segmentId, String membershipType, UUID actorId);
    List<SegmentMembership> findActiveSegments(UUID tenantId, UUID accountId);
    void deactivateSegment(UUID tenantId, UUID accountId, UUID segmentId);
}
```

### 5.2 AI Gateway Integration (Reuse CRM-009)

```java
// New AI capabilities for Customer Intelligence
public enum CustomerIntelligenceCapability {
    HEALTH_SCORING,         // Calculate customer health score
    CLV_FORECAST,           // Predict customer lifetime value
    CHURN_PREDICTION,       // Predict churn risk
    NEXT_BEST_ACTION,       // Already in AiGatewayPort.Capability
    SEGMENTATION,           // AI-driven customer segmentation
    OPPORTUNITY_DETECTION   // Detect upsell/cross-sell opportunities
}
```

---

## 6. Security Architecture

### 6.1 RBAC Capabilities

| Capability | Description |
|------------|-------------|
| CRM.CUSTOMER_360.READ | Read unified customer profile |
| CRM.CUSTOMER_INTELLIGENCE.READ | Read scores and insights |
| CRM.CUSTOMER_INTELLIGENCE.WRITE | Trigger rescoring |
| CRM.CUSTOMER_INTELLIGENCE.ADMIN | Manage scoring models |
| CRM.CUSTOMER_SEGMENT.MANAGE | Manage segments |

### 6.2 Security Constraints

- All endpoints require authenticated context
- All queries are tenant-scoped
- AI outputs require human confirmation for actionable recommendations
- Score changes are audited
- Segment changes are audited

---

## 7. API Design

### 7.1 Customer 360 Endpoints

| Method | Path | Capability | Description |
|--------|------|------------|-------------|
| GET | /api/v2/crm/customer-360/{accountId} | CRM.CUSTOMER_360.READ | Unified profile |
| GET | /api/v2/crm/customer-360/{accountId}/intelligence | CRM.CUSTOMER_INTELLIGENCE.READ | Scores + insights |
| GET | /api/v2/crm/customer-360/{accountId}/scores | CRM.CUSTOMER_INTELLIGENCE.READ | Score details |
| GET | /api/v2/crm/customer-360/{accountId}/scores/history | CRM.CUSTOMER_INTELLIGENCE.READ | Score history |
| GET | /api/v2/crm/customer-360/{accountId}/insights | CRM.CUSTOMER_INTELLIGENCE.READ | AI insights |
| GET | /api/v2/crm/customer-360/{accountId}/segments | CRM.CUSTOMER_360.READ | Segment memberships |
| POST | /api/v2/crm/customer-360/{accountId}/rescore | CRM.CUSTOMER_INTELLIGENCE.WRITE | Trigger rescore |
| GET | /api/v2/crm/customer-360/search | CRM.CUSTOMER_360.READ | Search customers |
| GET | /api/v2/crm/customer-360/analytics/segments | CRM.CUSTOMER_INTELLIGENCE.READ | Segment analytics |
| GET | /api/v2/crm/customer-360/analytics/trends | CRM.CUSTOMER_INTELLIGENCE.READ | Trend analytics |

---

## 8. Integration Architecture

### 8.1 Internal Integrations

| System | Integration | Pattern |
|--------|-------------|---------|
| CRM Customer Master | Read customer profile | Repository |
| CRM Activities | Aggregate engagement data | Query port |
| CRM Opportunities | Aggregate revenue data | Query port |
| CRM Timeline | Read/write timeline events | TimelineEventPort |
| CRM Audit | Record intelligence operations | AuditPort |
| CRM Workflow | Trigger workflows on thresholds | WorkflowIntegrationPort |
| AI Gateway | Request scores/insights | AiGatewayPort + outbox |
| Notifications | Alert on score changes | NotificationPort |

### 8.2 Future External Integrations

| System | Integration | Status |
|--------|-------------|--------|
| ERP | Revenue, orders, invoices | DEFERRED |
| Accounting | Financial health | DEFERRED |
| HR | Account team context | DEFERRED |
| Ecommerce | Purchase behavior | DEFERRED |
| POS | Transaction data | DEFERRED |

---

## 9. Related Artifacts

| Document | Purpose |
|----------|---------|
| CRM-010-CUSTOMER360-QUERYPORT-CONTRACT.md | Query port interface contract |
| CRM-010-AI-GATEWAY-CONTRACT.md | AI capability contracts (6 capabilities) |
| CRM-010-CUSTOMER360-ARCHITECTURE-ADR.md | Architecture decision record |
| CRM-010-DATABASE-MIGRATION.md | Migration plan (3 migrations) |
| CRM-010-INTEGRATION-MOCKS.md | External system mock strategy |
| CRM-010-AGENT-DEPENDENCIES.md | Agent execution dependency graph |
| CRM-010-STORY-GOVERNANCE.md | DoR/DoD and quality gates |
| CRM-010-CRITICAL-PATH.md | Critical path analysis |
| CRM-010-AI-CAPABILITY-SPECS.md | AI capability specifications |

---

**Architecture Blueprint Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
