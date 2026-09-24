/**
 * G7 Mobile Offline Foundation — Core Type Definitions
 *
 * Requirements: DATA-003 (Local Storage Schema), OFF-001 (Entity Subset)
 */

// ============================================================
// Entity Types
// ============================================================

export type EntityType = 'account' | 'contact' | 'lead' | 'opportunity' | 'task' | 'note' | 'activity';

export interface BaseEntity {
  id: string;
  tenant_id: string;
  sync_version: number;
  created_at: string;
  updated_at: string;
  last_synced_at?: string;
  deleted_at?: string;
}

export interface Account extends BaseEntity {
  name: string;
  industry?: string;
  phone?: string;   // encrypted
  email?: string;   // encrypted
  website?: string;
  address?: string;  // encrypted
}

export interface Contact extends BaseEntity {
  account_id?: string;
  first_name: string;
  last_name: string;
  email?: string;    // encrypted
  phone?: string;    // encrypted
  address?: string;  // encrypted
  notes?: string;    // encrypted
}

export interface Lead extends BaseEntity {
  first_name: string;
  last_name: string;
  email?: string;    // encrypted
  phone?: string;    // encrypted
  status: string;
  source?: string;
  notes?: string;    // encrypted
}

export interface Opportunity extends BaseEntity {
  account_id?: string;
  contact_id?: string;
  pipeline_id?: string;
  title: string;
  amount?: number;
  stage: string;
  close_date?: string;
  description?: string; // encrypted
}

export interface Task extends BaseEntity {
  title: string;
  description?: string; // encrypted
  status: string;
  due_date?: string;
  assigned_to?: string;
}

export interface Note extends BaseEntity {
  entity_type: string;
  entity_id: string;
  content: string;    // encrypted
}

export interface Activity extends BaseEntity {
  entity_type: string;
  entity_id: string;
  activity_type: string;
  description?: string; // encrypted
  result?: string;
}

// ============================================================
// Sync Types
// ============================================================

export type SyncState = 'ONLINE' | 'OFFLINE' | 'REAUTH_REQUIRED' | 'FULL_RESYNC_REQUIRED' | 'SYNC_BLOCKED';

export type MutationOperation = 'CREATE' | 'UPDATE' | 'DELETE';

export type MutationStatus = 'QUEUED' | 'SENDING' | 'APPLIED' | 'REJECTED' | 'CONFLICT' | 'FAILED';

export interface Mutation {
  id: string;
  idempotencyKey: string;
  entityType: EntityType;
  entityId: string;
  operation: MutationOperation;
  expectedVersion?: number;
  payload: Record<string, any>;
  clientTimestamp: string;
  status: MutationStatus;
  retryCount: number;
  maxRetries: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DeltaSyncResponse {
  entityType: string;
  nextCursor: string | null;
  entityCount: number;
  entities: EntityDelta[];
  serverTimestamp: string;
  hasMore: boolean;
}

export interface EntityDelta {
  entityId: string;
  operation: 'CREATE' | 'UPDATE' | 'DELETE';
  version: number;
  data: Record<string, any>;
  updatedAt: string;
}

export interface PushSyncRequest {
  mutations: Array<{
    idempotencyKey: string;
    entityType: string;
    entityId: string;
    operation: MutationOperation;
    expectedVersion?: number;
    payload: Record<string, any>;
  }>;
}

export interface PushSyncResponse {
  totalMutations: number;
  applied: number;
  rejected: number;
  duplicates: number;
  results: Array<{
    idempotencyKey: string;
    entityId: string;
    status: 'APPLIED' | 'REJECTED' | 'CONFLICT' | 'DUPLICATE';
    httpStatus: string;
    serverVersion?: number;
    etag?: string;
    conflictInfo?: any;
    errorMessage?: string;
  }>;
}

// ============================================================
// Conflict Types
// ============================================================

export type ConflictClass = 'C1' | 'C2' | 'C3' | 'C4' | 'C5' | 'C6' | 'C7' | 'C8' | 'C9' | 'C10' | 'C11' | 'C12';

export type ConflictStatus = 'OPEN' | 'RESOLUTION_PENDING' | 'RESOLVED' | 'EXPIRED' | 'ARCHIVED';

export type ConflictResolution = 'CLIENT_WINS' | 'SERVER_WINS' | 'MERGED' | 'USER_CHOICE';

export interface Conflict {
  conflictId: string;
  entityType: EntityType;
  entityId: string;
  conflictType: string;
  conflictClass: ConflictClass;
  status: ConflictStatus;
  clientVersion: number;
  serverVersion: number;
  clientState: Record<string, any>;
  serverState: Record<string, any>;
  resolution?: ConflictResolution;
  detectedAt: string;
  resolvedAt?: string;
}

// ============================================================
// Auth Types
// ============================================================

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export interface DeviceInfo {
  deviceId: string;
  platform: 'ios' | 'android';
  deviceName: string;
  appVersion: string;
}

// ============================================================
// Encryption Types
// ============================================================

export interface EncryptedField {
  value: string;    // Base64-encoded ciphertext
  iv: string;       // Base64-encoded IV
  tag: string;      // Base64-encoded GCM tag
}
