import { apiClient } from "./client";

/**
 * HRM G3 Task 5 — typed facade for the performance-goals SELF surface.
 *
 * Routes mirror the canonical backend contract exactly:
 *   GET    /api/v2/hr/performance/goals                     (GOAL.SELF_VIEW)
 *   GET    /api/v2/hr/performance/goals/{goalId}            (GOAL.SELF_VIEW)
 *   POST   /api/v2/hr/performance/goals                     (GOAL.SELF_UPDATE)
 *   PUT    /api/v2/hr/performance/goals/{goalId}            (GOAL.SELF_UPDATE)
 *   PATCH  /api/v2/hr/performance/goals/{goalId}/progress   (GOAL.SELF_UPDATE)
 *
 * SELF identity (tenant/user/employment) is derived by the backend from the
 * authenticated principal — the facade never sends caller-supplied authority
 * ids. Transport is owned exclusively by apiClient; mutations carry the same
 * Idempotency-Key convention as the HR G2 facade. Review/TEAM endpoints are
 * intentionally absent (Task 6 scope).
 */

const ROOT = "/api/v2/hr/performance/goals";

function mutationOptions() {
  return {
    context: {
      headers: {
        "Idempotency-Key": globalThis.crypto.randomUUID(),
      },
    },
  };
}

/** Runtime shape of GoalResponse from HrPerformanceGoalV2Controller. */
export interface G3PerformanceGoal {
  id: string;
  tenantId: string;
  personId: string;
  employmentId: string;
  title: string;
  metric: string;
  targetValue: string;
  progress: number;
  status: string;
  /** ISO date (YYYY-MM-DD). */
  startsOn: string;
  /** ISO date (YYYY-MM-DD). */
  endsOn: string;
  /** ISO timestamp. */
  createdAt: string;
  /** ISO timestamp. */
  updatedAt: string;
}

/** Runtime shape of GoalWriteRequest. Progress defaults to 0 when omitted. */
export interface G3GoalWriteRequest {
  title: string;
  metric: string;
  targetValue: string;
  progress?: number;
  startsOn: string;
  endsOn: string;
}

/** Runtime shape of GoalProgressRequest. Backend validation: 0..100. */
export interface G3GoalProgressRequest {
  progress: number;
}

export const hrG3Api = {
  listGoals: () => apiClient.get<G3PerformanceGoal[]>(ROOT),

  getGoal: (goalId: string) => apiClient.get<G3PerformanceGoal>(`${ROOT}/${goalId}`),

  createGoal: (input: G3GoalWriteRequest) =>
    apiClient.post<G3PerformanceGoal>(ROOT, input, mutationOptions()),

  updateGoal: (goalId: string, input: G3GoalWriteRequest) =>
    apiClient.put<G3PerformanceGoal>(`${ROOT}/${goalId}`, input, mutationOptions()),

  updateGoalProgress: (goalId: string, input: G3GoalProgressRequest) =>
    apiClient.patch<G3PerformanceGoal>(`${ROOT}/${goalId}/progress`, input, mutationOptions()),
};
