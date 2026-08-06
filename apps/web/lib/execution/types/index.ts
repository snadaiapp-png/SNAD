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
