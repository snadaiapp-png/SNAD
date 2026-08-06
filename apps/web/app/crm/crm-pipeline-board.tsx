"use client";

import { type DragEvent, type KeyboardEvent, useCallback, useMemo, useState } from "react";
import { type CrmOpportunity, type CrmPipeline, type CrmStage } from "@/lib/api/crm";
import { useCrmI18n } from "./crm-i18n";
import styles from "./crm.module.css";

interface PipelineBoardProps {
  pipelines: CrmPipeline[];
  stages: Record<string, CrmStage[]>;
  opportunities: CrmOpportunity[];
  accountNames: Map<string, string>;
  busy: boolean;
  searchQuery?: string;
  onMove: (opportunityId: string, stageId: string) => Promise<void> | void;
}

export function adjacentStageId(
  pipelineStages: CrmStage[],
  currentStageId: string,
  direction: -1 | 1,
): string | null {
  const ordered = [...pipelineStages].sort((left, right) => left.sequence - right.sequence);
  const current = ordered.findIndex((stage) => stage.id === currentStageId);
  const target = current + direction;
  return current >= 0 && target >= 0 && target < ordered.length ? ordered[target].id : null;
}

/* ============================================================================
 *  Formatting helpers
 * ============================================================================ */

function formatAmount(amount: number | null | undefined, currency: string): string {
  if (amount == null) return "—";
  return `${currency} ${amount.toLocaleString()}`;
}

function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

function columnTotal(opps: CrmOpportunity[]): number {
  return opps.reduce((sum, o) => sum + (o.amount ?? 0), 0);
}

function columnWeighted(opps: CrmOpportunity[], stage: CrmStage): number {
  return opps.reduce((sum, o) => sum + (o.amount ?? 0) * stage.probability, 0);
}

