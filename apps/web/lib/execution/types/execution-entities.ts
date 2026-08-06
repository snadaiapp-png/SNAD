/**
 * SANAD Execution Framework — Canonical Entity Types
 * ---------------------------------------------------
 * These types define the execution model for all SANAD modules.
 * No module may define its own execution types.
 */

// ── Status Enums ─────────────────────────────────────────────────────────────

/** Status of an execution group */
export type GroupStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "BLOCKED"
  | "DONE"
  | "NEEDS_REVIEW"
  | "APPROVED"
  | "REJECTED";

/** Status of an execution task */
export type TaskStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "BLOCKED"
  | "DONE"
  | "NEEDS_REVIEW"
  | "APPROVED";

/** Type of work a task represents */
export type TaskType =
  | "Backend"
  | "Frontend"
  | "Database"
  | "API"
  | "Security"
  | "Test"
  | "Report"
  | "Mobile"
  | "AI"
  | "Billing"
  | "Design"
  | "DevOps"
  | "Documentation";

/** Priority level for tasks */
export type TaskPriority = "Critical" | "High" | "Medium" | "Low";

/** Certification status */
export type CertificationStatus =
  | "NOT_CERTIFIED"
  | "PENDING_REVIEW"
  | "CERTIFIED"
  | "REJECTED";

/** Type of evidence */
export type EvidenceType =
  | "SOURCE_CODE"
  | "DATABASE_MIGRATION"
  | "API_IMPLEMENTATION"
  | "FRONTEND_IMPLEMENTATION"
  | "TEST"
  | "DOCUMENTATION"
  | "CI_EVIDENCE"
  | "PRODUCTION_DEPLOYMENT"
  | "MANUAL_VERIFICATION";

// ── Core Entities ────────────────────────────────────────────────────────────

/**
 * Execution Program — Top-level container for all execution groups.
 * Example: "CRM Platform", "ERP System", "POS Module"
 */
export interface ExecutionProgram {
  /** Unique program identifier */
  id: string;
  /** Program code (e.g., "CRM", "ERP", "POS") */
  code: string;
  /** Program name in Arabic */
  titleAr: string;
  /** Program name in English */
  titleEn: string;
  /** Program description in Arabic */
  descriptionAr: string;
  /** Program description in English */
  descriptionEn: string;
  /** Current program status */
  status: GroupStatus;
  /** All groups in this program */
  groups: ExecutionGroup[];
}

/**
 * Execution Group — A logical grouping of milestones and tasks.
 * Example: "G0: Execution Control", "G1: Database Foundation"
 */
export interface ExecutionGroup {
  /** Unique group identifier */
  id: string;
  /** Group code (e.g., "G0", "G1", "G2") */
  code: string;
  /** Group title in Arabic */
  titleAr: string;
  /** Group title in English */
  titleEn: string;
  /** Group purpose in Arabic */
  purposeAr: string;
  /** Group purpose in English */
  purposeEn: string;
  /** Current group status */
  status: GroupStatus;
  /** IDs of groups this group depends on */
  dependencies: string[];
  /** IDs of groups that can run in parallel with this group */
  canParallelizeWith: string[];
  /** Stage report content (null if not yet generated) */
  stageReport: string | null;
  /** Milestones within this group */
  milestones: ExecutionMilestone[];
  /** Tasks within this group (flat, not nested in milestones) */
  tasks: ExecutionTask[];
}

/**
 * Execution Milestone — A checkpoint within a group.
 * Example: "Database Schema Complete", "API Tests Passing"
 */
export interface ExecutionMilestone {
  /** Unique milestone identifier */
  id: string;
  /** Milestone code (e.g., "M1", "M2") */
  code: string;
  /** Milestone title in Arabic */
  titleAr: string;
  /** Milestone title in English */
  titleEn: string;
  /** Milestone description */
  description: string;
  /** Current milestone status */
  status: TaskStatus;
  /** IDs of tasks that must complete before this milestone */
  taskDependencies: string[];
  /** Acceptance criteria for this milestone */
  acceptanceCriteria: AcceptanceCriteria[];
}

