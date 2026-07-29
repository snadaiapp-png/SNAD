# CRM-010 Use Case Catalog

## Customer 360 Use Cases

| Use Case | Service Method | Description |
|----------|---------------|-------------|
| `LoadCustomer360` | `Customer360ApplicationService.loadCustomer360()` | Loads unified 360 view combining profile, scores, NBAs, and segments |
| `GetCustomerScores` | `Customer360ApplicationService.getScores()` | Retrieves latest scores (cache-through) |
| `GetCustomerScoreHistory` | `Customer360ApplicationService.getScoreHistory()` | Queries score change history |

## Scoring Use Cases

| Use Case | Service Method | Description |
|----------|---------------|-------------|
| `CalculateHealthScore` | `CustomerScoringService.calculateHealthScore()` | Computes health score via AI + rule-based fallback, persists, publishes events |
| `CalculateCLV` | `CustomerLifetimeValueService.calculateCLV()` | Computes customer lifetime value with AI-enhanced projection |
| `CalculateChurnRisk` | `ChurnPredictionService.predictChurnRisk()` | Predicts churn risk using AI + rule-based fallback |
| `RefreshAllScores` | `CustomerScoringService.refreshAllScores()` | Recalculates all score types for an account |
| `GetLatestHealth` | `CustomerHealthService.getLatestHealth()` | Retrieves latest health score |
| `GetLatestCLV` | `CustomerLifetimeValueService.getLatestCLV()` | Retrieves latest CLV |
| `GetLatestRisk` | `ChurnPredictionService.getLatestRisk()` | Retrieves latest risk score |

## Segmentation Use Cases

| Use Case | Service Method | Description |
|----------|---------------|-------------|
| `CreateSegment` | `CustomerSegmentationService.createSegment()` | Creates a new segment definition |
| `AddCustomerToSegment` | `CustomerSegmentationService.addCustomerToSegment()` | Assigns customer to segment |
| `RemoveCustomerFromSegment` | `CustomerSegmentationService.removeCustomerFromSegment()` | Deactivates customer membership |
| `GetActiveSegments` | `CustomerSegmentationService.getActiveSegments()` | Lists active segment memberships |
| `GetAllSegments` | `CustomerSegmentationService.getAllSegments()` | Lists all segment definitions |
| `FindBySegmentCode` | `CustomerSegmentationService.findByCode()` | Finds segment by unique code |

## Next Best Action Use Cases

| Use Case | Service Method | Description |
|----------|---------------|-------------|
| `GenerateRecommendation` | `NextBestActionService.generateRecommendation()` | Creates NBA recommendation with 7-day expiry |
| `AcceptRecommendation` | `NextBestActionService.acceptRecommendation()` | Transitions NBA to ACCEPTED (optimistic locking) |
| `RejectRecommendation` | `NextBestActionService.rejectRecommendation()` | Transitions NBA to REJECTED |
| `ExpireStaleRecommendations` | `NextBestActionService.expireStaleRecommendations()` | Expires all stale NBAs for a tenant |
| `GetPendingActions` | `NextBestActionService.getPendingActions()` | Lists pending NBAs for an account |

## Opportunity Use Cases

| Use Case | Service Method | Description |
|----------|---------------|-------------|
| `DetectOpportunity` | `OpportunityScoringService.detectOpportunity()` | AI-driven opportunity detection with NBA generation |

## Insight Use Cases

| Use Case | Service Method | Description |
|----------|---------------|-------------|
| `GetCustomerInsights` | `CustomerInsightService.getCustomerInsights()` | Aggregated intelligence summary (scores + NBAs + segments + assessment) |
