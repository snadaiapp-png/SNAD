/**
 * Execution Framework Contract Tests
 * -----------------------------------
 * Platform-wide contract tests that verify every provider
 * satisfies the ExecutionProvider interface.
 *
 * Fail CI if any provider violates the contract.
 */

import { describe, it, expect, beforeAll } from "vitest";
import type {
  ExecutionProvider,
  ExecutionProgram,
  ExecutionGroup,
  ExecutionTask,
  ExecutionProgress,
  Certification,
} from "./types";
import {
  calculateGroupProgress,
  calculateProgramProgress,
  validateExecutionGroup,
  validateExecutionProgram,
  isGroupValid,
  isProgramValid,
  validateProgressIntegrity,
  validateDependencyIntegrity,
} from "./index";

// ── CRM Provider Contract Tests ─────────────────────────────────────────

describe("CRM ExecutionProvider Contract", () => {
  let provider: ExecutionProvider;

  beforeAll(async () => {
    // Dynamic import to avoid issues with test environment
    const { CrmExecutionProvider } = await import("../../app/crm/crm-execution-provider");
    provider = new CrmExecutionProvider();
  });

  describe("Provider Identity", () => {
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
  });

  describe("Program Access", () => {
    it("should return programs", async () => {
      const programs = await provider.getPrograms();
      expect(Array.isArray(programs)).toBe(true);
      expect(programs.length).toBeGreaterThan(0);
    });

    it("should return a program by ID", async () => {
      const programs = await provider.getPrograms();
      const program = programs[0];
      const fetched = await provider.getProgram(program.id);
      expect(fetched).toBeDefined();
      expect(fetched?.id).toBe(program.id);
    });

    it("should return null for non-existent program", async () => {
      const program = await provider.getProgram("NON-EXISTENT");
      expect(program).toBeNull();
    });
  });

  describe("Group Access", () => {
    it("should return groups for a program", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);
      expect(Array.isArray(groups)).toBe(true);
      expect(groups.length).toBeGreaterThan(0);
    });

    it("should return a group by code", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);
      const group = groups[0];
      const fetched = await provider.getGroup(programs[0].id, group.code);
      expect(fetched).toBeDefined();
      expect(fetched?.code).toBe(group.code);
    });

    it("should return null for non-existent group", async () => {
      const programs = await provider.getPrograms();
      const group = await provider.getGroup(programs[0].id, "NON-EXISTENT");
      expect(group).toBeNull();
    });
  });

  describe("Task Access", () => {
    it("should return tasks for a group", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);
      const tasks = await provider.getTasks(programs[0].id, groups[0].code);
      expect(Array.isArray(tasks)).toBe(true);
    });

    it("should return evidence for a task", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);
      const tasks = await provider.getTasks(programs[0].id, groups[0].code);
      if (tasks.length > 0) {
        const evidence = await provider.getEvidence(programs[0].id, groups[0].code, tasks[0].id);
        expect(Array.isArray(evidence)).toBe(true);
      }
    });
  });

  describe("Progress Calculation", () => {
    it("should return progress for a group", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);
      const progress = await provider.getProgress(programs[0].id, groups[0].code);
      expect(progress).toBeDefined();
      expect(progress.total).toBeGreaterThanOrEqual(0);
      expect(progress.done).toBeGreaterThanOrEqual(0);
      expect(progress.percentage).toBeGreaterThanOrEqual(0);
      expect(progress.percentage).toBeLessThanOrEqual(100);
    });

    it("should return progress for a program", async () => {
      const programs = await provider.getPrograms();
      const progress = await provider.getProgramProgress(programs[0].id);
      expect(progress).toBeDefined();
      expect(progress.total).toBeGreaterThanOrEqual(0);
      expect(progress.percentage).toBeGreaterThanOrEqual(0);
      expect(progress.percentage).toBeLessThanOrEqual(100);
    });

    it("should have consistent progress calculation", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);

      for (const group of groups) {
        const providerProgress = await provider.getProgress(programs[0].id, group.code);
        const calculatedProgress = calculateGroupProgress(group);

        expect(providerProgress.percentage).toBe(calculatedProgress.percentage);
        expect(providerProgress.total).toBe(calculatedProgress.total);
        expect(providerProgress.done).toBe(calculatedProgress.done);
      }
    });
  });

  describe("Certification Access", () => {
    it("should return certification for a group", async () => {
      const programs = await provider.getPrograms();
      const groups = await provider.getGroups(programs[0].id);
      const certification = await provider.getCertification(programs[0].id, groups[0].code);
      // Certification can be null if not yet certified
      if (certification) {
        expect(certification).toBeDefined();
        expect(certification.entityType).toBeDefined();
      }
    });
  });
});

