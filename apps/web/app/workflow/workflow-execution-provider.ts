/**
 * Workflow Execution Provider
 * ---------------------------
 * Implements the ExecutionProvider interface for the Workflow module.
 * Provides execution data to the shared execution engine.
 */

import type {
  ExecutionProgram,
  ExecutionGroup,
  ExecutionMilestone,
  ExecutionTask,
  ExecutionEvidence,
  ExecutionProgress,
  Certification,
} from "../../lib/execution";
import { calculateGroupProgress, calculateProgramProgress } from "../../lib/execution";
import { WORKFLOW_GROUP_DATA, WORKFLOW_TASKS, type WorkflowTask } from "./workflow-execution-data";

/**
 * Workflow Execution Provider
 *
 * Satisfies the ExecutionProvider interface for the Workflow module.
 * All execution logic uses the shared framework calculators and validators.
 */
export class WorkflowExecutionProvider {
  readonly moduleId = "WORKFLOW";
  readonly moduleName = "Workflow Automation";

  private program: ExecutionProgram;
  private certifications: Map<string, Certification> = new Map();

  constructor() {
    this.program = this.buildProgram();
  }

  // ── Data Access ──────────────────────────────────────────────────────────

  async getPrograms(): Promise<ExecutionProgram[]> {
    return [this.program];
  }

  async getProgram(programId: string): Promise<ExecutionProgram | null> {
    if (programId === this.program.id) {
      return this.program;
    }
    return null;
  }

  async getGroups(programId: string): Promise<ExecutionGroup[]> {
    if (programId === this.program.id) {
      return this.program.groups;
    }
    return [];
  }

  async getGroup(programId: string, groupCode: string): Promise<ExecutionGroup | null> {
    if (programId === this.program.id) {
      return this.program.groups.find((g) => g.code === groupCode) ?? null;
    }
    return null;
  }

  async getMilestones(programId: string, groupCode: string): Promise<ExecutionMilestone[]> {
    const group = await this.getGroup(programId, groupCode);
    return group?.milestones ?? [];
  }

  async getTasks(programId: string, groupCode: string): Promise<ExecutionTask[]> {
    const group = await this.getGroup(programId, groupCode);
    return group?.tasks ?? [];
  }

  async getEvidence(programId: string, groupCode: string, taskId: string): Promise<ExecutionEvidence[]> {
    const tasks = await this.getTasks(programId, groupCode);
    const task = tasks.find((t) => t.id === taskId);
    return task?.evidence ?? [];
  }

  async getProgress(programId: string, groupCode: string): Promise<ExecutionProgress> {
    const group = await this.getGroup(programId, groupCode);
    if (!group) {
      return { total: 0, done: 0, approved: 0, inProgress: 0, blocked: 0, notStarted: 0, needsReview: 0, percentage: 0 };
    }
    return calculateGroupProgress(group);
  }

  async getProgramProgress(programId: string): Promise<ExecutionProgress> {
    const program = await this.getProgram(programId);
    if (!program) {
      return { total: 0, done: 0, approved: 0, inProgress: 0, blocked: 0, notStarted: 0, needsReview: 0, percentage: 0 };
    }
    return calculateProgramProgress(program);
  }

  async getCertification(programId: string, groupCode: string): Promise<Certification | null> {
    return this.certifications.get(groupCode) ?? null;
  }

  // ── Mutation ─────────────────────────────────────────────────────────────

  async updateTaskStatus(
    programId: string,
    groupCode: string,
    taskId: string,
    status: string
  ): Promise<void> {
    const group = this.program.groups.find((g) => g.code === groupCode);
    if (!group) return;

    const task = group.tasks.find((t) => t.id === taskId);
    if (!task) return;

    // Update task status
    task.status = status as ExecutionTask["status"];

    // Update group status based on task progress
    const progress = calculateGroupProgress(group);
    if (progress.percentage === 100) {
      group.status = "DONE";
    } else if (progress.percentage > 0) {
      group.status = "IN_PROGRESS";
    }
  }

  async submitForCertification(programId: string, groupCode: string): Promise<void> {
    const group = await this.getGroup(programId, groupCode);
    if (!group) return;

    const progress = calculateGroupProgress(group);
    if (progress.percentage === 100) {
      this.certifications.set(groupCode, {
        id: `CERT-${groupCode}-${Date.now()}`,
        entityId: group.id,
        entityType: "GROUP",
        status: "PENDING_REVIEW",
        acceptanceCriteria: [],
      });
    }
  }

  // ── Private Helpers ──────────────────────────────────────────────────────

  private buildProgram(): ExecutionProgram {
    const groups = WORKFLOW_GROUP_DATA.map((groupData) => this.buildGroup(groupData));
    const allTasks = groups.flatMap((g) => g.tasks);
    const completedTasks = allTasks.filter((t) => t.status === "DONE" || t.status === "APPROVED");
    const overallPercentage = allTasks.length > 0
      ? Math.round((completedTasks.length / allTasks.length) * 100)
      : 0;

    let status: ExecutionGroup["status"] = "NOT_STARTED";
    if (overallPercentage === 100) status = "DONE";
    else if (overallPercentage > 0) status = "IN_PROGRESS";

    return {
      id: "WORKFLOW-PROGRAM",
      code: "WORKFLOW",
      titleAr: "أتمتة سير العمل",
      titleEn: "Workflow Automation",
      descriptionAr: "نظام أتمتة سير العمل لإدارة العمليات والمهام والتقارير",
      descriptionEn: "Workflow automation system for managing processes, tasks, and reports",
      status,
      groups,
    };
  }

  private buildGroup(groupData: {
    code: string;
    titleAr: string;
    titleEn: string;
    purposeAr: string;
    purposeEn: string;
    status: ExecutionGroup["status"];
    dependencies: string[];
    canParallelizeWith: string[];
    stageReport: string | null;
  }): ExecutionGroup {
    const tasks = WORKFLOW_TASKS
      .filter((t) => t.groupCode === groupData.code)
      .map((task) => this.buildTask(task));

    return {
      id: `GROUP-${groupData.code}`,
      code: groupData.code,
      titleAr: groupData.titleAr,
      titleEn: groupData.titleEn,
      purposeAr: groupData.purposeAr,
      purposeEn: groupData.purposeEn,
      status: groupData.status,
      dependencies: groupData.dependencies,
      canParallelizeWith: groupData.canParallelizeWith,
      stageReport: groupData.stageReport,
      milestones: [],
      tasks,
    };
  }

  private buildTask(taskData: WorkflowTask): ExecutionTask {
    return {
      id: taskData.id,
      number: taskData.number,
      nameAr: taskData.nameAr,
      nameEn: taskData.nameEn,
      groupCode: taskData.groupCode,
      descriptionAr: taskData.descriptionAr,
      descriptionEn: taskData.descriptionEn,
      type: taskData.type,
      priority: taskData.priority,
      status: taskData.status,
      dependencies: taskData.dependencies,
      acceptanceCriteriaAr: taskData.acceptanceCriteriaAr,
      implementationNotesAr: taskData.implementationNotesAr,
      evidence: [], // Evidence will be added as tasks are completed
    };
  }
}
