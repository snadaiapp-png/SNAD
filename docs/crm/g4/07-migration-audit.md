# G4 Migration Audit

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe

## Migration Summary

| Metric | Value |
|--------|-------|
| Total Migration Files | 50 |
| Applied to Production | 36 |
| Pending | 14 |
| Out-of-Order Allowed | Yes (SPRING_FLYWAY_OUT_OF_ORDER=true) |
| Migration Drift | 0 |

## G4-Related Migrations

| Migration | Table | Status |
|-----------|-------|--------|
| V50__crm_init.sql | crm_pipeline, crm_opportunity, crm_stage, crm_lead, crm_contact | Applied |
| V51__crm_add_stages.sql | crm_stage (added pipeline_id, position) | Applied |

## Migration Health

| Check | Status |
|-------|--------|
| All applied migrations checksums match | ✅ |
| No failed migrations | ✅ |
| No pending G4 migrations | ✅ |
| Out-of-order migrations handled | ✅ |
| No migration drift detected | ✅ |

## Database Schema (G4 Tables)

### crm_pipeline
| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| name | VARCHAR(160) | NOT NULL |
| currency_code | VARCHAR(3) | |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

### crm_opportunity
| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| pipeline_id | UUID | FK → crm_pipeline |
| stage_id | UUID | FK → crm_stage |
| name | VARCHAR(160) | NOT NULL |
| value | DECIMAL | |
| currency_code | VARCHAR(3) | |
| account_id | UUID | FK → crm_account |
| contact_id | UUID | FK → crm_contact |
| lead_id | UUID | FK → crm_lead |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

### crm_stage
| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| pipeline_id | UUID | FK → crm_pipeline |
| name | VARCHAR(160) | NOT NULL |
| position | INT | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

### crm_lead
| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | |
| phone | VARCHAR(50) | |
| company | VARCHAR(200) | |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

### crm_contact
| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | |
| phone | VARCHAR(50) | |
| account_id | UUID | FK → crm_account |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |
