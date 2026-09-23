import { beforeEach, describe, expect, it, vi } from "vitest";

const requestMock = vi.hoisted(() => vi.fn());

vi.mock("./client", () => ({
  apiClient: {
    request: requestMock,
  },
}));

import { hrmOnboardingApi, hrmRecruitmentApi } from "./hr-v2-recruitment-api";

describe("HRM G1 web/backend contract", () => {
  beforeEach(() => {
    requestMock.mockReset();
  });

  it("unwraps the canonical recruitment CursorPage and maps canonical opening fields", async () => {
    requestMock.mockResolvedValueOnce({
      items: [
        {
          id: "00000000-0000-0000-0000-000000000101",
          openingNumber: "OP-000101",
          jobId: "00000000-0000-0000-0000-000000000201",
          jobVersionId: null,
          orgUnitId: "00000000-0000-0000-0000-000000000301",
          positionId: null,
          state: "OPEN",
          requestedHeadcount: 2,
          filledHeadcount: 1,
          complianceDecision: "APPROVED",
          complianceDecidedAt: "2026-09-22T12:00:00Z",
          opensAt: "2026-09-22T12:00:00Z",
          closesAt: null,
          version: 4,
        },
      ],
      nextCursor: null,
    });

    const openings = await hrmRecruitmentApi.listOpenings();

    expect(Array.isArray(openings)).toBe(true);
    expect(openings).toHaveLength(1);
    expect(openings[0]).toMatchObject({
      openingId: "00000000-0000-0000-0000-000000000101",
      title: "OP-000101",
      departmentName: "00000000-0000-0000-0000-000000000301",
      headcount: 2,
      filledCount: 1,
      status: "OPEN",
    });
  });

  it("unwraps onboarding CursorPage and enriches task counts from canonical plan detail", async () => {
    requestMock
      .mockResolvedValueOnce({
        items: [
          {
            id: "00000000-0000-0000-0000-000000000401",
            planNumber: "ONB-000401",
            employmentId: "00000000-0000-0000-0000-000000000501",
            state: "IN_PROGRESS",
            workflowLinked: true,
            version: 2,
            createdAt: "2026-09-22T12:00:00Z",
            updatedAt: "2026-09-22T12:30:00Z",
          },
        ],
        nextCursor: null,
      })
      .mockResolvedValueOnce({
        plan: {
          id: "00000000-0000-0000-0000-000000000401",
          planNumber: "ONB-000401",
          employmentId: "00000000-0000-0000-0000-000000000501",
          state: "IN_PROGRESS",
          workflowLinked: true,
          version: 2,
          createdAt: "2026-09-22T12:00:00Z",
          updatedAt: "2026-09-22T12:30:00Z",
        },
        tasks: [
          {
            id: "00000000-0000-0000-0000-000000000601",
            sequence: 1,
            title: "Create account",
            state: "DONE",
            assigneeUserId: null,
            dueAt: null,
            reasonCode: null,
            resolvedAt: "2026-09-22T13:00:00Z",
          },
          {
            id: "00000000-0000-0000-0000-000000000602",
            sequence: 2,
            title: "Issue badge",
            state: "PENDING",
            assigneeUserId: "00000000-0000-0000-0000-000000000701",
            dueAt: null,
            reasonCode: null,
            resolvedAt: null,
          },
        ],
      });

    const plans = await hrmOnboardingApi.listPlans();

    expect(Array.isArray(plans)).toBe(true);
    expect(plans[0]).toMatchObject({
      planId: "00000000-0000-0000-0000-000000000401",
      candidateDisplayName: "ONB-000401",
      employmentId: "00000000-0000-0000-0000-000000000501",
      state: "IN_PROGRESS",
      tasksTotal: 2,
      tasksCompleted: 1,
      tasksOverdue: 0,
      assigneeId: "00000000-0000-0000-0000-000000000701",
    });
  });

  it("translates legacy web command payloads to the canonical backend request contract", async () => {
    requestMock.mockResolvedValue({ id: "ok", state: "OK" });

    await hrmRecruitmentApi.rejectOpening("opening-1", "key-1", "REJECTED_BY_REVIEWER");
    expect(requestMock).toHaveBeenLastCalledWith(expect.objectContaining({
      body: { reasonCode: "REJECTED_BY_REVIEWER" },
    }));

    await hrmRecruitmentApi.submitApplication({ openingId: "opening-1", candidateId: "candidate-1" }, "key-2");
    expect(requestMock).toHaveBeenLastCalledWith(expect.objectContaining({
      body: { jobOpeningId: "opening-1", candidateId: "candidate-1" },
    }));

    await hrmRecruitmentApi.scheduleInterview(
      "application-1",
      {
        mode: "VIDEO",
        plannedAt: "2026-09-24T09:00:00Z",
        participantIds: ["user-1", "user-2"],
        notes: "web-only note",
      },
      "key-3",
    );
    expect(requestMock).toHaveBeenLastCalledWith(expect.objectContaining({
      body: {
        mode: "VIDEO",
        plannedAt: "2026-09-24T09:00:00Z",
        panelUserIds: ["user-1", "user-2"],
      },
    }));

    await hrmOnboardingApi.cancelPlan("plan-1", "key-4", "CANCELLED_BY_HR");
    expect(requestMock).toHaveBeenLastCalledWith(expect.objectContaining({
      body: { reasonCode: "CANCELLED_BY_HR" },
    }));

    await hrmOnboardingApi.waiveTask("plan-1", "task-1", { reason: "NOT_APPLICABLE" }, "key-5");
    expect(requestMock).toHaveBeenLastCalledWith(expect.objectContaining({
      body: { reasonCode: "NOT_APPLICABLE" },
    }));
  });
});
