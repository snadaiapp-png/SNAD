/**
 * G7 Entity Configuration
 *
 * Requirements: OFF-001 (Entity Subset), OFF-002 (Eligibility Rules),
 *               SYNC-015 (Entity Coverage), ARCH-004 (Hybrid Strategy)
 */

import { EntityType } from '../types';

export interface EntityConfig {
  type: EntityType;
  tableName: string;
  syncEnabled: boolean;
  pushOnly: boolean;
  pullOnly: boolean;
  autoMergeEnabled: boolean;
  userResolutionRequired: boolean;
  sensitiveFields: string[];
  displayName: string;
  displayNameArabic: string;
}

export const ENTITY_CONFIGS: Record<EntityType, EntityConfig> = {
  account: {
    type: 'account', tableName: 'crm_accounts', syncEnabled: true,
    pushOnly: false, pullOnly: false, autoMergeEnabled: true,
    userResolutionRequired: false, sensitiveFields: ['phone', 'email', 'address'],
    displayName: 'Account', displayNameArabic: 'حساب',
  },
  contact: {
    type: 'contact', tableName: 'crm_contacts', syncEnabled: true,
    pushOnly: false, pullOnly: false, autoMergeEnabled: true,
    userResolutionRequired: false, sensitiveFields: ['phone', 'email', 'address', 'notes'],
    displayName: 'Contact', displayNameArabic: 'جهة اتصال',
  },
  lead: {
    type: 'lead', tableName: 'crm_leads', syncEnabled: true,
    pushOnly: false, pullOnly: false, autoMergeEnabled: false,
    userResolutionRequired: true, sensitiveFields: ['phone', 'email', 'notes'],
    displayName: 'Lead', displayNameArabic: 'عميل محتمل',
  },
  opportunity: {
    type: 'opportunity', tableName: 'crm_opportunities', syncEnabled: true,
    pushOnly: false, pullOnly: false, autoMergeEnabled: false,
    userResolutionRequired: true, sensitiveFields: ['description'],
    displayName: 'Opportunity', displayNameArabic: 'فرصة',
  },
  task: {
    type: 'task', tableName: 'crm_tasks', syncEnabled: true,
    pushOnly: false, pullOnly: false, autoMergeEnabled: true,
    userResolutionRequired: false, sensitiveFields: ['description'],
    displayName: 'Task', displayNameArabic: 'مهمة',
  },
  note: {
    type: 'note', tableName: 'crm_notes', syncEnabled: true,
    pushOnly: true, pullOnly: false, autoMergeEnabled: false,
    userResolutionRequired: false, sensitiveFields: ['content'],
    displayName: 'Note', displayNameArabic: 'ملاحظة',
  },
  activity: {
    type: 'activity', tableName: 'crm_activities', syncEnabled: true,
    pushOnly: false, pullOnly: false, autoMergeEnabled: true,
    userResolutionRequired: false, sensitiveFields: ['description'],
    displayName: 'Activity', displayNameArabic: 'نشاط',
  },
};

export function getSyncableEntityTypes(): EntityType[] {
  return (Object.keys(ENTITY_CONFIGS) as EntityType[]).filter(type => ENTITY_CONFIGS[type].syncEnabled);
}

/** OFF-002: entities eligible for server -> device replication. */
export function getPullEligibleEntityTypes(): EntityType[] {
  return getSyncableEntityTypes().filter(type => !ENTITY_CONFIGS[type].pushOnly);
}

/** OFF-002: whether an entity may create an offline mutation. */
export function isPushEligible(type: EntityType): boolean {
  const config = ENTITY_CONFIGS[type];
  return Boolean(config?.syncEnabled && !config.pullOnly);
}

export function isPullEligible(type: EntityType): boolean {
  const config = ENTITY_CONFIGS[type];
  return Boolean(config?.syncEnabled && !config.pushOnly);
}

export function assertPushEligible(type: EntityType): void {
  if (!isPushEligible(type)) {
    throw new Error(`OFFLINE_ENTITY_NOT_PUSH_ELIGIBLE:${type}`);
  }
}

export function getEntityConfig(type: EntityType): EntityConfig {
  const config = ENTITY_CONFIGS[type];
  if (!config) throw new Error(`Unknown entity type: ${type}`);
  return config;
}

export function canAutoMerge(type: EntityType): boolean {
  return ENTITY_CONFIGS[type]?.autoMergeEnabled ?? false;
}

export function requiresUserResolution(type: EntityType): boolean {
  return ENTITY_CONFIGS[type]?.userResolutionRequired ?? false;
}

export function getSensitiveFields(type: EntityType): string[] {
  return ENTITY_CONFIGS[type]?.sensitiveFields ?? [];
}
