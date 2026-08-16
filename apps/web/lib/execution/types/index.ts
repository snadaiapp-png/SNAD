/**
 * SANAD Execution Framework — Core Types
 * ----------------------------------------
 * Canonical entity types for the execution model.
 * Every SANAD module SHALL use these types.
 */

export type {
  ExecutionProgram,
  ExecutionGroup,
  ExecutionMilestone,
  ExecutionTask,
  ExecutionEvidence,
  AcceptanceCriteria,
  Certification,
  ExecutionProgress,
  ExecutionDependency,
  ExecutionArtifact,
  GroupStatus,
  TaskStatus,
  TaskType,
  TaskPriority,
  CertificationStatus,
  EvidenceType,
} from "./execution-entities";

// Re-export the ExecutionProvider interface so consumers can import the
// full execution contract from a single canonical location.
// The provider contract is the platform-wide interface that every module
// provider (CRM, ERP, POS, etc.) must satisfy — without this re-export,
// importing ExecutionProvider from "./types" fails with TS2305.
export type { ExecutionProvider } from "../providers/execution-provider";
