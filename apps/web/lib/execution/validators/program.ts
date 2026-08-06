/**
 * Program Validator
 * -----------------
 * Comprehensive validation for an execution program.
 */

import type { ExecutionProgram, Certification } from "../types";
import type { ValidationResult } from "./progress";
import { validateExecutionGroup } from "./group";
import { validateDependencyIntegrity } from "./dependencies";
import { validateProgramConsistency } from "./consistency";

/**
 * Run all validations for an execution program.
 *
 * @param program - The execution program
 * @param certifications - Map of group code to certification
 * @returns Array of all validation results
 */
export function validateExecutionProgram(
  program: ExecutionProgram,
  certifications?: Map<string, Certification>
): ValidationResult[] {
  const results: ValidationResult[] = [];

  // Program-level consistency
  results.push(...validateProgramConsistency(program));

  // Dependency integrity
  results.push(...validateDependencyIntegrity(program.groups));

  // Group-level validation
  for (const group of program.groups) {
    const cert = certifications?.get(group.code);
    results.push(...validateExecutionGroup(group, cert));
  }

  return results;
}

/**
 * Check if a program passes all validations.
 *
 * @param program - The execution program
 * @param certifications - Map of group code to certification
 * @returns true if all validations pass
 */
export function isProgramValid(
  program: ExecutionProgram,
  certifications?: Map<string, Certification>
): boolean {
  const results = validateExecutionProgram(program, certifications);
  return results.every((r) => r.passed);
}

/**
 * Get validation summary for a program.
 *
 * @param program - The execution program
 * @param certifications - Map of group code to certification
 * @returns Summary object
 */
export function getValidationSummary(
  program: ExecutionProgram,
  certifications?: Map<string, Certification>
): {
  totalRules: number;
  passed: number;
  failed: number;
  allPassed: boolean;
  results: ValidationResult[];
} {
  const results = validateExecutionProgram(program, certifications);
  const passed = results.filter((r) => r.passed).length;
  const failed = results.filter((r) => !r.passed).length;

  return {
    totalRules: results.length,
    passed,
    failed,
    allPassed: failed === 0,
    results,
  };
}