// ── Type Compatibility Tests ─────────────────────────────────────────────

describe("ExecutionFramework Type Compatibility", () => {
  it("should have valid ExecutionProgress structure", () => {
    const progress: ExecutionProgress = {
      total: 10,
      done: 5,
      approved: 2,
      inProgress: 2,
      blocked: 1,
      notStarted: 0,
      needsReview: 0,
      percentage: 70,
    };

    expect(progress.total).toBe(10);
    expect(progress.done).toBe(5);
    expect(progress.approved).toBe(2);
    expect(progress.percentage).toBe(70);
  });

  it("should have valid ExecutionTask structure", () => {
    const task: ExecutionTask = {
      id: "TEST-01",
      number: "G0-01",
      nameAr: "مهمة اختبار",
      nameEn: "Test Task",
      groupCode: "G0",
      descriptionAr: "وصف المهمة",
      descriptionEn: "Task description",
      type: "Frontend",
      priority: "High",
      status: "DONE",
      dependencies: [],
      acceptanceCriteriaAr: "معايير القبول",
      implementationNotesAr: "ملاحظات التنفيذ",
      evidence: [],
    };

    expect(task.id).toBe("TEST-01");
    expect(task.type).toBe("Frontend");
    expect(task.status).toBe("DONE");
  });
});

// ── Calculator Compatibility Tests ───────────────────────────────────────

