/**
 * Execution Provider Interface
 * ----------------------------
 * Every SANAD module SHALL implement this interface.
 * The execution engine never contains business-specific data.
 */

import type {
  ExecutionProgram,
  ExecutionGroup,
  ExecutionMilestone,
  ExecutionTask,
  ExecutionEvidence,
  ExecutionProgress,
  Certification,
} from "../types";

/**
 * ExecutionProvider — The contract for module-specific execution data.
 *
 * Each module (CRM, ERP, POS, etc.) implements this interface
 * to provide its own execution data to the shared execution engine.
 */
export interface ExecutionProvider {
  /** Unique module identifier */
  readonly moduleId: string;

  /** Module display name */
  readonly moduleName: string;

  // ── Data Access ──────────────────────────────────────────────────────────

  /** Get all programs for this module */
  getPrograms(): Promise<ExecutionProgram[]>;

  /** Get a specific program by ID */
  getProgram(programId: string): Promise<ExecutionProgram | null>;

  /** Get all groups for a program */
  getGroups(programId: string): Promise<ExecutionGroup[]>;

  /** Get a specific group by code */
  getGroup(programId: string, groupCode: string): Promise<ExecutionGroup | null>;

  /** Get all milestones for a group */
  getMilestones(programId: string, groupCode: string): Promise<ExecutionMilestone[]>;

  /** Get all tasks for a group */
  getTasks(programId: string, groupCode: string): Promise<ExecutionTask[]>;

  /** Get all evidence for a task */
  getEvidence(programId: string, groupCode: string, taskId: string): Promise<ExecutionEvidence[]>;

  /** Get progress for a group */
  getProgress(programId: string, groupCode: string): Promise<ExecutionProgress>;

  /** Get progress for a program */
  getProgramProgress(programId: string): Promise<ExecutionProgress>;

  /** Get certification for a group */
  getCertification(programId: string, groupCode: string): Promise<Certification | null>;

  // ── Mutation (optional) ──────────────────────────────────────────────────

  /** Update task status (if module supports it) */
  updateTaskStatus?(programId: string, groupCode: string, taskId: string, status: string): Promise<void>;

  /** Submit for certification (if module supports it) */
  submitForCertification?(programId: string, groupCode: string): Promise<void>;
}

/**
 * Simple in-memory execution provider for testing and prototyping.
 */
export class InMemoryExecutionProvider implements ExecutionProvider {
  readonly moduleId: string;
  readonly moduleName: string;

  private programs: ExecutionProgram[] = [];

  constructor(moduleId: string, moduleName: string) {
    this.moduleId = moduleId;
    this.moduleName = moduleName;
  }

  /** Load programs into the provider */
  loadPrograms(programs: ExecutionProgram[]): void {
    this.programs = programs;
  }

  async getPrograms(): Promise<ExecutionProgram[]> {
    return this.programs;
  }

  async getProgram(programId: string): Promise<ExecutionProgram | null> {
    return this.programs.find((p) => p.id === programId) ?? null;
  }

  async getGroups(programId: string): Promise<ExecutionGroup[]> {
    const program = await this.getProgram(programId);
    return program?.groups ?? [];
  }

  async getGroup(programId: string, groupCode: string): Promise<ExecutionGroup | null> {
    const groups = await this.getGroups(programId);
    return groups.find((g) => g.code === groupCode) ?? null;
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
    const { calculateGroupProgress } = await import("../calculators");
    const group = await this.getGroup(programId, groupCode);
    if (!group) {
      return { total: 0, done: 0, approved: 0, inProgress: 0, blocked: 0, notStarted: 0, needsReview: 0, percentage: 0 };
    }
    return calculateGroupProgress(group);
  }

  async getProgramProgress(programId: string): Promise<ExecutionProgress> {
    const { calculateProgramProgress } = await import("../calculators");
    const program = await this.getProgram(programId);
    if (!program) {
      return { total: 0, done: 0, approved: 0, inProgress: 0, blocked: 0, notStarted: 0, needsReview: 0, percentage: 0 };
    }
    return calculateProgramProgress(program);
  }

  async getCertification(_programId: string, _groupCode: string): Promise<Certification | null> {
    // Default: no certification
    return null;
  }
}
