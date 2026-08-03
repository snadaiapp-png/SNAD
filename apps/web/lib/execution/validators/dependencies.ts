/**
 * Dependencies Validator
 * ----------------------
 * Validates dependency integrity for execution groups.
 */

import type { ExecutionGroup } from "../types";
import type { ValidationResult } from "./progress";
import { wouldCreateCycle } from "../calculators";

/**
 * Validate dependency integrity for a group.
 *
 * Rules:
 * - All dependency references must exist
 * - No circular dependencies
 * - Dependencies must be valid group codes
 *
 * @param groups - All execution groups
 * @returns Array of validation results
 */
export function validateDependencyIntegrity(groups: ExecutionGroup[]): ValidationResult[] {
  const results: ValidationResult[] = [];
  const groupCodes = new Set(groups.map((g) => g.code));

  for (const group of groups) {
    // Rule: All dependency references must exist
    const allRefsExist = group.dependencies.every((dep) => groupCodes.has(dep));
    results.push({
      rule: `Dependency references for ${group.code}`,
      passed: allRefsExist,
      message: allRefsExist
        ? `${group.code} all dependencies exist`
        : `${group.code} has invalid dependency references`,
    });

    // Rule: No self-dependency
    const noSelfDep = !group.dependencies.includes(group.code);
    results.push({
      rule: `No self-dependency for ${group.code}`,
      passed: noSelfDep,
      message: noSelfDep
        ? `${group.code} has no self-dependency`
        : `${group.code} depends on itself`,
    });
  }

  // Rule: No circular dependencies (check each pair)
  for (const group of groups) {
    for (const dep of group.dependencies) {
      const hasCycle = wouldCreateCycle(groups, group.code, dep);
      if (hasCycle) {
        results.push({
          rule: `Circular dependency check for ${group.code} → ${dep}`,
          passed: false,
          message: `Circular dependency detected: ${group.code} → ${dep}`,
        });
      }
    }
  }

  // If no cycles found, add a passing result
  const cycleResults = results.filter((r) => r.rule.startsWith("Circular dependency"));
  if (cycleResults.length === 0) {
    results.push({
      rule: "No circular dependencies",
      passed: true,
      message: "No circular dependencies detected",
    });
  }

  return results;
}
