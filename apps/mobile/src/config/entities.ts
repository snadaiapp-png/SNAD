/**
 * G7 Entity Configuration
 *
 * Requirements: OFF-001 (Entity Subset), SYNC-015 (Entity Coverage)
 *
 * Defines which entities are synced, their sync policies,
 * and which fields are sensitive (encrypted at rest).
 */

import { EntityType } from '../types';

export interface EntityConfig {
  type: EntityType;
  tableName: string;
  syncEnabled: boolean;
  pushOnly: boolean;       // Note: push-only entities accept client mutations but don't pull from server
  pullOnly: boolean;       // Pipeline, Tags, Custom Fields: pull from server but reject client mutations
  autoMergeEnabled: boolean; // Account, Contact, Task, Activity: auto-merge non-conflicting fields
  userResolutionRequired: boolean; // Lead, Opportunity, Pipeline, Tags, Custom Fields: user must resolve conflicts
  sensitiveFields: string[];
  displayName: string;
  displayNameArabic: string;
}

/**
 * Entity policies per ADR-G7-001 Hybrid Policy.
 *
 * | Entity      | Strategy                          | Auto-Merge? | User Resolution? |
 * |-------------|-----------------------------------|-------------|-----------------|
 * | Account     | Reject + Auto-Merge Non-Conflicting | YES      | Only overlapping |
 * | Contact     | Reject + Auto-Merge Non-Conflicting | YES      | Only overlapping |
 * | Lead        | Reject + User Resolution           | NO          | YES — always    |
 * | Opportunity | Reject + User Resolution           | NO          | YES — always    |
 * | Task        | Reject + Auto-Merge               | YES         | Only overlapping |
 * | Activity    | Reject + Auto-Merge Non-Conflicting | YES      | Only overlapping |
 * | Note        | Push-Only (Archive only)           | N/A         | NO              |
 * | Pipeline    | Reject + User Resolution           | NO          | YES — always    |
 * | Tags        | Reject + User Resolution           | NO          | YES — always    |
 * | Custom Fields | Reject + User Resolution        | NO          | YES — always    |
 */
export const ENTITY_CONFIGS: Record<EntityType, EntityConfig> = {
  account: {
    type: 'account',
    tableName: 'crm_accounts',
    syncEnabled: true,
    pushOnly: false,
    pullOnly: false,
    autoMergeEnabled: true,
    userResolutionRequired: false,
    sensitiveFields: ['phone', 'email', 'address'],
    displayName: 'Account',
    displayNameArabic: 'حساب',
  },
  contact: {
    type: 'contact',
    tableName: 'crm_contacts',
    syncEnabled: true,
    pushOnly: false,
    pullOnly: false,
    autoMergeEnabled: true,
    userResolutionRequired: false,
    sensitiveFields: ['phone', 'email', 'address', 'notes'],
    displayName: 'Contact',
    displayNameArabic: 'جهة اتصال',
  },
  lead: {
    type: 'lead',
    tableName: 'crm_leads',
    syncEnabled: true,
    pushOnly: false,
    pullOnly: false,
    autoMergeEnabled: false,
    userResolutionRequired: true,
    sensitiveFields: ['phone', 'email', 'notes'],
    displayName: 'Lead',
    displayNameArabic: 'عميل محتمل',
  },
  opportunity: {
    type: 'opportunity',
    tableName: 'crm_opportunities',
    syncEnabled: true,
    pushOnly: false,
    pullOnly: false,
    autoMergeEnabled: false,
    userResolutionRequired: true,
    sensitiveFields: ['description'],
    displayName: 'Opportunity',
    displayNameArabic: 'فرصة',
  },
  task: {
    type: 'task',
    tableName: 'crm_tasks',
    syncEnabled: true,
    pushOnly: false,
    pullOnly: false,
    autoMergeEnabled: true,
    userResolutionRequired: false,
    sensitiveFields: ['description'],
    displayName: 'Task',
    displayNameArabic: 'مهمة',
  },
  note: {
    type: 'note',
    tableName: 'crm_notes',
    syncEnabled: true,
    pushOnly: true,
    pullOnly: false,
    autoMergeEnabled: false,
    userResolutionRequired: false,
    sensitiveFields: ['content'],
    displayName: 'Note',
    displayNameArabic: 'ملاحظة',
  },
  activity: {
    type: 'activity',
    tableName: 'crm_activities',
    syncEnabled: true,
    pushOnly: false,
    pullOnly: false,
    autoMergeEnabled: true,
    userResolutionRequired: false,
    sensitiveFields: ['description'],
    displayName: 'Activity',
    displayNameArabic: 'نشاط',
  },
};

/**
 * Get all syncable entity types.
 */
export function getSyncableEntityTypes(): EntityType[] {
  return (Object.keys(ENTITY_CONFIGS) as EntityType[]).filter(
    (type) => ENTITY_CONFIGS[type].syncEnabled
  );
}

/**
 * Get entity config by type.
 */
export function getEntityConfig(type: EntityType): EntityConfig {
  const config = ENTITY_CONFIGS[type];
  if (!config) {
    throw new Error(`Unknown entity type: ${type}`);
  }
  return config;
}

/**
 * Check if entity type allows auto-merge.
 */
export function canAutoMerge(type: EntityType): boolean {
  return ENTITY_CONFIGS[type]?.autoMergeEnabled ?? false;
}

/**
 * Check if entity type requires user resolution.
 */
export function requiresUserResolution(type: EntityType): boolean {
  return ENTITY_CONFIGS[type]?.userResolutionRequired ?? false;
}

/**
 * Get sensitive fields for entity type.
 */
export function getSensitiveFields(type: EntityType): string[] {
  return ENTITY_CONFIGS[type]?.sensitiveFields ?? [];
}
