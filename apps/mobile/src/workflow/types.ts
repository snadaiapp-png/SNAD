/**
 * R2-I Mobile Action/Decision Center — workflow wire types
 *
 * Shapes are mirrored 1:1 from the backend contract:
 *   - WorkflowDtos.WorkItemResponse   (apps/sanad-platform/.../workflow/api/WorkflowDtos.java)
 *   - WorkflowDtos.ApprovalResponse
 *   - WorkflowNotificationController  (JDBC queryForList → snake_case columns)
 */

/** Mirrors WorkflowDtos.WorkItemResponse (camelCase JSON). */
export interface WorkItem {
  id: string;
  workflowInstanceId: string;
  workflowStepInstanceId: string;
  type: string;
  status: string;
  assigneeEmployeeId: string;
  claimedByEmployeeId: string;
  assignmentMode: string;
  title: string;
  priority: number;
  dueAt: string;
  /** Optimistic-lock version — ALWAYS sent back as expectedVersion on mutations. */
  version: number;
}

/** Mirrors WorkflowDtos.ApprovalResponse (camelCase JSON). */
export interface ApprovalDecision {
  id: string;
  workflowInstanceId: string;
  workflowStepInstanceId: string;
  requestedFromUserId: string;
  requestedFromEmployeeId: string;
  status: string;
  decision: string;
  comments: string;
  /** Optimistic-lock version — ALWAYS sent back as expectedVersion on decisions. */
  version: number;
}

/**
 * In-app notification row (GET /api/v1/workflows/notifications).
 * The endpoint serializes raw JDBC rows, so columns stay snake_case.
 */
export interface WorkflowNotification {
  id: string;
  event_type: string;
  workflow_instance_id: string | null;
  work_item_id: string | null;
  external_action_id: string | null;
  title: string;
  body: string;
  /** Only https:// deep links are honored on mobile (see workflow/deep-link). */
  deep_link: string | null;
  priority: string;
  read_at: string | null;
  created_at: string;
}
