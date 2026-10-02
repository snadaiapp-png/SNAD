import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
  },
}));

import { apiClient } from "./client";

/**
 * Load the Task 5 facade dynamically so RED evidence records a real,
 * per-test failure while the module does not exist yet (feature-missing RED),
 * rather than a suite-level collection error.
 */
async function loadFacade() {
  return await import("./hr-g3-api");
}

const GOAL_ROOT = "/api/v2/hr/performance/goals";

const GOAL_RESPONSE = {
  id: "0b6fbc7e-1111-4c11-9c11-0a1b2c3d4e5f",
  tenantId: "t-1",
  personId: "p-1",
  employmentId: "e-1",
  title: "Reduce onboarding time",
  metric: "Average onboarding days",
  targetValue: "10",
  progress: 40,
  status: "DRAFT",
  startsOn: "2026-10-01",
  endsOn: "2026-12-31",
  createdAt: "2026-10-01T08:00:00Z",
  updatedAt: "2026-10-01T09:30:00Z",
};

describe("hrG3Api typed goals facade (Task 5 SELF surface)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("A. listGoals routes GET /api/v2/hr/performance/goals through apiClient", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.get).mockResolvedValueOnce([GOAL_RESPONSE]);

    const result = await hrG3Api.listGoals();

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith(GOAL_ROOT);
    expect(result).toEqual([GOAL_RESPONSE]);
  });

  it("B. getGoal routes GET /api/v2/hr/performance/goals/{goalId} through apiClient", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.get).mockResolvedValueOnce(GOAL_RESPONSE);

    const result = await hrG3Api.getGoal(GOAL_RESPONSE.id);

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith(`${GOAL_ROOT}/${GOAL_RESPONSE.id}`);
    expect(result).toEqual(GOAL_RESPONSE);
  });

  it("C. createGoal routes POST /api/v2/hr/performance/goals with the typed write body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce(GOAL_RESPONSE);

    const input = {
      title: "Reduce onboarding time",
      metric: "Average onboarding days",
      targetValue: "10",
      progress: 0,
      startsOn: "2026-10-01",
      endsOn: "2026-12-31",
    };
    const result = await hrG3Api.createGoal(input);

    expect(apiClient.post).toHaveBeenCalledTimes(1);
    expect(apiClient.post).toHaveBeenCalledWith(GOAL_ROOT, input, expect.anything());
    expect(result).toEqual(GOAL_RESPONSE);
  });

  it("D. updateGoal routes PUT /api/v2/hr/performance/goals/{goalId} with the typed write body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.put).mockResolvedValueOnce(GOAL_RESPONSE);

    const input = {
      title: "Reduce onboarding time",
      metric: "Average onboarding days",
      targetValue: "8",
      progress: 55,
      startsOn: "2026-10-01",
      endsOn: "2026-12-31",
    };
    const result = await hrG3Api.updateGoal(GOAL_RESPONSE.id, input);

    expect(apiClient.put).toHaveBeenCalledTimes(1);
    expect(apiClient.put).toHaveBeenCalledWith(`${GOAL_ROOT}/${GOAL_RESPONSE.id}`, input, expect.anything());
    expect(result).toEqual(GOAL_RESPONSE);
  });

  it("E. updateGoalProgress routes PATCH /api/v2/hr/performance/goals/{goalId}/progress with the typed body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.patch).mockResolvedValueOnce({ ...GOAL_RESPONSE, progress: 70 });

    const result = await hrG3Api.updateGoalProgress(GOAL_RESPONSE.id, { progress: 70 });

    expect(apiClient.patch).toHaveBeenCalledTimes(1);
    expect(apiClient.patch).toHaveBeenCalledWith(
      `${GOAL_ROOT}/${GOAL_RESPONSE.id}/progress`,
      { progress: 70 },
      expect.anything(),
    );
    expect(result).toEqual({ ...GOAL_RESPONSE, progress: 70 });
  });

  it("F. mutations use the governed apiClient transport with an Idempotency-Key and no ad-hoc fetch", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce(GOAL_RESPONSE);
    vi.mocked(apiClient.put).mockResolvedValueOnce(GOAL_RESPONSE);
    vi.mocked(apiClient.patch).mockResolvedValueOnce(GOAL_RESPONSE);

    const writeInput = {
      title: "t",
      metric: "m",
      targetValue: "1",
      progress: 5,
      startsOn: "2026-10-01",
      endsOn: "2026-12-31",
    };
    await hrG3Api.createGoal(writeInput);
    await hrG3Api.updateGoal(GOAL_RESPONSE.id, writeInput);
    await hrG3Api.updateGoalProgress(GOAL_RESPONSE.id, { progress: 5 });

    const mutationOptions = expect.objectContaining({
      context: {
        headers: {
          "Idempotency-Key": expect.any(String),
        },
      },
    });
    expect(apiClient.post).toHaveBeenCalledWith(GOAL_ROOT, writeInput, mutationOptions);
    expect(apiClient.put).toHaveBeenCalledWith(`${GOAL_ROOT}/${GOAL_RESPONSE.id}`, writeInput, mutationOptions);
    expect(apiClient.patch).toHaveBeenCalledWith(
      `${GOAL_ROOT}/${GOAL_RESPONSE.id}/progress`,
      { progress: 5 },
      mutationOptions,
    );

    // The facade must not open its own transport: apiClient is the only fetch owner.
    const facadePath = resolve(__dirname, "hr-g3-api.ts");
    expect(existsSync(facadePath), "Task 5 facade hr-g3-api.ts must exist").toBe(true);
    const facadeSource = readFileSync(facadePath, "utf8");
    expect(facadeSource).not.toMatch(/\bfetch\s*\(/);
  });

  it("G. propagates real backend errors instead of converting them to fake success", async () => {
    const { hrG3Api } = await loadFacade();
    const forbidden = new Error("HTTP 403 Forbidden: GET /api/v2/hr/performance/goals");
    vi.mocked(apiClient.get).mockRejectedValueOnce(forbidden);
    const conflict = new Error("HTTP 409 Conflict: PATCH /api/v2/hr/performance/goals/x/progress");
    vi.mocked(apiClient.patch).mockRejectedValueOnce(conflict);

    await expect(hrG3Api.listGoals()).rejects.toBe(forbidden);
    await expect(hrG3Api.updateGoalProgress(GOAL_RESPONSE.id, { progress: 70 })).rejects.toBe(conflict);
  });

  it("H. SELF operations never request caller-supplied tenant/employment authority", async () => {
    const { hrG3Api } = await loadFacade();

    // listGoals is SELF-scoped: the backend derives identity from the session.
    expect(hrG3Api.listGoals.length).toBe(0);

    vi.mocked(apiClient.get).mockResolvedValue([GOAL_RESPONSE]);
    await hrG3Api.listGoals();
    expect(apiClient.get).toHaveBeenCalledWith(GOAL_ROOT);
    const listCall = vi.mocked(apiClient.get).mock.calls[0];
    const serializedListCall = JSON.stringify(listCall);
    expect(serializedListCall).not.toContain("employmentId");
    expect(serializedListCall).not.toContain("tenantId");

    // getGoal takes only the goal id; no employment/tenant authority parameter.
    expect(hrG3Api.getGoal.length).toBe(1);
    await hrG3Api.getGoal(GOAL_RESPONSE.id);
    expect(apiClient.get).toHaveBeenLastCalledWith(`${GOAL_ROOT}/${GOAL_RESPONSE.id}`);
    const getCall = vi.mocked(apiClient.get).mock.calls[1];
    expect(JSON.stringify(getCall)).not.toContain("employmentId");

    // updateGoalProgress takes only the goal id and the progress payload.
    expect(hrG3Api.updateGoalProgress.length).toBe(2);
    vi.mocked(apiClient.patch).mockResolvedValueOnce(GOAL_RESPONSE);
    await hrG3Api.updateGoalProgress(GOAL_RESPONSE.id, { progress: 60 });
    const patchCall = vi.mocked(apiClient.patch).mock.calls[0];
    expect(JSON.stringify(patchCall[1])).not.toContain("employmentId");
    expect(JSON.stringify(patchCall[1])).not.toContain("tenantId");
  });
});

