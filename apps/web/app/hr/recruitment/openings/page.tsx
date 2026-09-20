"use client";

/**
 * Job Openings List — G1-T11 Screen 2.
 * ============================================================================
 * Table view of all job openings with state badges, status filter, and
 * row actions (pause/resume, close). Capability-driven: the actions
 * column is rendered only if HRM.RECRUITMENT.OPENING.MANAGE is present.
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.OPENING.VIEW required to view the table itself.
 *   - HRM.RECRUITMENT.OPENING.MANAGE required to see action buttons.
 *   - HRM.RECRUITMENT.OPENING.PUBLISH required to see submit/approve/reject.
 *
 * Tenant scoping: listOpenings() is session-tenant-scoped.
 *
 * Arabic/RTL: HrDataTable wraps the table with dir="rtl" and logical CSS.
 * Backend status codes are rendered via openingStatusLabel(dict, code)
 * with raw-code fallback (never invented).
 *
 * Idempotency: pause/close mutations send Idempotency-Key header; the
 * backend replays the original result on retry. UI shows a notice after
 * a successful action and refetches the list.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmRecruitmentApi,
  type OpeningSummaryResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { openingStatusLabel } from "../../hr-labels";
import styles from "../../hr.module.css";

const STATUS_FILTER_OPTIONS = ["DRAFT", "IN_APPROVAL", "OPEN", "PAUSED", "CLOSED", "CANCELLED"] as const;

export default function JobOpeningsListPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_VIEW);
  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_MANAGE);
  const canPublish = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_PUBLISH);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [rows, setRows] = useState<OpeningSummaryResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmRecruitmentApi.listOpenings(
        statusFilter ? { status: statusFilter } : undefined,
      );
      setRows(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function rowAction(kind: "pause" | "close" | "submit" | "approve" | "reject", opening: OpeningSummaryResponse) {
    setBusyId(opening.openingId);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      if (kind === "pause") await hrmRecruitmentApi.pauseOpening(opening.openingId, key);
      else if (kind === "close") await hrmRecruitmentApi.closeOpening(opening.openingId, key);
      else if (kind === "submit") await hrmRecruitmentApi.submitOpening(opening.openingId, key);
      else if (kind === "approve") await hrmRecruitmentApi.approveOpening(opening.openingId, key);
      else if (kind === "reject") await hrmRecruitmentApi.rejectOpening(opening.openingId, key, "rejected via ui");
      setNotice(t("hrm.recruitment.openings.action." + kind));
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusyId(null);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/openings">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const columns: HrColumn<OpeningSummaryResponse>[] = [
    {
      key: "title",
      header: t("hrm.recruitment.openings.col.title"),
      render: (row) => (
        <Link href={`/hr/recruitment/openings/${row.openingId}`} className={styles.linkButton}>
          {row.title}
        </Link>
      ),
    },
    {
      key: "departmentName",
      header: t("hrm.recruitment.openings.col.department"),
      render: (row) => row.departmentName ?? "—",
    },
    { key: "headcount", header: t("hrm.recruitment.openings.col.headcount"), align: "end" },
    { key: "filledCount", header: t("hrm.recruitment.openings.col.filled"), align: "end" },
    {
      key: "status",
      header: t("hrm.recruitment.openings.col.status"),
      render: (row) => (
        <HrStateBadge
          label={openingStatusLabel(dict, row.status)}
          code={row.status}
          tone={toneForState(row.status)}
        />
      ),
    },
    ...((canManage || canPublish) ? [{
      key: "actions",
      header: t("hrm.recruitment.openings.col.actions"),
      render: (row: OpeningSummaryResponse) => (
        <span className={styles.actionRow}>
          {canPublish && row.status === "DRAFT" ? (
            <button
              type="button"
              className={styles.linkButton}
              onClick={() => void rowAction("submit", row)}
              disabled={busyId === row.openingId}
              aria-label={t("hrm.recruitment.openings.action.submit")}
            >
              {t("hrm.recruitment.openings.action.submit")}
            </button>
          ) : null}
          {canPublish && row.status === "IN_APPROVAL" ? (
            <>
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => void rowAction("approve", row)}
                disabled={busyId === row.openingId}
              >
                {t("hrm.recruitment.openings.action.approve")}
              </button>
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => void rowAction("reject", row)}
                disabled={busyId === row.openingId}
              >
                {t("hrm.recruitment.openings.action.reject")}
              </button>
            </>
          ) : null}
          {canManage && row.status === "OPEN" ? (
            <button
              type="button"
              className={styles.linkButton}
              onClick={() => void rowAction("pause", row)}
              disabled={busyId === row.openingId}
            >
              {t("hrm.recruitment.openings.action.pause")}
            </button>
          ) : null}
          {canManage && (row.status === "OPEN" || row.status === "PAUSED") ? (
            <button
              type="button"
              className={styles.linkButton}
              onClick={() => void rowAction("close", row)}
              disabled={busyId === row.openingId}
            >
              {t("hrm.recruitment.openings.action.close")}
            </button>
          ) : null}
        </span>
      ),
    }] : []),
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/openings">
      <header>
        <h1>{t("hrm.recruitment.openings.title")}</h1>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError ? (
        <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p>
      ) : null}

      <div className={styles.filterBar}>
        <label htmlFor="status-filter" className={styles.kpiLabel}>
          {t("hrm.recruitment.openings.filter.status.all")}:
        </label>
        <select
          id="status-filter"
          className={styles.filterSelect}
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">{t("hrm.recruitment.openings.filter.status.all")}</option>
          {STATUS_FILTER_OPTIONS.map((s) => (
            <option key={s} value={s}>{t("hrm.recruitment.openings.filter.status." + s)}</option>
          ))}
        </select>
      </div>

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <HrDataTable<OpeningSummaryResponse>
          caption={t("hrm.recruitment.openings.caption")}
          columns={columns}
          rows={rows}
          rowKey={(row) => row.openingId}
          emptyTitle={t("hrm.recruitment.openings.empty.title")}
          emptyDescription={t("hrm.recruitment.openings.empty.description")}
        />
      )}
    </HrWorkspace>
  );
}
