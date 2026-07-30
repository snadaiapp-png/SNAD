"use client";

import { useEffect, useState, useCallback } from "react";
import {
  type CrmTask,
  getTasks,
  updateTaskStatus,
  createTask,
} from "../crmApi";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface CreateTaskPayload {
  title: string;
  description?: string;
  priority: string;
  assigneeId?: string;
  dueDate?: string;
}

// ---------------------------------------------------------------------------
// Status / Priority helpers
// ---------------------------------------------------------------------------

const STATUS_OPTIONS = ["all", "OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"] as const;

const STATUS_LABELS: Record<string, string> = {
  all: "All",
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const PRIORITY_OPTIONS = ["LOW", "MEDIUM", "HIGH", "URGENT"] as const;

const PRIORITY_LABELS: Record<string, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

const STATUS_CLASSES: Record<string, string> = {
  OPEN: "bg-slate-100 text-slate-700",
  IN_PROGRESS: "bg-amber-100 text-amber-700",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

const PRIORITY_CLASSES: Record<string, string> = {
  LOW: "bg-slate-100 text-slate-600",
  MEDIUM: "bg-blue-100 text-blue-600",
  HIGH: "bg-orange-100 text-orange-600",
  URGENT: "bg-red-100 text-red-600",
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function TasksTab() {
  const [tasks, setTasks] = useState<CrmTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [showCreate, setShowCreate] = useState(false);
  const [selectedTask, setSelectedTask] = useState<CrmTask | null>(null);

  // Fetch tasks
  const loadTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getTasks({ status: statusFilter === "all" ? undefined : statusFilter });
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

  // Handlers
  const handleCreate = async (payload: CreateTaskPayload) => {
    await createTask(payload);
    setShowCreate(false);
    await loadTasks();
  };

  const handleStatusChange = async (taskId: string, newStatus: string) => {
    await updateTaskStatus(taskId, newStatus);
    setSelectedTask(null);
    await loadTasks();
  };

  // Filtered tasks for client-side fallback
  const filteredTasks =
    statusFilter === "all"
      ? tasks
      : tasks.filter((t) => t.status === statusFilter);

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">Tasks</h2>
        <button
          onClick={() => setShowCreate(true)}
          className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          + New Task
        </button>
      </div>

      {/* Filter bar */}
      <div className="flex gap-2">
        {STATUS_OPTIONS.map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              statusFilter === status
                ? "bg-blue-600 text-white"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200"
            }`}
          >
            {STATUS_LABELS[status]}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center py-12">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
          <span className="ml-2 text-sm text-slate-500">Loading tasks…</span>
        </div>
      ) : error ? (
        <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
          <button onClick={loadTasks} className="ml-2 underline hover:no-underline">
            Retry
          </button>
        </div>
      ) : filteredTasks.length === 0 ? (
        <div className="py-12 text-center text-sm text-slate-500">
          No tasks found.
        </div>
      ) : (
        <div className="divide-y rounded-lg border border-slate-200 bg-white">
          {filteredTasks.map((task) => (
            <TaskRow key={task.id} task={task} onSelect={() => setSelectedTask(task)} />
          ))}
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

// ---------------------------------------------------------------------------
// Task row
// ---------------------------------------------------------------------------

function TaskRow({ task, onSelect }: { task: CrmTask; onSelect: () => void }) {
  return (
    <button
      onClick={onSelect}
      className="flex w-full items-center gap-4 px-4 py-3 text-left hover:bg-slate-50 focus:outline-none"
    >
      {/* Status dot */}
      <span
        className={`inline-block h-2.5 w-2.5 flex-shrink-0 rounded-full ${
          task.status === "COMPLETED"
            ? "bg-emerald-500"
            : task.status === "IN_PROGRESS"
              ? "bg-amber-500"
              : task.status === "CANCELLED"
                ? "bg-red-400"
                : "bg-slate-400"
        }`}
      />

      {/* Title & meta */}
      <div className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium text-slate-900">
          {task.title}
        </span>
        {task.description ? (
          <span className="block truncate text-xs text-slate-500">{task.description}</span>
        ) : null}
      </div>

      {/* Badges */}
      <span
        className={`flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASSES[task.status] ?? "bg-slate-100 text-slate-600"}`}
      >
        {STATUS_LABELS[task.status] ?? task.status}
      </span>
      <span
        className={`flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${PRIORITY_CLASSES[task.priority] ?? "bg-slate-100 text-slate-600"}`}
      >
        {PRIORITY_LABELS[task.priority] ?? task.priority}
      </span>

      {/* Assignee */}
      <span className="flex-shrink-0 text-xs text-slate-500">
        {task.assigneeName ?? task.assigneeId ?? "—"}
      </span>

      {/* Due date */}
      {task.dueDate ? (
        <span className="flex-shrink-0 text-xs text-slate-500">
          {new Date(task.dueDate).toLocaleDateString()}
        </span>
      ) : null}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Create task modal
// ---------------------------------------------------------------------------

function CreateTaskModal({
  onSubmit,
  onClose,
}: {
  onSubmit: (payload: CreateTaskPayload) => Promise<void>;
  onClose: () => void;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<string>("MEDIUM");
  const [dueDate, setDueDate] = useState("");
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
        dueDate: dueDate || undefined,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="mb-4 text-lg font-semibold text-slate-900">New Task</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Title *</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Priority</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                {PRIORITY_OPTIONS.map((p) => (
                  <option key={p} value={p}>
                    {PRIORITY_LABELS[p]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Due Date</label>
              <input
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !title.trim()}
              className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting ? "Creating…" : "Create Task"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Task detail modal
// ---------------------------------------------------------------------------

function TaskDetailModal({
  task,
  onStatusChange,
  onClose,
}: {
  task: CrmTask;
  onStatusChange: (taskId: string, status: string) => Promise<void>;
  onClose: () => void;
}) {
  const [updating, setUpdating] = useState(false);

  const transition = async (newStatus: string) => {
    setUpdating(true);
    try {
      await onStatusChange(task.id, newStatus);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-start justify-between">
          <h3 className="text-lg font-semibold text-slate-900">{task.title}</h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            ✕
          </button>
        </div>

        {task.description ? (
          <p className="mb-4 text-sm text-slate-600">{task.description}</p>
        ) : null}

        <div className="mb-4 grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="font-medium text-slate-700">Status:</span>{" "}
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASSES[task.status] ?? ""}`}
            >
              {STATUS_LABELS[task.status] ?? task.status}
            </span>
          </div>
          <div>
            <span className="font-medium text-slate-700">Priority:</span>{" "}
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${PRIORITY_CLASSES[task.priority] ?? ""}`}
            >
              {PRIORITY_LABELS[task.priority] ?? task.priority}
            </span>
          </div>
          <div>
            <span className="font-medium text-slate-700">Assignee:</span>{" "}
            {task.assigneeName ?? task.assigneeId ?? "—"}
          </div>
          <div>
            <span className="font-medium text-slate-700">Due:</span>{" "}
            {task.dueDate ? new Date(task.dueDate).toLocaleDateString() : "—"}
          </div>
        </div>

        {/* Status transitions */}
        <div className="flex gap-2 border-t border-slate-200 pt-4">
          {task.status === "OPEN" && (
            <button
              onClick={() => transition("IN_PROGRESS")}
              disabled={updating}
              className="rounded-md bg-amber-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
            >
              Start Progress
            </button>
          )}
          {task.status === "IN_PROGRESS" && (
            <button
              onClick={() => transition("COMPLETED")}
              disabled={updating}
              className="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              Mark Complete
            </button>
          )}
          {(task.status === "OPEN" || task.status === "IN_PROGRESS") && (
            <button
              onClick={() => transition("CANCELLED")}
              disabled={updating}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Cancel
            </button>
          )}
          <button
            onClick={onClose}
            className="ml-auto rounded-md px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
