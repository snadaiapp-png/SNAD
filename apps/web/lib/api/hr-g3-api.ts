import { apiClient } from "./client";

/**
 * HRM G3 typed facade for the performance domain (Task 5 goals + Task 6
 * reviews).
 *
 * Routes mirror the canonical backend contract exactly:
 *
 * Goals (Task 5):
 *   GET    /api/v2/hr/performance/goals                     (GOAL.SELF_VIEW)
 *   GET    /api/v2/hr/performance/goals/{goalId}            (GOAL.SELF_VIEW)
 *   POST   /api/v2/hr/performance/goals                     (GOAL.SELF_UPDATE)
 *   PUT    /api/v2/hr/performance/goals/{goalId}            (GOAL.SELF_UPDATE)
 *   PATCH  /api/v2/hr/performance/goals/{goalId}/progress   (GOAL.SELF_UPDATE)
 *
 * Reviews (Task 6) — SELF and TEAM are capability-separated:
 *   GET    /api/v2/hr/performance/reviews                        (REVIEW.SELF_VIEW)
 *   GET    /api/v2/hr/performance/reviews/{reviewId}             (REVIEW.SELF_VIEW)
 *   POST   /api/v2/hr/performance/reviews                        (REVIEW.SELF_SUBMIT)
 *   POST   /api/v2/hr/performance/reviews/{reviewId}/submit      (REVIEW.SELF_SUBMIT)
 *   POST   /api/v2/hr/performance/reviews/{reviewId}/acknowledge (REVIEW.SELF_SUBMIT)
 *   GET    /api/v2/hr/performance/reviews/team                   (REVIEW.TEAM_MANAGE)
 *   GET    /api/v2/hr/performance/reviews/team/{reviewId}        (REVIEW.TEAM_MANAGE)
 *   POST   /api/v2/hr/performance/reviews/team/{employmentId}    (REVIEW.TEAM_MANAGE)
 *   POST   /api/v2/hr/performance/reviews/team/reviews/{reviewId}/cancel (REVIEW.TEAM_MANAGE)
 *
 * The backend defines NO updateReview and NO createPeerReview HTTP endpoint —
 * the facade deliberately exposes neither.
 *
 * SELF identity (tenant/user) is derived by the backend from the authenticated
 * principal — the facade never sends caller-supplied authority ids. The TEAM
 * create route carries employmentId in the PATH only; the body holds review
 * fields exclusively. Transport is owned exclusively by apiClient; mutations
 * carry the same Idempotency-Key convention as the HR G2 facade.
 */

const ROOT = "/api/v2/hr/performance/goals";
const REVIEW_ROOT = "/api/v2/hr/performance/reviews";

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

/** Runtime shape of PerformanceReview from HrPerformanceReviewV2Controller. */
export interface G3PerformanceReview {
  id: string;
  tenantId: string;
  subjectPersonId: string;
  subjectEmploymentId: string;
  reviewerPersonId: string;
  reviewerEmploymentId: string;
  /** SELF | MANAGER | PEER */
  source: string;
  /** DRAFT | SUBMITTED | ACKNOWLEDGED | CANCELLED */
  status: string;
  cycle: string;
  /** ISO date (YYYY-MM-DD). */
  periodStart: string;
  /** ISO date (YYYY-MM-DD). */
  periodEnd: string;
  /** 1..5 when SUBMITTED/ACKNOWLEDGED; null while DRAFT/CANCELLED. */
  rating: number | null;
  comments: string | null;
  version: number;
  /** ISO timestamp. */
  createdAt: string;
  createdBy: string;
  /** ISO timestamp. */
  updatedAt: string;
  updatedBy: string;
}

/** Runtime shape of ReviewWriteRequest. Backend validation: cycle <=80 chars, rating 1..5. */
export interface G3ReviewWriteRequest {
  cycle: string;
  periodStart: string;
  periodEnd: string;
  rating: number;
  comments?: string;
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

  // ---- Task 6: performance reviews (SELF) --------------------------------

  listReviews: () => apiClient.get<G3PerformanceReview[]>(REVIEW_ROOT),

  getReview: (reviewId: string) =>
    apiClient.get<G3PerformanceReview>(`${REVIEW_ROOT}/${reviewId}`),

  createSelfReview: (input: G3ReviewWriteRequest) =>
    apiClient.post<G3PerformanceReview>(REVIEW_ROOT, input, mutationOptions()),

  submitReview: (reviewId: string) =>
    apiClient.post<G3PerformanceReview>(`${REVIEW_ROOT}/${reviewId}/submit`, undefined, mutationOptions()),

  acknowledgeReview: (reviewId: string) =>
    apiClient.post<G3PerformanceReview>(`${REVIEW_ROOT}/${reviewId}/acknowledge`, undefined, mutationOptions()),

  // ---- Task 6: performance reviews (TEAM — capability-separated) ---------

  listTeamReviews: () => apiClient.get<G3PerformanceReview[]>(`${REVIEW_ROOT}/team`),

  getTeamReview: (reviewId: string) =>
    apiClient.get<G3PerformanceReview>(`${REVIEW_ROOT}/team/${reviewId}`),

  createTeamReview: (employmentId: string, input: G3ReviewWriteRequest) =>
    apiClient.post<G3PerformanceReview>(`${REVIEW_ROOT}/team/${employmentId}`, input, mutationOptions()),

  cancelTeamReview: (reviewId: string) =>
    apiClient.post<G3PerformanceReview>(`${REVIEW_ROOT}/team/reviews/${reviewId}/cancel`, undefined, mutationOptions()),
};