/**
 * Execution Task — A unit of work that can be completed.
 * Example: "Create CRM extension tables", "Write Playwright tests"
 */
export interface ExecutionTask {
  /** Unique task identifier */
  id: string;
  /** Task number within the group (e.g., "G0-01") */
  number: string;
  /** Task title in Arabic */
  nameAr: string;
  /** Task title in English */
  nameEn: string;
  /** ID of the group this task belongs to */
  groupCode: string;
  /** Task description in Arabic */
  descriptionAr: string;
  /** Task description in English */
  descriptionEn: string;
  /** Type of work */
  type: TaskType;
  /** Task priority */
  priority: TaskPriority;
  /** Current task status */
  status: TaskStatus;
  /** IDs of tasks this task depends on */
  dependencies: string[];
  /** Acceptance criteria for this task */
  acceptanceCriteriaAr: string;
  /** Implementation notes */
  implementationNotesAr: string;
  /** Evidence supporting this task's completion */
  evidence: ExecutionEvidence[];
}

/**
 * Execution Evidence — Proof that work was completed.
 * Example: "Flyway migration V20260716_1 applied", "22 Testcontainers tests pass"
 */
export interface ExecutionEvidence {
  /** Unique evidence identifier */
  id: string;
  /** Type of evidence */
  type: EvidenceType;
  /** Evidence title */
  title: string;
  /** Evidence description */
  description: string;
  /** Path to evidence file or artifact */
  path?: string;
  /** SHA-256 hash of evidence artifact */
  hash?: string;
  /** When the evidence was created */
  createdAt: Date;
  /** Who created the evidence */
  createdBy: string;
}

/**
 * Acceptance Criteria — Conditions that must be met for certification.
 * Example: "PostgreSQL rejects cross-tenant writes", "All 26 indexes exist"
 */
export interface AcceptanceCriteria {
  /** Unique criteria identifier */
  id: string;
  /** Criteria description in Arabic */
  descriptionAr: string;
  /** Criteria description in English */
  descriptionEn: string;
  /** Whether this criteria has been met */
  passed: boolean;
  /** Evidence supporting this criteria */
  evidenceId?: string;
}

/**
 * Certification — Formal approval of a group or milestone.
 */
export interface Certification {
  /** Unique certification identifier */
  id: string;
  /** ID of the entity being certified */
  entityId: string;
  /** Type of entity being certified */
  entityType: "PROGRAM" | "GROUP" | "MILESTONE";
  /** Current certification status */
  status: CertificationStatus;
  /** All acceptance criteria that must pass */
  acceptanceCriteria: AcceptanceCriteria[];
  /** When certification was granted */
  certifiedAt?: Date;
  /** Who granted certification */
  certifiedBy?: string;
  /** Certification notes */
  notes?: string;
}

/**
 * ExecutionProgress — Calculated progress for an entity.
 */
export interface ExecutionProgress {
  /** Total number of items */
  total: number;
  /** Number of completed items */
  done: number;
  /** Number of approved items */
  approved: number;
  /** Number of in-progress items */
  inProgress: number;
  /** Number of blocked items */
  blocked: number;
  /** Number of not-started items */
  notStarted: number;
  /** Number of needs-review items */
  needsReview: number;
  /** Progress percentage (0-100) */
  percentage: number;
}

/**
 * ExecutionDependency — A dependency between two entities.
 */
export interface ExecutionDependency {
  /** ID of the dependent entity */
  fromId: string;
  /** ID of the dependency entity */
  toId: string;
  /** Type of dependency */
  type: "BLOCKS" | "ENABLES" | "REQUIRES";
}

/**
 * ExecutionArtifact — A file or artifact produced by execution.
 */
export interface ExecutionArtifact {
  /** Unique artifact identifier */
  id: string;
  /** Artifact name */
  name: string;
  /** Artifact type */
  type: "SOURCE_CODE" | "MIGRATION" | "TEST" | "DOCUMENT" | "CONFIG" | "DEPLOYMENT";
  /** Path to the artifact */
  path: string;
  /** SHA-256 hash */
  hash: string;
  /** When the artifact was created */
  createdAt: Date;
}
