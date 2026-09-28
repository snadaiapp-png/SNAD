import type { ExecutionGroup } from "../../lib/execution";

/**
 * Authoritative G2 engineering closure, bound to the exact main SHA and
 * post-merge evidence that completed the governed G2 closure.
 */
export const HR_G2_CLOSURE = {
  implementation: "DONE" as const,
  closureEvidenceMainSha: "24d7a52b3696b66bca2380433b51045a35c74d66",
  authenticatedAcceptanceRunId: 36344192747,
  humanPreviewRunId: 36344192703,
  postMergeVerificationRunId: 36344192701,
  visualEvidenceCases: 60,
  visualEvidencePasses: 60,
} as const;

/**
 * Reconciles the historical execution-roadmap seed with the governed G2
 * closure. Only G2 is affected; no later execution group is advanced.
 */
export function reconcileG2ExecutionGroup(group: ExecutionGroup): ExecutionGroup {
  if (group.code !== "G2" || HR_G2_CLOSURE.implementation !== "DONE") {
    return group;
  }

  return {
    ...group,
    status: "DONE",
    tasks: group.tasks.map((task) => ({
      ...task,
      status: "DONE",
    })),
  };
}
