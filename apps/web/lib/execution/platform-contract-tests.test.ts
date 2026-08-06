/**
 * Platform Contract Tests — SANAD Execution Framework
 * ---------------------------------------------------
 * Tests all module providers for compliance with the ExecutionProvider interface.
 * Ensures no module duplicates execution logic.
 */

import { describe, it, expect } from "vitest";
import type {
  ExecutionProvider,
  ExecutionProgram,
  ExecutionGroup,
  ExecutionTask,
  ExecutionProgress,
} from "./index";
import {
  calculateGroupProgress,
  calculateProgramProgress,
} from "./index";

// ── Import All Module Providers ──────────────────────────────────────────────

import { CrmExecutionProvider } from "../../app/crm/crm-execution-provider";
import { NotificationsExecutionProvider } from "../../app/notifications/notifications-execution-provider";
import { LicensingExecutionProvider } from "../../app/licensing/licensing-execution-provider";
import { WorkflowExecutionProvider } from "../../app/workflow/workflow-execution-provider";
import { HrExecutionProvider } from "../../app/hr/hr-execution-provider";
import { IdentityExecutionProvider } from "../../app/identity/identity-execution-provider";
import { ErpExecutionProvider } from "../../app/erp/erp-execution-provider";
import { FinanceExecutionProvider } from "../../app/finance/finance-execution-provider";
import { InventoryExecutionProvider } from "../../app/inventory/inventory-execution-provider";
import { PosExecutionProvider } from "../../app/pos/pos-execution-provider";
import { AnalyticsExecutionProvider } from "../../app/analytics/analytics-execution-provider";
import { SubscriptionsExecutionProvider } from "../../app/subscriptions/subscriptions-execution-provider";
import { AiPlatformExecutionProvider } from "../../app/ai-platform/ai-platform-execution-provider";

// ── Provider Registry ────────────────────────────────────────────────────────

const providers: ExecutionProvider[] = [
  new CrmExecutionProvider(),
  new NotificationsExecutionProvider(),
  new LicensingExecutionProvider(),
  new WorkflowExecutionProvider(),
  new HrExecutionProvider(),
  new IdentityExecutionProvider(),
  new ErpExecutionProvider(),
  new FinanceExecutionProvider(),
  new InventoryExecutionProvider(),
  new PosExecutionProvider(),
  new AnalyticsExecutionProvider(),
  new SubscriptionsExecutionProvider(),
  new AiPlatformExecutionProvider(),
];

// ── Contract Tests ───────────────────────────────────────────────────────────

describe("Platform ExecutionProvider Contract", () => {
  for (const provider of providers) {
    describe(`${provider.moduleId} Provider`, () => {
      it("should have a module ID", () => {
        expect(provider.moduleId).toBeDefined();
        expect(typeof provider.moduleId).toBe("string");
        expect(provider.moduleId.length).toBeGreaterThan(0);
      });

      it("should have a module name", () => {
        expect(provider.moduleName).toBeDefined();
        expect(typeof provider.moduleName).toBe("string");
        expect(provider.moduleName.length).toBeGreaterThan(0);
      });

      it("should return programs", async () => {
        const programs = await provider.getPrograms();
        expect(Array.isArray(programs)).toBe(true);
        expect(programs.length).toBeGreaterThan(0);
      });

      it("should return a program by ID", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const fetched = await provider.getProgram(program.id);
        expect(fetched).not.toBeNull();
        expect(fetched?.id).toBe(program.id);
      });

      it("should return null for non-existent program", async () => {
        const fetched = await provider.getProgram("NON-EXISTENT");
        expect(fetched).toBeNull();
      });

      it("should return groups for a program", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const groups = await provider.getGroups(program.id);
        expect(Array.isArray(groups)).toBe(true);
        expect(groups.length).toBeGreaterThan(0);
      });

      it("should return a group by code", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const groups = await provider.getGroups(program.id);
        const group = groups[0];
        const fetched = await provider.getGroup(program.id, group.code);
        expect(fetched).not.toBeNull();
        expect(fetched?.code).toBe(group.code);
      });

      it("should return null for non-existent group", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const fetched = await provider.getGroup(program.id, "NON-EXISTENT");
        expect(fetched).toBeNull();
      });

      it("should return tasks for a group", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const groups = await provider.getGroups(program.id);
        const group = groups[0];
        const tasks = await provider.getTasks(program.id, group.code);
        expect(Array.isArray(tasks)).toBe(true);
      });

      it("should return progress for a group", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const groups = await provider.getGroups(program.id);
        const group = groups[0];
        const progress = await provider.getProgress(program.id, group.code);
        expect(progress).toBeDefined();
        expect(typeof progress.total).toBe("number");
        expect(typeof progress.done).toBe("number");
        expect(typeof progress.percentage).toBe("number");
      });

      it("should return progress for a program", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const progress = await provider.getProgramProgress(program.id);
        expect(progress).toBeDefined();
        expect(typeof progress.total).toBe("number");
        expect(typeof progress.done).toBe("number");
        expect(typeof progress.percentage).toBe("number");
      });

      it("should have consistent progress calculation", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const groups = await provider.getGroups(program.id);
        const allTasks = groups.flatMap((g) => g.tasks);
        const completedTasks = allTasks.filter(
          (t) => t.status === "DONE" || t.status === "APPROVED"
        );
        const expectedPercentage =
          allTasks.length > 0
            ? Math.round((completedTasks.length / allTasks.length) * 100)
            : 0;
        const progress = await provider.getProgramProgress(program.id);
        expect(progress.percentage).toBe(expectedPercentage);
      });

      it("should return certification for a group", async () => {
        const programs = await provider.getPrograms();
        const program = programs[0];
        const groups = await provider.getGroups(program.id);
        const group = groups[0];
        const certification = await provider.getCertification(
          program.id,
          group.code
        );
        // Certification may be null if not yet submitted
        expect(certification === null || typeof certification === "object").toBe(
          true
        );
      });
    });
  }
});

