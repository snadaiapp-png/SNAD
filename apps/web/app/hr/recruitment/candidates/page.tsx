"use client";

/**
 * Candidate Directory — G1-T11 Screen 4.
 * ============================================================================
 * Masked-contact table of all candidates with pool-state filter and archive
 * action. Capability-driven: archive column shown only if
 * HRM.RECRUITMENT.CANDIDATE.MANAGE is present.
 *
 * Privacy (spec §10):
 *   - Candidate contact is masked by default (e.g. "محمد ا.", "m•••@example.com").
 *   - Unmasked reads require HRM.PII.VIEW + audit-logged server-side.
 *   - This UI never displays unmasked PII; the masked DTO is the only data
 *     the client receives.
 *
 * Tenant scoping: listCandidates() is session-tenant-scoped.
 *
 * Idempotency: archiveCandidate() sends Idempotency-Key header; replays
 * return the original result (200-replay semantics per HrRecruitmentIdempotencyApiContractTest).
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
  type CandidateSummaryResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { formatArabicDate, candidatePoolStateLabel } from "../../hr-labels";
import styles from "../../hr.module.css";

const POOL_STATE_FILTERS = ["ACTIVE", "DORMANT", "ARCHIVED"] as const;

export default function CandidateDirectoryPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_VIEW);
  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [rows, setRows] = useState<CandidateSummaryResponse[]>([]);
  const [poolFilter, setPoolFilter] = useState<string>("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmRecruitmentApi.listCandidates(
        poolFilter ? { poolState: poolFilter } : undefined,
      );
      setRows(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [poolFilter]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function archive(candidate: CandidateSummaryResponse) {
    setBusyId(candidate.candidateId);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      await hrmRecruitmentApi.archiveCandidate(candidate.candidateId, key);
      setNotice(t("hrm.recruitment.candidates.action.archive"));
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
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/candidates">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const columns: HrColumn<CandidateSummaryResponse>[] = [
    {
      key: "displayName",
      header: t("hrm.recruitment.candidates.col.name"),
      render: (row) => (
        <Link href={`/hr/recruitment/candidates/${row.candidateId}`} className={styles.linkButton}>
          {row.displayName}
        </Link>
      ),
    },
    {
      key: "poolState",
      header: t("hrm.recruitment.candidates.col.poolState"),
      render: (row) => (
        <HrStateBadge
          label={candidatePoolStateLabel(dict, row.poolState)}
          code={row.poolState}
          tone={toneForState(row.poolState)}
        />
      ),
    },
    {
      key: "duplicateWarning",
      header: t("hrm.recruitment.candidates.col.duplicate"),
      render: (row) => row.duplicateWarning
        ? <strong>{t("hrm.recruitment.candidates.duplicate.yes")}</strong>
        : t("hrm.recruitment.candidates.duplicate.no"),
    },
    {
      key: "createdAt",
      header: t("hrm.recruitment.candidates.col.createdAt"),
      render: (row) => formatArabicDate(row.createdAt),
    },
    ...(canManage ? [{
      key: "actions",
      header: t("hrm.recruitment.candidates.col.actions"),
      render: (row: CandidateSummaryResponse) => (
        <span className={styles.actionRow}>
          {row.poolState !== "ARCHIVED" ? (
            <button
              type="button"
              className={styles.linkButton}
              onClick={() => void archive(row)}
              disabled={busyId === row.candidateId}
            >
              {t("hrm.recruitment.candidates.action.archive")}
            </button>
          ) : null}
        </span>
      ),
    }] : []),
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/candidates">
      <header>
        <h1>{t("hrm.recruitment.candidates.title")}</h1>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError ? (
        <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p>
      ) : null}

      <div className={styles.filterBar}>
        <label htmlFor="pool-filter" className={styles.kpiLabel}>
          {t("hrm.recruitment.candidates.col.poolState")}:
        </label>
        <select
          id="pool-filter"
          className={styles.filterSelect}
          value={poolFilter}
          onChange={(e) => setPoolFilter(e.target.value)}
        >
          <option value="">{t("hrm.recruitment.openings.filter.status.all")}</option>
          {POOL_STATE_FILTERS.map((s) => (
            <option key={s} value={s}>{t("hrm.recruitment.candidates.poolState." + s)}</option>
          ))}
        </select>
      </div>

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <HrDataTable<CandidateSummaryResponse>
          caption={t("hrm.recruitment.candidates.caption")}
          columns={columns}
          rows={rows}
          rowKey={(row) => row.candidateId}
          emptyTitle={t("hrm.recruitment.candidates.empty.title")}
          emptyDescription={t("hrm.recruitment.candidates.empty.description")}
        />
      )}
    </HrWorkspace>
  );
}
