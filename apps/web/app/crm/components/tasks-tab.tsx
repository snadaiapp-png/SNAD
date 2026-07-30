"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmTask } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-command-center.module.css";

/* ============================================================================
 *  Task status / priority helpers
 * ============================================================================ */

const TASK_STATUSES = ["OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"] as const;

const STATUS_LABELS: Record<string, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const STATUS_COLORS: Record<string, string> = {
  OPEN: "var(--snad-info, #3b82f6)",
  IN_PROGRESS: "var(--snad-warning, #f59e0b)",
  COMPLETED: "var(--snad-success, #10b981)",
  CANCELLED: "var(--snad-muted, #6b7280)",
};

const PRIORITY_LABELS: Record<number, string> = {
  1: "Low",
  2: "Medium",
  3: "High",
  4: "Urgent",
};

/* ============================================================================
 *  TasksTab — main component
 * ============================================================================ */

export function TasksTab() {
  const { t } = useCrmI18n();
  const [tasks, setTasks] = useState<CrmTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [showCreate, setShowCreate] = useState(false);
  const [selectedTask, setSelectedTask] = useState<CrmTask | null>(null);

  /* ---------- data fetching ---------- */

  const loadTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await crmApi.tasks(statusFilter || undefined);
      setTasks(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tasks");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadTasks();
  }, [loadTasks]);

  /* ---------- handlers ---------- */

  const handleCreate = async (body: {
    title: string;
    description?: string;
    priority?: number;
    assigneeUserId?: string;
    startAt?: string;
    dueAt?: string;
  }) => {
    await crmApi.createTask(body);
    setShowCreate(false);
    await loadTasks();
  };

  const handleStatusChange = async (taskId: string, action: "start" | "complete" | "cancel", payload?: string) => {
    if (action === "start") await crmApi.startTask(taskId);
    else if (action === "complete") await crmApi.completeTask(taskId, payload);
    else if (action === "cancel") await crmApi.cancelTask(taskId, payload);
    setSelectedTask(null);
    await loadTasks();
  };

  /* ---------- derived ---------- */

  const filteredTasks = statusFilter
    ? tasks.filter((t) => t.status === statusFilter)
    : tasks;

  /* ---------- render ---------- */

  return (
    <div className={styles.tabContainer}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.tasks")}</h2>
        <button
          onClick={() => setShowCreate(true)}
          className={styles.primaryButton}
        >
          {t("tasks.create")}
        </button>
      </div>

      {/* Filter bar */}
      <div className={styles.filterBar}>
        <button
          onClick={() => setStatusFilter("")}
          className={`${styles.filterChip} ${statusFilter === "" ? styles.filterChipActive : ""}`}
        >
          {t("tasks.filter.all")}
        </button>
        {TASK_STATUSES.map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={`${styles.filterChip} ${statusFilter === status ? styles.filterChipActive : ""}`}
          >
            {STATUS_LABELS[status]}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading ? (
        <div className={styles.loadingState}>Loading tasks…</div>
      ) : error ? (
        <div className={styles.errorState}>
          {error}
          <button onClick={loadTasks} className={styles.retryButton}>
            Retry
          </button>
        </div>
      ) : filteredTasks.length === 0 ? (
        <div className={styles.emptyState}>No tasks found.</div>
      ) : (
        <div className={styles.tableContainer}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Title</th>
                <th>Status</th>
                <th>Priority</th>
                <th>Assignee</th>
                <th>Due Date</th>
              </tr>
            </thead>
            <tbody>
              {filteredTasks.map((task) => (
                <tr
                  key={task.id}
                  className={styles.tableRow}
                  onClick={() => setSelectedTask(task)}
                >
                  <td className={styles.tableCellTitle}>{task.title}</td>
                  <td>
                    <span
                      className={styles.statusBadge}
                      style={{ color: STATUS_COLORS[task.status] ?? "inherit" }}
                    >
                      {STATUS_LABELS[task.status] ?? task.status}
                    </span>
                  </td>
                  <td>{PRIORITY_LABELS[task.priority] ?? `P${task.priority}`}</td>
                  <td>{task.assignee_user_id ?? "—"}</td>
                  <td>
                    {task.due_at
                      ? new Date(task.due_at).toLocaleDateString()
                      : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modals */}
      {showCreate && (
        <CreateTaskModal onSubmit={handleCreate} onClose={() => setShowCreate(false)} />
      )}
      {selectedTask && (
        <TaskDetailModal
          task={selectedTask}
          onStatusChange={handleStatusChange}
          onClose={() => setSelectedTask(null)}
        />
      )}
    </div>
  );
}

/* ============================================================================
 *  Create Task Modal
 * ============================================================================ */

function CreateTaskModal({
  onSubmit,
  onClose,
}: {
  onSubmit: (body: {
    title: string;
    description?: string;
    priority?: number;
    assigneeUserId?: string;
    startAt?: string;
    dueAt?: string;
  }) => Promise<void>;
  onClose: () => void;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<number>(2);
  const [dueAt, setDueAt] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit({
        title: title.trim(),
        description: description.trim() || undefined,
        priority,
        dueAt: dueAt || undefined,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modal}>
        <h3 className={styles.modalTitle}>New Task</h3>
        <form onSubmit={handleSubmit} className={styles.modalForm}>
          <div className={styles.formGroup}>
            <label className={styles.formLabel}>Title *</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              className={styles.formInput}
            />
          </div>
          <div className={styles.formGroup}>
            <label className={styles.formLabel}>Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className={styles.formInput}
            />
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label className={styles.formLabel}>Priority</label>
              <select
                value={priority}
                onChange={(e) => setPriority(Number(e.target.value))}
                className={styles.formInput}
              >
                <option value={1}>Low</option>
                <option value={2}>Medium</option>
                <option value={3}>High</option>
                <option value={4}>Urgent</option>
              </select>
            </div>
            <div className={styles.formGroup}>
              <label className={styles.formLabel}>Due Date</label>
              <input
                type="date"
                value={dueAt}
                onChange={(e) => setDueAt(e.target.value)}
                className={styles.formInput}
              />
            </div>
          </div>
          <div className={styles.modalActions}>
            <button type="button" onClick={onClose} className={styles.secondaryButton}>
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !title.trim()}
              className={styles.primaryButton}
            >
              {submitting ? "Creating…" : "Create Task"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/* ============================================================================
 *  Task Detail Modal
 * ============================================================================ */

function TaskDetailModal({
  task,
  onStatusChange,
  onClose,
}: {
  task: CrmTask;
  onStatusChange: (taskId: string, action: "start" | "complete" | "cancel", payload?: string) => Promise<void>;
  onClose: () => void;
}) {
  const [updating, setUpdating] = useState(false);

  const transition = async (action: "start" | "complete" | "cancel", payload?: string) => {
    setUpdating(true);
    try {
      await onStatusChange(task.id, action, payload);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>{task.title}</h3>
          <button onClick={onClose} className={styles.closeButton}>
            ✕
          </button>
        </div>

        {task.description ? (
          <p className={styles.modalDescription}>{task.description}</p>
        ) : null}

        <div className={styles.detailGrid}>
          <div>
            <span className={styles.detailLabel}>Status:</span>{" "}
            <span
              className={styles.statusBadge}
              style={{ color: STATUS_COLORS[task.status] ?? "inherit" }}
            >
              {STATUS_LABELS[task.status] ?? task.status}
            </span>
          </div>
          <div>
            <span className={styles.detailLabel}>Priority:</span>{" "}
            {PRIORITY_LABELS[task.priority] ?? `P${task.priority}`}
          </div>
          <div>
            <span className={styles.detailLabel}>Assignee:</span>{" "}
            {task.assignee_user_id ?? "—"}
          </div>
          <div>
            <span className={styles.detailLabel}>Due:</span>{" "}
            {task.due_at ? new Date(task.due_at).toLocaleDateString() : "—"}
          </div>
        </div>

        {/* Status transitions */}
        <div className={styles.modalActions}>
          {task.status === "OPEN" && (
            <button
              onClick={() => transition("start")}
              disabled={updating}
              className={styles.primaryButton}
            >
              Start Progress
            </button>
          )}
          {task.status === "IN_PROGRESS" && (
            <button
              onClick={() => transition("complete")}
              disabled={updating}
              className={styles.primaryButton}
            >
              Mark Complete
            </button>
          )}
          {(task.status === "OPEN" || task.status === "IN_PROGRESS") && (
            <button
              onClick={() => transition("cancel")}
              disabled={updating}
              className={styles.secondaryButton}
            >
              Cancel
            </button>
          )}
          <button onClick={onClose} className={styles.secondaryButton}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
