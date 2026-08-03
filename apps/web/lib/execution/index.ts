/**
 * SANAD Execution Framework
 * --------------------------
 * The official execution framework for all SANAD modules.
 *
 * Architecture:
 *   Execution Engine → Module Data Provider → Validation → API → UI
 *
 * Usage:
 *   import { calculateGroupProgress, validateExecutionGroup } from "@/lib/execution";
 *
 * Every SANAD module SHALL use this framework instead of implementing
 * its own execution logic.
 */

// ── Types ────────────────────────────────────────────────────────────────────
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
} from "./types";

// ── Calculators ──────────────────────────────────────────────────────────────
export {
  calculateGroupProgress,
  calculateProgramProgress,
  calculateGroupProgressMap,
  calculateCertificationStatus,
  isEligibleForCertification,
  buildDependencyGraph,
  topologicalSort,
  getDependents,
  getAllDependencies,
  getGroupEvidenceCoverage,
  hasSufficientEvidence,
} from "./calculators";

// ── Validators ───────────────────────────────────────────────────────────────
export {
  validateProgressIntegrity,
  validateCertificationIntegrity,
  validateEvidenceIntegrity,
  validateDependencyIntegrity,
  validateTaskIntegrity,
  validateCrossLayerConsistency,
  validateExecutionGroup,
  validateExecutionProgram,
  isGroupValid,
  isProgramValid,
  getValidationSummary,
} from "./validators";

// ── Providers ────────────────────────────────────────────────────────────────
export type { ExecutionProvider } from "./providers";
export { InMemoryExecutionProvider } from "./providers/execution-provider";

// ── Hooks ────────────────────────────────────────────────────────────────────
export {
  useGroupProgress,
  useProgramProgress,
  useGroupProgressMap,
  useGroupValidation,
  useProgramValidation,
  useExecutionProvider,
} from "./hooks";

// ── Constants ────────────────────────────────────────────────────────────────
export {
  GROUP_STATUS_LABELS_AR,
  GROUP_STATUS_LABELS_EN,
  TASK_STATUS_LABELS_AR,
  TASK_STATUS_LABELS_EN,
  TASK_TYPE_LABELS_AR,
  TASK_TYPE_LABELS_EN,
  PRIORITY_LABELS_AR,
  PRIORITY_LABELS_EN,
  STATUS_COLORS,
  EXECUTION_RULES,
} from "./constants";
