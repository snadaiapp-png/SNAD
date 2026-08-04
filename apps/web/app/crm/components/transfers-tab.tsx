"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmTransfer } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-shared-styles.module.css";

/* ============================================================================
 *  Transfer status / type helpers
 * ============================================================================ */

const STATE_LABELS: Record<string, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under Review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  COMPLETED: "Completed",
  FAILED: "Failed",
};

const STATE_COLORS: Record<string, string> = {
  DRAFT: "var(--snad-color-text-muted)",
  SUBMITTED: "var(--snad-color-info)",
  UNDER_REVIEW: "var(--snad-color-warning)",
  APPROVED: "var(--snad-color-success)",
  REJECTED: "var(--snad-color-error)",
  CANCELLED: "var(--snad-color-text-muted)",
  COMPLETED: "var(--snad-color-success)",
  FAILED: "var(--snad-color-error)",
};

const TRANSFER_TYPE_LABELS: Record<string, string> = {
  PERMANENT: "Permanent",
  TEMPORARY: "Temporary",
};

/* ============================================================================
 *  TransfersTab — main component
 * ============================================================================ */

export function TransfersTab() {
  const { t } = useCrmI18n();
  const [transfers, setTransfers] = useState<CrmTransfer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [stateFilter, setStateFilter] = useState<string>("");
  const [selectedTransfer, setSelectedTransfer] = useState<CrmTransfer | null>(null);

  /* ---------- data fetching ---------- */

  const loadTransfers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await crmApi.transfers(stateFilter || undefined);
      setTransfers(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load transfers");
    } finally {
      setLoading(false);
    }
  }, [stateFilter]);

  useEffect(() => {
    loadTransfers();
  }, [loadTransfers]);

  /* ---------- handlers ---------- */

  const handleApprove = async (transferId: string, comment?: string) => {
    await crmApi.approveTransfer(transferId, comment);
    setSelectedTransfer(null);
    await loadTransfers();
  };

  const handleReject = async (transferId: string, comment?: string) => {
    await crmApi.rejectTransfer(transferId, comment);
    setSelectedTransfer(null);
    await loadTransfers();
  };

  /* ---------- derived ---------- */

  const filteredTransfers = stateFilter
    ? transfers.filter((tr) => tr.state === stateFilter)
    : transfers;

  /* ---------- render ---------- */

  return (
    <div className={styles.tabContainer}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.transfers")}</h2>
      </div>

      {/* Filter bar */}
      <div className={styles.filterBar}>
        <button
          onClick={() => setStateFilter("")}
          className={`${styles.filterChip} ${stateFilter === "" ? styles.filterChipActive : ""}`}
        >
          {t("transfers.filter.all")}
        </button>
        {["SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED"].map((state) => (
          <button
            key={state}
            onClick={() => setStateFilter(state)}
            className={`${styles.filterChip} ${stateFilter === state ? styles.filterChipActive : ""}`}
          >
            {STATE_LABELS[state]}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading ? (
        <div className={styles.loadingState}>Loading transfers…</div>
      ) : error ? (
        <div className={styles.errorState}>
          {error}
          <button onClick={loadTransfers} className={styles.retryButton}>
            Retry
          </button>
        </div>
      ) : filteredTransfers.length === 0 ? (
        <div className={styles.emptyState}>No transfer requests found.</div>
      ) : (
        <div className={styles.tableContainer}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Requester</th>
                <th>Type</th>
                <th>Transfer</th>
                <th>State</th>
                <th>Reason</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {filteredTransfers.map((transfer) => (
                <tr
                  key={transfer.id}
                  className={styles.tableRow}
                  onClick={() => setSelectedTransfer(transfer)}
                >
                  <td className={styles.tableCellTitle}>{transfer.requesterUserId}</td>
                  <td>{TRANSFER_TYPE_LABELS[transfer.transferType] ?? transfer.transferType}</td>
                  <td>{transfer.recordType} ({transfer.recordIds.length})</td>
                  <td>
                    <span
                      className={styles.statusBadge}
                      style={{ color: STATE_COLORS[transfer.state] ?? "inherit" }}
                    >
                      {STATE_LABELS[transfer.state] ?? transfer.state}
                    </span>
                  </td>
                  <td>{transfer.reason.length > 40 ? transfer.reason.slice(0, 40) + "…" : transfer.reason}</td>
                  <td>{new Date(transfer.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Detail Modal */}
      {selectedTransfer && (
        <TransferDetailModal
          transfer={selectedTransfer}
          onApprove={handleApprove}
          onReject={handleReject}
          onClose={() => setSelectedTransfer(null)}
        />
      )}
    </div>
  );
}

/* ============================================================================
 *  Transfer Detail Modal
 * ============================================================================ */

function TransferDetailModal({
  transfer,
  onApprove,
  onReject,
  onClose,
}: {
  transfer: CrmTransfer;
  onApprove: (id: string, comment?: string) => Promise<void>;
  onReject: (id: string, comment?: string) => Promise<void>;
  onClose: () => void;
}) {
  const [comment, setComment] = useState("");
  const [action, setAction] = useState<"approve" | "reject" | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!action) return;
    setSubmitting(true);
    try {
      if (action === "approve") await onApprove(transfer.id, comment || undefined);
      else await onReject(transfer.id, comment || undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>Transfer Request</h3>
          <button onClick={onClose} className={styles.closeButton}>✕</button>
        </div>

        <div className={styles.detailGrid}>
          <div>
            <span className={styles.detailLabel}>Status:</span>{" "}
            <span
              className={styles.statusBadge}
              style={{ color: STATE_COLORS[transfer.state] ?? "inherit" }}
            >
              {STATE_LABELS[transfer.state] ?? transfer.state}
            </span>
          </div>
          <div>
            <span className={styles.detailLabel}>Type:</span>{" "}
            {TRANSFER_TYPE_LABELS[transfer.transferType] ?? transfer.transferType}
          </div>
          <div>
            <span className={styles.detailLabel}>Records:</span>{" "}
            {transfer.recordType} ({transfer.recordIds.length} item{transfer.recordIds.length !== 1 ? "s" : ""})
          </div>
          <div>
            <span className={styles.detailLabel}>Requester:</span>{" "}
            {transfer.requesterUserId}
          </div>
          <div>
            <span className={styles.detailLabel}>Current Owner:</span>{" "}
            {transfer.currentOwnerUserId}
          </div>
          <div>
            <span className={styles.detailLabel}>Proposed Owner:</span>{" "}
            {transfer.proposedOwnerUserId ?? transfer.proposedOwnerTeamId ?? "—"}
          </div>
          <div>
            <span className={styles.detailLabel}>Policy:</span>{" "}
            {transfer.policy}
          </div>
          <div>
            <span className={styles.detailLabel}>Created:</span>{" "}
            {new Date(transfer.createdAt).toLocaleString()}
          </div>
        </div>

        <div className={styles.formGroup} style={{ marginTop: "0.75rem" }}>
          <span className={styles.detailLabel}>Reason:</span>
          <p style={{ margin: "0.25rem 0 0" }}>{transfer.reason}</p>
        </div>

        {/* Action buttons for pending transfers */}
        {(transfer.state === "SUBMITTED" || transfer.state === "UNDER_REVIEW") && (
          <div className={styles.formGroup} style={{ marginTop: "1rem" }}>
            <label className={styles.formLabel}>Comment (optional)</label>
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={2}
              className={styles.formInput}
              placeholder="Add a comment for your decision…"
            />
            <div className={styles.modalActions} style={{ marginTop: "0.75rem" }}>
              <button
                onClick={() => { setAction("approve"); }}
                disabled={submitting}
                className={styles.primaryButton}
                style={{ background: "var(--snad-color-success)" }}
              >
                {submitting && action === "approve" ? "Approving…" : "Approve"}
              </button>
              <button
                onClick={() => { setAction("reject"); }}
                disabled={submitting}
                className={styles.secondaryButton}
                style={{ color: "var(--snad-color-error)" }}
              >
                {submitting && action === "reject" ? "Rejecting…" : "Reject"}
              </button>
            </div>
            {action && (
              <div className={styles.modalActions} style={{ marginTop: "0.5rem" }}>
                <button
                  onClick={handleSubmit}
                  disabled={submitting}
                  className={styles.primaryButton}
                >
                  {submitting ? "Submitting…" : `Confirm ${action === "approve" ? "Approval" : "Rejection"}`}
                </button>
                <button onClick={() => setAction(null)} className={styles.secondaryButton}>
                  Cancel
                </button>
              </div>
            )}
          </div>
        )}

        <div className={styles.modalActions} style={{ marginTop: "1rem" }}>
          <button onClick={onClose} className={styles.secondaryButton}>Close</button>
        </div>
      </div>
    </div>
  );
}
