"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmOpportunity, CrmPipeline, CrmStage } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-command-center.module.css";

/* ============================================================================
 *  Opportunity constants
 * ============================================================================ */

const OPPORTUNITY_STATUSES = ["OPEN", "WON", "LOST", "ABANDONED"] as const;
type OpportunityStatus = (typeof OPPORTUNITY_STATUSES)[number];

const STATUS_COLORS: Record<string, string> = {
  OPEN: "var(--snad-info, #3b82f6)",
  WON: "var(--snad-success, #10b981)",
  LOST: "var(--snad-error, #ef4444)",
  ABANDONED: "var(--snad-muted, #6b7280)",
};

/* ============================================================================
 *  OpportunitiesTab — main component
 * ============================================================================ */

export function OpportunitiesTab() {
  const { t } = useCrmI18n();
  const [opportunities, setOpportunities] = useState<CrmOpportunity[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<CrmStage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<OpportunityStatus | "">("");
  const [pipelineFilter, setPipelineFilter] = useState<string>("");
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [moveTarget, setMoveTarget] = useState<CrmOpportunity | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [oppData, pipelineData] = await Promise.all([
        crmApi.opportunities(),
        crmApi.pipelines(),
      ]);
      setOpportunities(oppData);
      setPipelines(pipelineData);
      // Fetch stages for all pipelines
      const allStages: CrmStage[] = [];
      for (const p of pipelineData) {
        const s = await crmApi.stages(p.id);
        allStages.push(...s);
      }
      setStages(allStages);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load opportunities");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleOpportunityCreated = useCallback(() => {
    setShowCreateForm(false);
    fetchData();
  }, [fetchData]);

  const handleStageMoved = useCallback(() => {
    setMoveTarget(null);
    fetchData();
  }, [fetchData]);

  const getStageName = useCallback((stageId: string) => {
    return stages.find((s) => s.id === stageId)?.name ?? stageId;
  }, [stages]);

  const getPipelineName = useCallback((pipelineId: string) => {
    return pipelines.find((p) => p.id === pipelineId)?.name ?? pipelineId;
  }, [pipelines]);

  const filteredOpportunities = opportunities.filter((opp) => {
    if (statusFilter && opp.status !== statusFilter) return false;
    if (pipelineFilter && opp.pipeline_id !== pipelineFilter) return false;
    return true;
  });

  return (
    <div className={styles.tabContent}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.opportunities")}</h2>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={() => setShowCreateForm(true)}
        >
          {t("opportunities.create")}
        </button>
      </div>

      {/* Filters */}
      <div className={styles.filterBar}>
        <button
          type="button"
          className={`${styles.filterChip} ${statusFilter === "" ? styles.filterChipActive : ""}`}
          onClick={() => setStatusFilter("")}
        >
          {t("opportunities.filter.all")}
        </button>
        {OPPORTUNITY_STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            className={`${styles.filterChip} ${statusFilter === status ? styles.filterChipActive : ""}`}
            onClick={() => setStatusFilter(status)}
          >
            {t(`opportunities.status.${status.toLowerCase()}`)}
          </button>
        ))}
        <select
          className={styles.formInput}
          style={{ maxWidth: 200, padding: "4px 8px" }}
          value={pipelineFilter}
          onChange={(e) => setPipelineFilter(e.target.value)}
        >
          <option value="">{t("opportunities.filter.allPipelines")}</option>
          {pipelines.map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>

      {/* Error */}
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
          <span>{t("opportunities.loading")}</span>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && filteredOpportunities.length === 0 && (
        <div className={styles.emptyLeads}>
          <p>{t("opportunities.empty")}</p>
        </div>
      )}

      {/* Opportunities table */}
      {!loading && filteredOpportunities.length > 0 && (
        <div className={styles.tableWrapper}>
          <table className={styles.dataTable}>
            <thead>
              <tr>
                <th>{t("opportunities.column.name")}</th>
                <th>{t("opportunities.column.pipeline")}</th>
                <th>{t("opportunities.column.stage")}</th>
                <th>{t("opportunities.column.amount")}</th>
                <th>{t("opportunities.column.probability")}</th>
                <th>{t("opportunities.column.status")}</th>
                <th>{t("opportunities.column.updated")}</th>
                <th>{t("opportunities.column.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {filteredOpportunities.map((opp) => (
                <tr key={opp.id}>
                  <td className={styles.cellPrimary}>{opp.name}</td>
                  <td>{getPipelineName(opp.pipeline_id)}</td>
                  <td>{getStageName(opp.stage_id)}</td>
                  <td>
                    {opp.amount != null
                      ? `${opp.currency_code} ${opp.amount.toLocaleString()}`
                      : "—"}
                  </td>
                  <td>{Math.round(opp.probability * 100)}%</td>
                  <td>
                    <span
                      className={styles.statusBadge}
                      style={{ backgroundColor: STATUS_COLORS[opp.status] ?? "var(--snad-muted)" }}
                    >
                      {t(`opportunities.status.${opp.status.toLowerCase()}`)}
                    </span>
                  </td>
                  <td>{new Date(opp.updated_at).toLocaleDateString()}</td>
                  <td>
                    <div className={styles.actionGroup}>
                      {opp.status === "OPEN" && (
                        <button
                          type="button"
                          className={styles.convertButton}
                          onClick={() => setMoveTarget(opp)}
                        >
                          {t("opportunities.action.moveStage")}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create form modal */}
      {showCreateForm && (
        <OpportunitiesCreateForm
          pipelines={pipelines}
          stages={stages}
          onCreated={handleOpportunityCreated}
          onCancel={() => setShowCreateForm(false)}
        />
      )}

      {/* Move stage dialog */}
      {moveTarget && (
        <MoveStageDialog
          opportunity={moveTarget}
          stages={stages.filter((s) => s.pipeline_id === moveTarget.pipeline_id)}
          onMoved={handleStageMoved}
          onCancel={() => setMoveTarget(null)}
        />
      )}
    </div>
  );
}

/* ============================================================================
 *  OpportunitiesCreateForm — modal form for creating a new opportunity
 * ============================================================================ */

function OpportunitiesCreateForm({
  pipelines,
  stages,
  onCreated,
  onCancel,
}: {
  pipelines: CrmPipeline[];
  stages: CrmStage[];
  onCreated: () => void;
  onCancel: () => void;
}) {
  const { t } = useCrmI18n();
  const [name, setName] = useState("");
  const [pipelineId, setPipelineId] = useState(pipelines[0]?.id ?? "");
  const [stageId, setStageId] = useState("");
  const [amount, setAmount] = useState("");
  const [currencyCode, setCurrencyCode] = useState("SAR");
  const [accountId, setAccountId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pipelineStages = stages
    .filter((s) => s.pipeline_id === pipelineId)
    .sort((a, b) => a.sequence - b.sequence);

  // Auto-select first stage when pipeline changes
  const handlePipelineChange = useCallback((newPipelineId: string) => {
    setPipelineId(newPipelineId);
    const firstStage = stages
      .filter((s) => s.pipeline_id === newPipelineId)
      .sort((a, b) => a.sequence - b.sequence)[0];
    setStageId(firstStage?.id ?? "");
  }, [stages]);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError(t("opportunities.create.nameRequired"));
      return;
    }
    if (!pipelineId || !stageId) {
      setError(t("opportunities.create.pipelineRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await crmApi.createOpportunity({
        accountId: accountId || "00000000-0000-0000-0000-000000000000",
        pipelineId,
        stageId,
        name: name.trim(),
        amount: amount ? parseFloat(amount) : undefined,
        currencyCode,
      });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create opportunity");
    } finally {
      setSubmitting(false);
    }
  }, [name, pipelineId, stageId, amount, currencyCode, accountId, onCreated, t]);

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>{t("opportunities.create.title")}</h3>
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.formGroup}>
            <label htmlFor="opp-name">{t("opportunities.create.name")} *</label>
            <input
              id="opp-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={styles.formInput}
              required
            />
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label htmlFor="opp-pipeline">{t("opportunities.create.pipeline")} *</label>
              <select
                id="opp-pipeline"
                value={pipelineId}
                onChange={(e) => handlePipelineChange(e.target.value)}
                className={styles.formInput}
              >
                {pipelines.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="opp-stage">{t("opportunities.create.stage")} *</label>
              <select
                id="opp-stage"
                value={stageId}
                onChange={(e) => setStageId(e.target.value)}
                className={styles.formInput}
              >
                {pipelineStages.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label htmlFor="opp-amount">{t("opportunities.create.amount")}</label>
              <input
                id="opp-amount"
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className={styles.formInput}
                min="0"
                step="0.01"
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="opp-currency">{t("opportunities.create.currency")}</label>
              <select
                id="opp-currency"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value)}
                className={styles.formInput}
              >
                <option value="SAR">SAR — ريال سعودي</option>
                <option value="USD">USD — US Dollar</option>
                <option value="EUR">EUR — Euro</option>
              </select>
            </div>
          </div>
          {error && <div className={styles.formError}>{error}</div>}
          <div className={styles.formActions}>
            <button type="button" className={styles.cancelButton} onClick={onCancel}>
              {t("opportunities.create.cancel")}
            </button>
            <button type="submit" className={styles.primaryButton} disabled={submitting}>
              {submitting ? t("opportunities.create.submitting") : t("opportunities.create.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/* ============================================================================
 *  MoveStageDialog — dialog for moving an opportunity to a new stage
 * ============================================================================ */

function MoveStageDialog({
  opportunity,
  stages,
  onMoved,
  onCancel,
}: {
  opportunity: CrmOpportunity;
  stages: CrmStage[];
  onMoved: () => void;
  onCancel: () => void;
}) {
  const { t } = useCrmI18n();
  const [selectedStageId, setSelectedStageId] = useState(opportunity.stage_id);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sortedStages = [...stages].sort((a, b) => a.sequence - b.sequence);

  const handleMove = useCallback(async () => {
    if (selectedStageId === opportunity.stage_id) {
      onMoved();
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await crmApi.moveOpportunity(opportunity.id, selectedStageId, reason || undefined);
      onMoved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to move opportunity");
    } finally {
      setSubmitting(false);
    }
  }, [opportunity.id, opportunity.stage_id, selectedStageId, reason, onMoved]);

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>{t("opportunities.move.title")}</h3>
        <div className={styles.convertSummary}>
          <p><strong>{t("opportunities.move.name")}:</strong> {opportunity.name}</p>
          <p><strong>{t("opportunities.move.currentStage")}:</strong> {opportunity.stage_id}</p>
        </div>
        <div className={styles.formGroup}>
          <label htmlFor="move-stage">{t("opportunities.move.newStage")} *</label>
          <select
            id="move-stage"
            value={selectedStageId}
            onChange={(e) => setSelectedStageId(e.target.value)}
            className={styles.formInput}
          >
            {sortedStages.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </div>
        <div className={styles.formGroup}>
          <label htmlFor="move-reason">{t("opportunities.move.reason")}</label>
          <input
            id="move-reason"
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className={styles.formInput}
            placeholder={t("opportunities.move.reasonPlaceholder")}
          />
        </div>
        {error && <div className={styles.formError}>{error}</div>}
        <div className={styles.formActions}>
          <button type="button" className={styles.cancelButton} onClick={onCancel}>
            {t("opportunities.move.cancel")}
          </button>
          <button type="button" className={styles.primaryButton} onClick={handleMove} disabled={submitting}>
            {submitting ? t("opportunities.move.submitting") : t("opportunities.move.submit")}
          </button>
        </div>
      </div>
    </div>
  );
}