describe("ExecutionFramework Calculator Compatibility", () => {
  it("should calculate group progress correctly", () => {
    const group: ExecutionGroup = {
      id: "GROUP-G0",
      code: "G0",
      titleAr: "مجموعة اختبار",
      titleEn: "Test Group",
      purposeAr: "غرض الاختبار",
      purposeEn: "Test purpose",
      status: "IN_PROGRESS",
      dependencies: [],
      canParallelizeWith: [],
      stageReport: null,
      milestones: [],
      tasks: [
        {
          id: "T01",
          number: "G0-01",
          nameAr: "مهمة 1",
          nameEn: "Task 1",
          groupCode: "G0",
          descriptionAr: "",
          descriptionEn: "",
          type: "Frontend",
          priority: "High",
          status: "DONE",
          dependencies: [],
          acceptanceCriteriaAr: "",
          implementationNotesAr: "",
          evidence: [],
        },
        {
          id: "T02",
          number: "G0-02",
          nameAr: "مهمة 2",
          nameEn: "Task 2",
          groupCode: "G0",
          descriptionAr: "",
          descriptionEn: "",
          type: "Backend",
          priority: "High",
          status: "NOT_STARTED",
          dependencies: [],
          acceptanceCriteriaAr: "",
          implementationNotesAr: "",
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
      id: "TEST-PROGRAM",
      code: "TEST",
      titleAr: "برنامج اختبار",
      titleEn: "Test Program",
      descriptionAr: "",
      descriptionEn: "",
      status: "IN_PROGRESS",
      groups: [
        {
          id: "GROUP-G0",
          code: "G0",
          titleAr: "مجموعة 1",
          titleEn: "Group 1",
          purposeAr: "",
          purposeEn: "",
          status: "DONE",
          dependencies: [],
          canParallelizeWith: [],
          stageReport: null,
          milestones: [],
          tasks: [
            {
              id: "T01",
              number: "G0-01",
              nameAr: "",
              nameEn: "",
              groupCode: "G0",
              descriptionAr: "",
              descriptionEn: "",
              type: "Frontend",
              priority: "High",
              status: "DONE",
              dependencies: [],
              acceptanceCriteriaAr: "",
              implementationNotesAr: "",
              evidence: [],
            },
          ],
        },
        {
          id: "GROUP-G1",
          code: "G1",
          titleAr: "مجموعة 2",
          titleEn: "Group 2",
          purposeAr: "",
          purposeEn: "",
          status: "IN_PROGRESS",
          dependencies: ["G0"],
          canParallelizeWith: [],
          stageReport: null,
          milestones: [],
          tasks: [
            {
              id: "T02",
              number: "G1-01",
              nameAr: "",
              nameEn: "",
              groupCode: "G1",
              descriptionAr: "",
              descriptionEn: "",
              type: "Backend",
              priority: "High",
              status: "DONE",
              dependencies: [],
              acceptanceCriteriaAr: "",
              implementationNotesAr: "",
              evidence: [],
            },
            {
              id: "T03",
              number: "G1-02",
              nameAr: "",
              nameEn: "",
              groupCode: "G1",
              descriptionAr: "",
              descriptionEn: "",
              type: "Backend",
              priority: "High",
              status: "NOT_STARTED",
              dependencies: [],
              acceptanceCriteriaAr: "",
              implementationNotesAr: "",
              evidence: [],
            },
          ],
        },
      ],
    };

    const progress = calculateProgramProgress(program);
    expect(progress.total).toBe(3);
    expect(progress.done).toBe(2);
    expect(progress.percentage).toBe(67);
  });
});

// ── Validator Compatibility Tests ────────────────────────────────────────

describe("ExecutionFramework Validator Compatibility", () => {
  it("should validate progress integrity", () => {
    const group: ExecutionGroup = {
      id: "GROUP-G0",
      code: "G0",
      titleAr: "مجموعة اختبار",
      titleEn: "Test Group",
      purposeAr: "",
      purposeEn: "",
      status: "IN_PROGRESS",
      dependencies: [],
      canParallelizeWith: [],
      stageReport: null,
      milestones: [],
      tasks: [
        {
          id: "T01",
          number: "G0-01",
          nameAr: "",
          nameEn: "",
          groupCode: "G0",
          descriptionAr: "",
          descriptionEn: "",
          type: "Frontend",
          priority: "High",
          status: "DONE",
          dependencies: [],
          acceptanceCriteriaAr: "",
          implementationNotesAr: "",
          evidence: [],
        },
      ],
    };

    const results = validateProgressIntegrity(group);
    expect(Array.isArray(results)).toBe(true);
    expect(results.length).toBeGreaterThan(0);
  });

  it("should validate dependency integrity", () => {
    const groups: ExecutionGroup[] = [
      {
        id: "GROUP-G0",
        code: "G0",
        titleAr: "",
        titleEn: "",
        purposeAr: "",
        purposeEn: "",
        status: "DONE",
        dependencies: [],
        canParallelizeWith: [],
        stageReport: null,
        milestones: [],
        tasks: [],
      },
      {
        id: "GROUP-G1",
        code: "G1",
        titleAr: "",
        titleEn: "",
        purposeAr: "",
        purposeEn: "",
        status: "IN_PROGRESS",
        dependencies: ["G0"],
        canParallelizeWith: [],
        stageReport: null,
        milestones: [],
        tasks: [],
      },
    ];

    const results = validateDependencyIntegrity(groups);
    expect(Array.isArray(results)).toBe(true);
  });
});