const REVIEW_ROOT = "/api/v2/hr/performance/reviews";

/** Runtime shape of PerformanceReview from HrPerformanceReviewV2Controller. */
const REVIEW_RESPONSE = {
  id: "5c2a1b7e-2222-4d22-8d22-1b2c3d4e5f6a",
  tenantId: "t-1",
  subjectPersonId: "p-1",
  subjectEmploymentId: "e-1",
  reviewerPersonId: "p-1",
  reviewerEmploymentId: "e-1",
  source: "SELF",
  status: "DRAFT",
  cycle: "2026-H1",
  periodStart: "2026-01-01",
  periodEnd: "2026-06-30",
  rating: null,
  comments: "خطة الربع الأول",
  version: 0,
  createdAt: "2026-10-01T08:00:00Z",
  createdBy: "u-1",
  updatedAt: "2026-10-01T09:30:00Z",
  updatedBy: "u-1",
};

const TEAM_REVIEW_RESPONSE = {
  ...REVIEW_RESPONSE,
  id: "7d3b2c8e-3333-4e33-9e33-2c3d4e5f6a7b",
  source: "MANAGER",
  status: "SUBMITTED",
  rating: 4,
  reviewerPersonId: "p-9",
  reviewerEmploymentId: "e-9",
};

const REVIEW_WRITE_INPUT = {
  cycle: "2026-H1",
  periodStart: "2026-01-01",
  periodEnd: "2026-06-30",
  rating: 4,
  comments: "أداء قوي ومستقر",
};