export function CrmPipelineBoard({
  pipelines,
  stages,
  opportunities,
  accountNames,
  busy,
  searchQuery = "",
  onMove,
}: PipelineBoardProps) {
  const { t } = useCrmI18n();
  const [selectedPipeline, setSelectedPipeline] = useState(pipelines[0]?.id ?? "");
  const [announcement, setAnnouncement] = useState("");
  const pipelineId = pipelines.some((pipeline) => pipeline.id === selectedPipeline)
    ? selectedPipeline
    : pipelines[0]?.id ?? "";

  const pipelineStages = useMemo(
    () => [...(stages[pipelineId] ?? [])].sort((left, right) => left.sequence - right.sequence),
    [pipelineId, stages],
  );

  const pipelineOpportunities = useMemo(() => {
    const byPipeline = opportunities.filter(
      (opportunity) => opportunity.pipeline_id === pipelineId,
    );
    if (!searchQuery.trim()) return byPipeline;
    const q = searchQuery.trim().toLowerCase();
    return byPipeline.filter(
      (opp) =>
        opp.name.toLowerCase().includes(q) ||
        (accountNames.get(opp.account_id) ?? "").toLowerCase().includes(q),
    );
  }, [opportunities, pipelineId, searchQuery, accountNames]);

  const boardCurrency =
    pipelines.find((p) => p.id === pipelineId)?.currency_code ?? "SAR";
  const boardTotal = useMemo(
    () => columnTotal(pipelineOpportunities),
    [pipelineOpportunities],
  );
  const boardWeighted = useMemo(
    () =>
      pipelineStages.reduce(
        (sum, stage) =>
          sum + columnWeighted(pipelineOpportunities.filter((o) => o.stage_id === stage.id), stage),
        0,
      ),
    [pipelineStages, pipelineOpportunities],
  );

  const move = useCallback(
    async (opportunity: CrmOpportunity, stageId: string) => {
      if (busy || opportunity.status !== "OPEN" || opportunity.stage_id === stageId) return;
      const stage = pipelineStages.find((item) => item.id === stageId);
      await onMove(opportunity.id, stageId);
      setAnnouncement(
        t("board.aria.moved").replace("{name}", opportunity.name).replace("{stage}", stage?.name ?? t("board.newStage")),
      );
    },
    [busy, onMove, pipelineStages, t],
  );

  const moveAdjacent = useCallback(
    (opportunity: CrmOpportunity, direction: -1 | 1) => {
      const target = adjacentStageId(pipelineStages, opportunity.stage_id, direction);
      if (target) void move(opportunity, target);
    },
    [pipelineStages, move],
  );

  function handleKeyDown(event: KeyboardEvent<HTMLElement>, opportunity: CrmOpportunity) {
    if (!event.altKey) return;
    if (event.key === "ArrowRight") {
      event.preventDefault();
      moveAdjacent(opportunity, -1);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      moveAdjacent(opportunity, 1);
    }
  }

  function handleDrop(event: DragEvent<HTMLElement>, stageId: string) {
    event.preventDefault();
    const opportunityId = event.dataTransfer.getData("application/x-snad-opportunity-id");
    const opportunity = pipelineOpportunities.find((item) => item.id === opportunityId);
    if (opportunity) void move(opportunity, stageId);
  }

  if (pipelines.length === 0) {
    return <p className={styles.emptyState}>{t("board.empty.noPipeline")}</p>;
  }

  return (
    <section className={styles.pipelineBoardSection} aria-labelledby="crm-pipeline-heading">
      <div className={styles.pipelineToolbar}>
        <h2 id="crm-pipeline-heading">{t("board.title.pipeline")}</h2>
        <div className={styles.pipelineSummary}>
          <span>
            <strong>{t("board.total")}</strong> {formatAmount(boardTotal, boardCurrency)}
          </span>
          <span>
            <strong>{t("board.weighted")}</strong> {formatAmount(boardWeighted, boardCurrency)}
          </span>
          <span>
            <strong>{t("board.opportunitiesCount")}</strong> {pipelineOpportunities.length}
          </span>
        </div>
        <label>
          {t("board.selectPipeline")}
          <select value={pipelineId} onChange={(event) => setSelectedPipeline(event.target.value)}>
            {pipelines.map((pipeline) => (
              <option key={pipeline.id} value={pipeline.id}>{pipeline.name}</option>
            ))}
          </select>
        </label>
      </div>
      <p className={styles.srOnly} aria-live="polite">{announcement}</p>
      <div className={styles.pipelineBoard} role="list" aria-label={t("board.aria.stages")}>
        {pipelineStages.map((stage, stageIndex) => {
          const cards = pipelineOpportunities.filter((item) => item.stage_id === stage.id);
          const stageTotal = columnTotal(cards);
          const stageWeighted = columnWeighted(cards, stage);
          return (
            <section
              key={stage.id}
              className={styles.pipelineColumn}
              role="listitem"
              aria-label={t("board.aria.column").replace("{stage}", stage.name).replace("{count}", String(cards.length))}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => handleDrop(event, stage.id)}
            >
              <header>
                <div>
                  <strong>{stage.name}</strong>
                  <span className={styles.stageProbability}>
                    {formatPercent(stage.probability)}
                  </span>
                </div>
                <span>{cards.length}</span>
              </header>
              {cards.length > 0 && (
                <div className={styles.columnTotalRow}>
                  <span>{formatAmount(stageTotal, boardCurrency)}</span>
                  <span className={styles.columnWeightedLabel}>
                    {t("board.weightedShort")} {formatAmount(stageWeighted, boardCurrency)}
                  </span>
                </div>
              )}
              <div className={styles.pipelineCards}>
                {cards.map((opportunity) => {
                  const weighted = (opportunity.amount ?? 0) * stage.probability;
                  return (
                    <article
                      key={opportunity.id}
                      className={styles.opportunityCard}
                      draggable={!busy && opportunity.status === "OPEN"}
                      tabIndex={0}
                      onKeyDown={(event) => handleKeyDown(event, opportunity)}
                      onDragStart={(event) => {
                        event.dataTransfer.effectAllowed = "move";
                        event.dataTransfer.setData("application/x-snad-opportunity-id", opportunity.id);
                      }}
                      aria-label={t("board.aria.card")
                        .replace("{name}", opportunity.name)
                        .replace("{account}", accountNames.get(opportunity.account_id) ?? t("board.unknownAccount"))
                        .replace("{stage}", stage.name)}
                    >
                      <strong>{opportunity.name}</strong>
                      <span>{accountNames.get(opportunity.account_id) ?? "—"}</span>
                      <span>{formatAmount(opportunity.amount, opportunity.currency_code)}</span>
                      <span className={styles.cardProbability}>
                        {t("board.probability")} {formatPercent(opportunity.probability)}
                      </span>
                      <span className={styles.cardWeighted}>
                        {t("board.weightedShort")} {formatAmount(weighted, opportunity.currency_code)}
                      </span>
                      <div className={styles.cardMoveActions} aria-label={t("board.aria.moveActions")}>
                        <button
                          type="button"
                          disabled={busy || opportunity.status !== "OPEN" || stageIndex === 0}
                          onClick={() => moveAdjacent(opportunity, -1)}
                          aria-label={t("board.aria.movePrev").replace("{name}", opportunity.name)}
                        >
                          {t("board.prev")}
                        </button>
                        <button
                          type="button"
                          disabled={busy || opportunity.status !== "OPEN" || stageIndex === pipelineStages.length - 1}
                          onClick={() => moveAdjacent(opportunity, 1)}
                          aria-label={t("board.aria.moveNext").replace("{name}", opportunity.name)}
                        >
                          {t("board.next")}
                        </button>
                      </div>
                    </article>
                  );
                })}
                {cards.length === 0 ? (
                  <p className={styles.pipelineEmpty}>{t("board.dropHere")}</p>
                ) : null}
              </div>
            </section>
          );
        })}
      </div>
      <p className={styles.keyboardHint}>{t("board.keyboardHint")}</p>
    </section>
  );
}
