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
