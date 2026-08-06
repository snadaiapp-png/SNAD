/**
 * Group Validator
 * ---------------
 * Comprehensive validation for an execution group.
 */

import type { ExecutionGroup, Certification } from "../types";
import type { ValidationResult } from "./progress";
import { validateProgressIntegrity } from "./progress";
import { validateCertificationIntegrity } from "./certification";
import { validateEvidenceIntegrity } from "./evidence";
import { validateTaskIntegrity } from "./tasks";
import { validateCrossLayerConsistency } from "./consistency";

/**
 * Run all validations for an execution group.
 *
 * @param group - The execution group
 * @param certification - Optional certification record
 * @returns Array of all validation results
 */
export function validateExecutionGroup(
  group: ExecutionGroup,
  certification?: Certification
): ValidationResult[] {
  const results: ValidationResult[] = [];

  // Progress validation
  results.push(...validateProgressIntegrity(group));

  // Task validation
  results.push(...validateTaskIntegrity(group));

  // Evidence validation
  results.push(...validateEvidenceIntegrity(group));

  // Cross-layer consistency
  results.push(...validateCrossLayerConsistency(group));

  // Certification validation (if provided)
  if (certification) {
    results.push(...validateCertificationIntegrity(group, certification));
  }

  return results;
}

/**
 * Check if a group passes all validations.
 *
 * @param group - The execution group
 * @param certification - Optional certification record
 * @returns true if all validations pass
 */
export function isGroupValid(
  group: ExecutionGroup,
  certification?: Certification
): boolean {
  const results = validateExecutionGroup(group, certification);
  return results.every((r) => r.passed);
}