describe("hrG3Api typed reviews facade (Task 6 SELF + TEAM surfaces)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("R1. listReviews routes GET /api/v2/hr/performance/reviews through apiClient", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.get).mockResolvedValueOnce([REVIEW_RESPONSE]);

    const result = await hrG3Api.listReviews();

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith(REVIEW_ROOT);
    expect(result).toEqual([REVIEW_RESPONSE]);
  });

  it("R2. getReview routes GET /api/v2/hr/performance/reviews/{reviewId} through apiClient", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.get).mockResolvedValueOnce(REVIEW_RESPONSE);

    const result = await hrG3Api.getReview(REVIEW_RESPONSE.id);

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith(`${REVIEW_ROOT}/${REVIEW_RESPONSE.id}`);
    expect(result).toEqual(REVIEW_RESPONSE);
  });

  it("R3. createSelfReview routes POST /api/v2/hr/performance/reviews with the typed write body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce(REVIEW_RESPONSE);

    const result = await hrG3Api.createSelfReview(REVIEW_WRITE_INPUT);

    expect(apiClient.post).toHaveBeenCalledTimes(1);
    expect(apiClient.post).toHaveBeenCalledWith(REVIEW_ROOT, REVIEW_WRITE_INPUT, expect.anything());
    expect(result).toEqual(REVIEW_RESPONSE);
  });

  it("R4. submitReview routes POST /api/v2/hr/performance/reviews/{reviewId}/submit without a body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce({ ...REVIEW_RESPONSE, status: "SUBMITTED" });

    const result = await hrG3Api.submitReview(REVIEW_RESPONSE.id);

    expect(apiClient.post).toHaveBeenCalledTimes(1);
    expect(apiClient.post).toHaveBeenCalledWith(
      `${REVIEW_ROOT}/${REVIEW_RESPONSE.id}/submit`,
      undefined,
      expect.anything(),
    );
    expect(result).toEqual({ ...REVIEW_RESPONSE, status: "SUBMITTED" });
  });

  it("R5. acknowledgeReview routes POST /api/v2/hr/performance/reviews/{reviewId}/acknowledge without a body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce({ ...REVIEW_RESPONSE, status: "ACKNOWLEDGED" });

    const result = await hrG3Api.acknowledgeReview(REVIEW_RESPONSE.id);

    expect(apiClient.post).toHaveBeenCalledWith(
      `${REVIEW_ROOT}/${REVIEW_RESPONSE.id}/acknowledge`,
      undefined,
      expect.anything(),
    );
    expect(result).toEqual({ ...REVIEW_RESPONSE, status: "ACKNOWLEDGED" });
  });

  it("R6. listTeamReviews routes GET /api/v2/hr/performance/reviews/team through apiClient", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.get).mockResolvedValueOnce([TEAM_REVIEW_RESPONSE]);

    const result = await hrG3Api.listTeamReviews();

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith(`${REVIEW_ROOT}/team`);
    expect(result).toEqual([TEAM_REVIEW_RESPONSE]);
  });

  it("R7. getTeamReview routes GET /api/v2/hr/performance/reviews/team/{reviewId} through apiClient", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.get).mockResolvedValueOnce(TEAM_REVIEW_RESPONSE);

    const result = await hrG3Api.getTeamReview(TEAM_REVIEW_RESPONSE.id);

    expect(apiClient.get).toHaveBeenCalledWith(`${REVIEW_ROOT}/team/${TEAM_REVIEW_RESPONSE.id}`);
    expect(result).toEqual(TEAM_REVIEW_RESPONSE);
  });

  it("R8. createTeamReview routes POST /api/v2/hr/performance/reviews/team/{employmentId} — employmentId stays in the path, never as body authority", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce(TEAM_REVIEW_RESPONSE);

    const result = await hrG3Api.createTeamReview("e-7", REVIEW_WRITE_INPUT);

    expect(apiClient.post).toHaveBeenCalledTimes(1);
    expect(apiClient.post).toHaveBeenCalledWith(`${REVIEW_ROOT}/team/e-7`, REVIEW_WRITE_INPUT, expect.anything());
    expect(result).toEqual(TEAM_REVIEW_RESPONSE);
    const body = vi.mocked(apiClient.post).mock.calls[0][1] as Record<string, unknown>;
    expect(JSON.stringify(Object.keys(body))).not.toContain("employmentId");
    expect(JSON.stringify(Object.keys(body))).not.toContain("tenantId");
  });

  it("R9. cancelTeamReview routes POST /api/v2/hr/performance/reviews/team/reviews/{reviewId}/cancel without a body", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValueOnce({ ...TEAM_REVIEW_RESPONSE, status: "CANCELLED" });

    const result = await hrG3Api.cancelTeamReview(TEAM_REVIEW_RESPONSE.id);

    expect(apiClient.post).toHaveBeenCalledWith(
      `${REVIEW_ROOT}/team/reviews/${TEAM_REVIEW_RESPONSE.id}/cancel`,
      undefined,
      expect.anything(),
    );
    expect(result).toEqual({ ...TEAM_REVIEW_RESPONSE, status: "CANCELLED" });
  });

  it("R10. mutations use the governed apiClient transport with an Idempotency-Key", async () => {
    const { hrG3Api } = await loadFacade();
    vi.mocked(apiClient.post).mockResolvedValue(REVIEW_RESPONSE);

    await hrG3Api.createSelfReview(REVIEW_WRITE_INPUT);
    await hrG3Api.submitReview(REVIEW_RESPONSE.id);
    await hrG3Api.acknowledgeReview(REVIEW_RESPONSE.id);

    for (const call of vi.mocked(apiClient.post).mock.calls) {
      const options = call[2] as { context: { headers: Record<string, string> } };
      expect(options?.context?.headers?.["Idempotency-Key"], `mutation ${call[0]} must carry Idempotency-Key`).toEqual(expect.any(String));
    }
  });

  it("R11. propagates real backend errors instead of swallowing them", async () => {
    const { hrG3Api } = await loadFacade();
    const forbidden = new Error("HTTP 403 Forbidden: GET /api/v2/hr/performance/reviews");
    vi.mocked(apiClient.get).mockRejectedValueOnce(forbidden);
    const conflict = new Error("HTTP 409 Conflict: POST /api/v2/hr/performance/reviews/x/submit");
    vi.mocked(apiClient.post).mockRejectedValueOnce(conflict);

    await expect(hrG3Api.listReviews()).rejects.toBe(forbidden);
    await expect(hrG3Api.submitReview(REVIEW_RESPONSE.id)).rejects.toBe(conflict);
  });

  it("R12. exposes no updateReview or createPeerReview — the backend defines no such endpoints", async () => {
    const { hrG3Api } = await loadFacade();
    expect((hrG3Api as Record<string, unknown>).updateReview).toBeUndefined();
    expect((hrG3Api as Record<string, unknown>).createPeerReview).toBeUndefined();
  });

  it("R13. review facade opens no ad-hoc fetch (apiClient is the only transport owner)", async () => {
    await loadFacade();
    const facadePath = resolve(__dirname, "hr-g3-api.ts");
    const facadeSource = readFileSync(facadePath, "utf8");
    expect(facadeSource).not.toMatch(/\bfetch\s*\(/);
  });
});
