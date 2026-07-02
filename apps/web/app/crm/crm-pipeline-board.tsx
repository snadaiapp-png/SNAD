"use client";

import { type DragEvent, type KeyboardEvent, useMemo, useState } from "react";
import { type CrmOpportunity, type CrmPipeline, type CrmStage } from "@/lib/api/crm";
import styles from "./crm.module.css";

interface PipelineBoardProps {
  pipelines: CrmPipeline[];
  stages: Record<string, CrmStage[]>;
  opportunities: CrmOpportunity[];
  accountNames: Map<string, string>;
  busy: boolean;
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

export function CrmPipelineBoard({
  pipelines,
  stages,
  opportunities,
  accountNames,
  busy,
  onMove,
}: PipelineBoardProps) {
  const [selectedPipeline, setSelectedPipeline] = useState(pipelines[0]?.id ?? "");
  const [announcement, setAnnouncement] = useState("");
  const pipelineId = pipelines.some((pipeline) => pipeline.id === selectedPipeline)
    ? selectedPipeline
    : pipelines[0]?.id ?? "";
  const pipelineStages = useMemo(
    () => [...(stages[pipelineId] ?? [])].sort((left, right) => left.sequence - right.sequence),
    [pipelineId, stages],
  );
  const pipelineOpportunities = opportunities.filter(
    (opportunity) => opportunity.pipeline_id === pipelineId,
  );

  async function move(opportunity: CrmOpportunity, stageId: string) {
    if (busy || opportunity.status !== "OPEN" || opportunity.stage_id === stageId) return;
    const stage = pipelineStages.find((item) => item.id === stageId);
    await onMove(opportunity.id, stageId);
    setAnnouncement(`تم نقل ${opportunity.name} إلى ${stage?.name ?? "المرحلة الجديدة"}.`);
  }

  function moveAdjacent(opportunity: CrmOpportunity, direction: -1 | 1) {
    const target = adjacentStageId(pipelineStages, opportunity.stage_id, direction);
    if (target) void move(opportunity, target);
  }

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
    return <p className={styles.emptyState}>أنشئ قناة مبيعات لعرض لوحة الفرص.</p>;
  }

  return (
    <section className={styles.pipelineBoardSection} aria-labelledby="crm-pipeline-heading">
      <div className={styles.pipelineToolbar}>
        <h2 id="crm-pipeline-heading">لوحة الفرص</h2>
        <label>
          قناة المبيعات
          <select value={pipelineId} onChange={(event) => setSelectedPipeline(event.target.value)}>
            {pipelines.map((pipeline) => (
              <option key={pipeline.id} value={pipeline.id}>{pipeline.name}</option>
            ))}
          </select>
        </label>
      </div>
      <p className={styles.srOnly} aria-live="polite">{announcement}</p>
      <div className={styles.pipelineBoard} role="list" aria-label="مراحل قناة المبيعات">
        {pipelineStages.map((stage, stageIndex) => {
          const cards = pipelineOpportunities.filter((item) => item.stage_id === stage.id);
          return (
            <section
              key={stage.id}
              className={styles.pipelineColumn}
              role="listitem"
              aria-label={`${stage.name}: ${cards.length} فرص`}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => handleDrop(event, stage.id)}
            >
              <header>
                <strong>{stage.name}</strong>
                <span>{cards.length}</span>
              </header>
              <div className={styles.pipelineCards}>
                {cards.map((opportunity) => (
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
                    aria-label={`${opportunity.name}. الحساب ${accountNames.get(opportunity.account_id) ?? "غير معروف"}. المرحلة ${stage.name}`}
                  >
                    <strong>{opportunity.name}</strong>
                    <span>{accountNames.get(opportunity.account_id) ?? "—"}</span>
                    <span>{opportunity.amount ?? 0} {opportunity.currency_code}</span>
                    <div className={styles.cardMoveActions} aria-label="نقل الفرصة بين المراحل">
                      <button
                        type="button"
                        disabled={busy || opportunity.status !== "OPEN" || stageIndex === 0}
                        onClick={() => moveAdjacent(opportunity, -1)}
                        aria-label={`نقل ${opportunity.name} إلى المرحلة السابقة`}
                      >السابق</button>
                      <button
                        type="button"
                        disabled={busy || opportunity.status !== "OPEN" || stageIndex === pipelineStages.length - 1}
                        onClick={() => moveAdjacent(opportunity, 1)}
                        aria-label={`نقل ${opportunity.name} إلى المرحلة التالية`}
                      >التالي</button>
                    </div>
                  </article>
                ))}
                {cards.length === 0 ? <p className={styles.pipelineEmpty}>اسحب فرصة إلى هنا</p> : null}
              </div>
            </section>
          );
        })}
      </div>
      <p className={styles.keyboardHint}>اختصار لوحة المفاتيح: Alt مع السهم الأيمن أو الأيسر لنقل الفرصة.</p>
    </section>
  );
}
