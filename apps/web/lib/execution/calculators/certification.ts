/**
 * Certification Calculator
 * ------------------------
 * Determines certification status based on acceptance criteria.
 */

import type { ExecutionGroup, Certification, CertificationStatus } from "../types";
import { calculateGroupProgress } from "./group-progress";

/**
 * Determine if a group is eligible for certification.
 *
 * Rules:
 * - All tasks must be DONE or APPROVED
 * - All acceptance criteria must pass
 * - Progress must be 100%
 *
 * @param group - The execution group
 * @param certification - The certification record
 * @returns true if the group is eligible for certification
 */
export function isEligibleForCertification(
  group: ExecutionGroup,
  certification: Certification
): boolean {
  const progress = calculateGroupProgress(group);

  // Rule: Progress must be 100%
  if (progress.percentage !== 100) return false;

  // Rule: All tasks must be DONE or APPROVED
  const allTasksComplete = group.tasks.every(
    (t) => t.status === "DONE" || t.status === "APPROVED"
  );
  if (!allTasksComplete) return false;

  // Rule: All acceptance criteria must pass
  const allCriteriaPass = certification.acceptanceCriteria.every((c) => c.passed);
  if (!allCriteriaPass) return false;

  return true;
}

/**
 * Calculate certification status for a group.
 *
 * @param group - The execution group
 * @param certification - The certification record
 * @returns CertificationStatus
 */
export function calculateCertificationStatus(
  group: ExecutionGroup,
  certification: Certification
): CertificationStatus {
  if (certification.status === "CERTIFIED") return "CERTIFIED";
  if (certification.status === "REJECTED") return "REJECTED";

  if (isEligibleForCertification(group, certification)) {
    return "PENDING_REVIEW";
  }

  return "NOT_CERTIFIED";
}
