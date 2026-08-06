/**
 * SANAD Execution Framework — Constants
 * --------------------------------------
 * Shared constants for the execution framework.
 */

import type { GroupStatus, TaskStatus, TaskType, TaskPriority } from "../types";

// ── Status Labels ────────────────────────────────────────────────────────────

export const GROUP_STATUS_LABELS_AR: Record<GroupStatus, string> = {
  NOT_STARTED: "لم تبدأ",
  IN_PROGRESS: "قيد التنفيذ",
  BLOCKED: "محظورة",
  DONE: "مكتملة",
  NEEDS_REVIEW: "بانتظار المراجعة",
  APPROVED: "معتمدة",
  REJECTED: "مرفوضة",
};

export const GROUP_STATUS_LABELS_EN: Record<GroupStatus, string> = {
  NOT_STARTED: "Not Started",
  IN_PROGRESS: "In Progress",
  BLOCKED: "Blocked",
  DONE: "Done",
  NEEDS_REVIEW: "Needs Review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
};

export const TASK_STATUS_LABELS_AR: Record<TaskStatus, string> = {
  NOT_STARTED: "لم تبدأ",
  IN_PROGRESS: "قيد التنفيذ",
  BLOCKED: "محظورة",
  DONE: "مكتملة",
  NEEDS_REVIEW: "بانتظار المراجعة",
  APPROVED: "معتمدة",
};

export const TASK_STATUS_LABELS_EN: Record<TaskStatus, string> = {
  NOT_STARTED: "Not Started",
  IN_PROGRESS: "In Progress",
  BLOCKED: "Blocked",
  DONE: "Done",
  NEEDS_REVIEW: "Needs Review",
  APPROVED: "Approved",
};

export const TASK_TYPE_LABELS_AR: Record<TaskType, string> = {
  Backend: "خلفية",
  Frontend: "واجهة",
  Database: "قاعدة بيانات",
  API: "API",
  Security: "أمن",
  Test: "اختبار",
  Report: "تقرير",
  Mobile: "جوال",
  AI: "ذكاء اصطناعي",
  Billing: "فوترة",
  Design: "تصميم",
  DevOps: "عمليات",
  Documentation: "توثيق",
};

export const TASK_TYPE_LABELS_EN: Record<TaskType, string> = {
  Backend: "Backend",
  Frontend: "Frontend",
  Database: "Database",
  API: "API",
  Security: "Security",
  Test: "Test",
  Report: "Report",
  Mobile: "Mobile",
  AI: "AI",
  Billing: "Billing",
  Design: "Design",
  DevOps: "DevOps",
  Documentation: "Documentation",
};

export const PRIORITY_LABELS_AR: Record<TaskPriority, string> = {
  Critical: "حرجة",
  High: "عالية",
  Medium: "متوسطة",
  Low: "منخفضة",
};

export const PRIORITY_LABELS_EN: Record<TaskPriority, string> = {
  Critical: "Critical",
  High: "High",
  Medium: "Medium",
  Low: "Low",
};

// ── Status Colors ────────────────────────────────────────────────────────────

export const STATUS_COLORS: Record<GroupStatus, string> = {
  NOT_STARTED: "#6b7280",
  IN_PROGRESS: "#3b82f6",
  BLOCKED: "#ef4444",
  DONE: "#22c55e",
  NEEDS_REVIEW: "#f59e0b",
  APPROVED: "#10b981",
  REJECTED: "#dc2626",
};

// ── Execution Rules ──────────────────────────────────────────────────────────

export const EXECUTION_RULES = {
  /** CERTIFIED group must contain at least one Task */
  CERTIFIED_REQUIRES_TASKS: true,
  /** Progress must equal Completed Tasks / Total Tasks */
  PROGRESS_FROM_TASKS: true,
  /** Progress = 100% requires every Task = DONE */
  PERCENTAGE_REQUIRES_COMPLETION: true,
  /** CERTIFIED requires Acceptance Criteria PASS */
  CERTIFICATION_REQUIRES_CRITERIA: true,
  /** Dashboard must exactly match API */
  DASHBOARD_MATCHES_API: true,
  /** API must exactly match Database */
  API_MATCHES_DATABASE: true,
  /** No duplicated execution state */
  NO_DUPLICATE_STATE: true,
} as const;
