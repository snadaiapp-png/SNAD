"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmAccount, CrmOpportunity, CrmPipeline, CrmStage } from "@/lib/api/crm";
import { CrmPipelineBoard } from "../crm-pipeline-board";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-command-center.module.css";

/* ============================================================================
 *  PipelineTab — data-fetching wrapper for the Kanban board
 * ----------------------------------------------------------------------------
 *  Responsibilities:
 *    • Fetch pipelines, stages (per pipeline), opportunities, and accounts.
 *    • Manage loading / error / retry / empty states.
 *    • Provide search and status filtering above the board.
 *    • Translate the raw arrays into the shapes the board expects:
 *        - stages:    Record<pipelineId, CrmStage[]>
 *        - accountNames: Map<accountId, display_name>
 *    • Handle optimistic stage moves with rollback on failure.
 * ============================================================================ */

const STATUS_FILTERS = ["OPEN", "WON", "LOST", "ABANDONED"] as const;
type StatusFilter = (typeof STATUS_FILTERS)[number];

export function PipelineTab() {
  const { t } = useCrmI18n();

  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stagesByPipeline, setStagesByPipeline] = useState<Record<string, CrmStage[]>>({});
  const [opportunities, setOpportunities] = useState<CrmOpportunity[]>([]);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter | "">("");
  const [busy, setBusy] = useState(false);

  /* ── Data fetching ────────────────────────────────────────────────────── */

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [pipelineData, oppData, accountData] = await Promise.all([
        crmApi.pipelines(),
        crmApi.opportunities(),
        crmApi.accounts(),
      ]);

      // Fetch stages for every pipeline in parallel.
      const stageEntries = await Promise.all(
        pipelineData.map(async (p) => [p.id, await crmApi.stages(p.id)] as const),
      );
      const stagesMap: Record<string, CrmStage[]> = {};
      for (const [id, stageList] of stageEntries) {
        stagesMap[id] = stageList;
      }

      setPipelines(pipelineData);
      setStagesByPipeline(stagesMap);
      setOpportunities(oppData);
      setAccounts(accountData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load pipeline data");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  /* ── Derived data ─────────────────────────────────────────────────────── */

  const accountNames = useMemo(() => {
    const map = new Map<string, string>();
    for (const a of accounts) {
      map.set(a.id, a.display_name);
    }
    return map;
  }, [accounts]);

  const filteredOpportunities = useMemo(() => {
    if (!statusFilter) return opportunities;
    return opportunities.filter((o) => o.status === statusFilter);
  }, [opportunities, statusFilter]);

  const hasAnyData = pipelines.length > 0 || opportunities.length > 0;

  /* ── Handlers ─────────────────────────────────────────────────────────── */

  const handleSearchSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    // Search is applied live via the board prop; this just prevents form nav.
  }, []);

  const handleMove = useCallback(
    async (opportunityId: string, stageId: string) => {
      // Optimistic update: move the card immediately, roll back on failure.
      const previous = opportunities;
      setOpportunities((prev) =>
        prev.map((o) => (o.id === opportunityId ? { ...o, stage_id: stageId } : o)),
      );
      setBusy(true);
      try {
        await crmApi.moveOpportunity(opportunityId, stageId);
      } catch (err) {
        // Roll back to the pre-move state.
        setOpportunities(previous);
        setError(
          err instanceof Error ? err.message : "Failed to move opportunity — rolled back",
        );
      } finally {
        setBusy(false);
      }
    },
    [opportunities],
  );

  /* ── Render ───────────────────────────────────────────────────────────── */

  return (
    <div className={styles.tabContent}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.pipeline")}</h2>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={fetchAll}
          disabled={loading}
        >
          {t("pipeline.refresh")}
        </button>
      </div>

      {/* Search + status filter bar */}
      <form onSubmit={handleSearchSubmit} className={styles.filterBar}>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("pipeline.searchPlaceholder")}
          className={styles.formInput}
          style={{ maxWidth: 300 }}
        />
      </form>

      <div className={styles.filterBar}>
        <button
          type="button"
          className={`${styles.filterChip} ${statusFilter === "" ? styles.filterChipActive : ""}`}
          onClick={() => setStatusFilter("")}
        >
          {t("pipeline.filter.allStatus")}
        </button>
        {STATUS_FILTERS.map((status) => (
          <button
            key={status}
            type="button"
            className={`${styles.filterChip} ${statusFilter === status ? styles.filterChipActive : ""}`}
            onClick={() => setStatusFilter(status)}
          >
            {t(`opportunities.status.${status.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {/* Error banner */}
      {error && (
        <div className={styles.errorBanner}>
          {error}
          <button type="button" className={styles.dismissButton} onClick={() => setError(null)}>
            ×
          </button>
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className={styles.loadingState}>
          <div className={styles.spinner} />
          <span>{t("pipeline.loading")}</span>
        </div>
      )}

      {/* Error with retry */}
      {!loading && error && (
        <div className={styles.emptyLeads}>
          <p>{t("pipeline.error")}</p>
          <button type="button" className={styles.primaryButton} onClick={fetchAll}>
            {t("pipeline.retry")}
          </button>
        </div>
      )}

      {/* Empty state — no pipelines configured */}
      {!loading && !error && !hasAnyData && (
        <div className={styles.emptyLeads}>
          <p>{t("pipeline.empty")}</p>
        </div>
      )}

      {/* Board */}
      {!loading && !error && hasAnyData && (
        <CrmPipelineBoard
          pipelines={pipelines}
          stages={stagesByPipeline}
          opportunities={filteredOpportunities}
          accountNames={accountNames}
          busy={busy}
          searchQuery={search}
          onMove={handleMove}
        />
      )}
    </div>
  );
}