// ── Type Compatibility Tests ─────────────────────────────────────────────────

describe("ExecutionFramework Type Compatibility", () => {
  it("should have valid ExecutionProgress structure", () => {
    const progress: ExecutionProgress = {
      total: 10,
      done: 5,
      approved: 3,
      inProgress: 2,
      blocked: 0,
      notStarted: 5,
      needsReview: 0,
      percentage: 50,
    };
    expect(progress.total).toBe(10);
    expect(progress.percentage).toBe(50);
  });

  it("should have valid ExecutionTask structure", () => {
    const task: ExecutionTask = {
      id: "TEST-T1",
      number: 1,
      nameAr: "مهمة اختبار",
      nameEn: "Test Task",
      groupCode: "G0",
      descriptionAr: "وصف",
      descriptionEn: "Description",
      type: "IMPLEMENTATION",
      priority: "P1",
      status: "NOT_STARTED",
      dependencies: [],
      acceptanceCriteriaAr: "المعايير",
      implementationNotesAr: null,
      evidence: [],
    };
    expect(task.id).toBe("TEST-T1");
    expect(task.status).toBe("NOT_STARTED");
  });
});

// ── Calculator Compatibility Tests ───────────────────────────────────────────

describe("ExecutionFramework Calculator Compatibility", () => {
  it("should calculate group progress correctly", () => {
    const group: ExecutionGroup = {
      id: "GROUP-G0",
      code: "G0",
      titleAr: "المجموعة",
      titleEn: "Group",
      purposeAr: "الهدف",
      purposeEn: "Purpose",
      status: "IN_PROGRESS",
      dependencies: [],
      canParallelizeWith: [],
      stageReport: null,
      milestones: [],
      tasks: [
        {
          id: "T1",
          number: 1,
          nameAr: "مهمة 1",
          nameEn: "Task 1",
          groupCode: "G0",
          descriptionAr: "",
          descriptionEn: "",
          type: "IMPLEMENTATION",
          priority: "P1",
          status: "DONE",
          dependencies: [],
          acceptanceCriteriaAr: "",
          implementationNotesAr: null,
          evidence: [],
        },
        {
          id: "T2",
          number: 2,
          nameAr: "مهمة 2",
          nameEn: "Task 2",
          groupCode: "G0",
          descriptionAr: "",
          descriptionEn: "",
          type: "IMPLEMENTATION",
          priority: "P1",
          status: "NOT_STARTED",
          dependencies: [],
          acceptanceCriteriaAr: "",
          implementationNotesAr: null,
          evidence: [],
        },
      ],
    };
    const progress = calculateGroupProgress(group);
    expect(progress.total).toBe(2);
    expect(progress.done).toBe(1);
    expect(progress.percentage).toBe(50);
  });

  it("should calculate program progress correctly", () => {
    const program: ExecutionProgram = {
      id: "PROGRAM-TEST",
      code: "TEST",
      titleAr: "اختبار",
      titleEn: "Test",
      descriptionAr: "",
      descriptionEn: "",
      status: "IN_PROGRESS",
      groups: [
        {
          id: "GROUP-G0",
          code: "G0",
          titleAr: "المجموعة",
          titleEn: "Group",
          purposeAr: "",
          purposeEn: "",
          status: "DONE",
          dependencies: [],
          canParallelizeWith: [],
          stageReport: null,
          milestones: [],
          tasks: [
            {
              id: "T1",
              number: 1,
              nameAr: "مهمة 1",
              nameEn: "Task 1",
              groupCode: "G0",
              descriptionAr: "",
              descriptionEn: "",
              type: "IMPLEMENTATION",
              priority: "P1",
              status: "DONE",
              dependencies: [],
              acceptanceCriteriaAr: "",
              implementationNotesAr: null,
              evidence: [],
            },
          ],
        },
      ],
    };
    const progress = calculateProgramProgress(program);
    expect(progress.total).toBe(1);
    expect(progress.done).toBe(1);
    expect(progress.percentage).toBe(100);
  });
});
