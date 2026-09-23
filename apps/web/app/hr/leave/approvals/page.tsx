"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { formatArabicDate } from "../../hr-labels";
import styles from "../../hr.module.css";

interface LeaveRequest {
  id: string;
  employmentId: string;
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  daysCount: string;
  reason: string | null;
  state: string;
  submittedAt: string | null;
}

/**
 * Leave Approval Queue — handles BOTH Manager-level (PENDING_MANAGER) and
 * HR-level (PENDING_HR) approvals via the canonical V2 endpoints:
 *   - POST /api/v2/hr/leave/requests/{id}/manager-approve  (requires HRM.LEAVE.TEAM_APPROVE)
 *   - POST /api/v2/hr/leave/requests/{id}/hr-approve        (requires HRM.LEAVE.HR_APPROVE)
 *
 * Visibility:
 *   - Users with TEAM_APPROVE see PENDING_MANAGER requests + "Manager Approve" button.
 *   - Users with HR_APPROVE see PENDING_HR requests + "HR Approve" button.
 *   - Users with BOTH see both queues (HR typically has both for end-to-end testing).
 *
 * Reject buttons call the matching /manager-reject or /hr-reject endpoint.
 */
export default function LeaveApprovalsPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canManagerApprove = capabilities.includes("HRM.LEAVE.TEAM_APPROVE");
  const canHrApprove = capabilities.includes("HRM.LEAVE.HR_APPROVE");
  const canView = canManagerApprove || canHrApprove;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      // Fetch all pending requests (controller returns PENDING_MANAGER + PENDING_HR
      // when state=PENDING, since the leave state machine treats both as pending
      // approval). The per-row action buttons filter by exact state.
      const res = await fetch("/api/platform/api/v2/hr/leave/requests?state=PENDING", { credentials: "same-origin" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setRequests(await res.json());
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function managerApprove(id: string) {
    setBusy(true);
    try {
      const res = await fetch(`/api/platform/api/v2/hr/leave/requests/${id}/manager-approve`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ comment: "Manager approved (G2 E2E)" }),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice("Manager approved — request escalated to HR");
      await load();
    } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  async function hrApprove(id: string) {
    setBusy(true);
    try {
      const res = await fetch(`/api/platform/api/v2/hr/leave/requests/${id}/hr-approve`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ comment: "HR approved (G2 E2E)" }),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice("HR approved — leave request is now APPROVED");
      await load();
    } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  async function managerReject(id: string) {
    setBusy(true);
    try {
      const res = await fetch(`/api/platform/api/v2/hr/leave/requests/${id}/manager-reject`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ reason: "Manager rejected (G2 E2E)" }),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice("Manager rejected");
      await load();
    } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  async function hrReject(id: string) {
    setBusy(true);
    try {
      const res = await fetch(`/api/platform/api/v2/hr/leave/requests/${id}/hr-reject`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ reason: "HR rejected (G2 E2E)" }),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice("HR rejected");
      await load();
    } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/approvals">
        <p role="alert" className={styles.kpiHint}>
          {t("hrm.recruitment.dashboard.permissionHint")}
        </p>
      </HrWorkspace>
    );
  }

  const columns: HrColumn<LeaveRequest>[] = [
    { key: "startDate", header: "From", render: (r) => formatArabicDate(r.startDate) },
    { key: "endDate", header: "To", render: (r) => formatArabicDate(r.endDate) },
    { key: "daysCount", header: "Days", align: "end" },
    { key: "reason", header: "Reason", render: (r) => r.reason ?? "—" },
    {
      key: "state",
      header: "State",
      render: (r) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} />,
    },
    {
      key: "actions",
      header: "Actions",
      render: (r) => (
        <span className={styles.actionRow}>
          {r.state === "PENDING_MANAGER" && canManagerApprove ? (
            <>
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => void managerApprove(r.id)}
                disabled={busy}
                data-testid={`manager-approve-${r.id}`}
              >
                Manager Approve
              </button>
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => void managerReject(r.id)}
                disabled={busy}
                data-testid={`manager-reject-${r.id}`}
              >
                Manager Reject
              </button>
            </>
          ) : null}
          {r.state === "PENDING_HR" && canHrApprove ? (
            <>
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => void hrApprove(r.id)}
                disabled={busy}
                data-testid={`hr-approve-${r.id}`}
              >
                HR Approve
              </button>
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => void hrReject(r.id)}
                disabled={busy}
                data-testid={`hr-reject-${r.id}`}
              >
                HR Reject
              </button>
            </>
          ) : null}
          {r.state === "APPROVED" ? <span data-testid={`approved-badge-${r.id}`}>✓ Approved</span> : null}
        </span>
      ),
    },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/approvals">
      <header>
        <h1>Leave Approval Queue</h1>
      </header>
      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <HrDataTable<LeaveRequest>
          caption="Pending Leave Requests"
          columns={columns}
          rows={requests}
          rowKey={(r) => r.id}
          emptyTitle="No pending leave requests"
        />
      )}
    </HrWorkspace>
  );
}
