/**
 * Dependencies Calculator
 * -----------------------
 * Calculates dependency relationships and detects cycles.
 */

import type { ExecutionGroup, ExecutionDependency } from "../types";

/**
 * Build dependency graph from groups.
 *
 * @param groups - Array of execution groups
 * @returns Array of dependency relationships
 */
export function buildDependencyGraph(groups: ExecutionGroup[]): ExecutionDependency[] {
  const dependencies: ExecutionDependency[] = [];

  for (const group of groups) {
    for (const depCode of group.dependencies) {
      dependencies.push({
        fromId: group.code,
        toId: depCode,
        type: "REQUIRES",
      });
    }
  }

  return dependencies;
}

/**
 * Check if adding a dependency would create a cycle.
 *
 * @param groups - Current groups
 * @param fromCode - Group that would depend on
 * @param toCode - Group that would be depended on
 * @returns true if adding this dependency would create a cycle
 */
export function wouldCreateCycle(
  groups: ExecutionGroup[],
  fromCode: string,
  toCode: string
): boolean {
  const visited = new Set<string>();
  const stack = [toCode];

  while (stack.length > 0) {
    const current = stack.pop()!;
    if (current === fromCode) return true;
    if (visited.has(current)) continue;
    visited.add(current);

    const group = groups.find((g) => g.code === current);
    if (group) {
      for (const dep of group.dependencies) {
        stack.push(dep);
      }
    }
  }

  return false;
}

/**
 * Topological sort of groups by dependencies.
 * Returns groups in execution order (earliest first).
 *
 * @param groups - Array of execution groups
 * @returns Array of group codes in execution order
 */
export function topologicalSort(groups: ExecutionGroup[]): string[] {
  const sorted: string[] = [];
  const visited = new Set<string>();
  const visiting = new Set<string>();

  function visit(code: string) {
    if (visited.has(code)) return;
    if (visiting.has(code)) return; // Cycle detected, skip
    visiting.add(code);

    const group = groups.find((g) => g.code === code);
    if (group) {
      for (const dep of group.dependencies) {
        visit(dep);
      }
    }

    visiting.delete(code);
    visited.add(code);
    sorted.push(code);
  }

  for (const group of groups) {
    visit(group.code);
  }

  return sorted;
}

/**
 * Get all groups that depend on a given group.
 *
 * @param groups - Array of execution groups
 * @param code - Group code to find dependents for
 * @returns Array of group codes that depend on the given group
 */
export function getDependents(groups: ExecutionGroup[], code: string): string[] {
  return groups
    .filter((g) => g.dependencies.includes(code))
    .map((g) => g.code);
}

/**
 * Get all dependencies of a given group (transitive).
 *
 * @param groups - Array of execution groups
 * @param code - Group code to find dependencies for
 * @returns Set of all dependency codes (transitive)
 */
export function getAllDependencies(groups: ExecutionGroup[], code: string): Set<string> {
  const deps = new Set<string>();
  const stack = [code];

  while (stack.length > 0) {
    const current = stack.pop()!;
    const group = groups.find((g) => g.code === current);
    if (group) {
      for (const dep of group.dependencies) {
        if (!deps.has(dep)) {
          deps.add(dep);
          stack.push(dep);
        }
      }
    }
  }

  return deps;
}
