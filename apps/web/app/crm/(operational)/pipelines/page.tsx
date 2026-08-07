"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { crmApi, type CrmPipeline, type CrmStage } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useAuth } from "@/lib/auth/auth-provider";
import { hasCapability } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/**
 * CRM Pipelines route — /crm/pipelines
 *
 * Loads `crmApi.pipelines()` and the stages for each pipeline, then renders:
 *   - A create-pipeline form (with comma-separated stages input) — visible only to users with CRM.ADMIN
 *   - The list of pipelines with their stages inline
 *
 * Backend enforces CRM.ADMIN for create-pipeline; the UI hides the form when
 * the user does not have that capability. (Capability check is best-effort;
 * backend remains authoritative.)
 */
export default function CrmPipelinesPage() {
  const { t } = useI18n();
  const { me } = useAuth();
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<Record<string, CrmStage[]>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Best-effort client-side check; backend is always authoritative
  const canCreatePipeline = useMemo(() => hasCapability(me, "CRM.ADMIN"), [me]);

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const nextPipelines = await crmApi.pipelines();
      const stageEntries = await Promise.all(
        nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
      );
      setPipelines(nextPipelines);
      setStages(Object.fromEntries(stageEntries));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setPipelines([]);
      setStages({});
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () =>
        crmApi.createPipeline({
          name: formValue(form, "name"),
          currencyCode: formValue(form, "currency") || "SAR",
          stages: formValue(form, "stages").split(",").map((item) => item.trim()).filter(Boolean),
        }),
      t("crm.pipelines.created"),
    );
    formElement.reset();
  }

  const hasPipelines = pipelines.length > 0;

  // Stage CRUD state
  const [stageErrors, setStageErrors] = useState<Record<string, string>>({});
  const [editingStageId, setEditingStageId] = useState<string | null>(null);
  const [editStageName, setEditStageName] = useState("");
  const [editStageProb, setEditStageProb] = useState("");
  const [newStageName, setNewStageName] = useState<Record<string, string>>({});

  async function handleCreateStage(pipelineId: string) {
    const name = (newStageName[pipelineId] ?? "").trim();
    if (!name) return;
    setBusy(true);
    setStageErrors((prev) => ({ ...prev, [pipelineId]: "" }));
    try {
      await crmApi.createStage(pipelineId, { name });
      setNewStageName((prev) => ({ ...prev, [pipelineId]: "" }));
      await reload();
    } catch (reason) {
      setStageErrors((prev) => ({ ...prev, [pipelineId]: toUserFacingError(reason).message }));
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdateStage(pipelineId: string, stageId: string) {
    setBusy(true);
    setStageErrors((prev) => ({ ...prev, [pipelineId]: "" }));
    try {
      const body: Record<string, unknown> = {};
      if (editStageName.trim()) body.name = editStageName.trim();
      if (editStageProb !== "") body.probability = Number(editStageProb);
      await crmApi.updateStage(pipelineId, stageId, body);
      setEditingStageId(null);
      await reload();
    } catch (reason) {
      setStageErrors((prev) => ({ ...prev, [pipelineId]: toUserFacingError(reason).message }));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteStage(pipelineId: string, stageId: string) {
    if (!window.confirm(t("crm.pipelines.stages.confirmDelete"))) return;
    setBusy(true);
    setStageErrors((prev) => ({ ...prev, [pipelineId]: "" }));
    try {
      await crmApi.deleteStage(pipelineId, stageId);
      await reload();
    } catch (reason) {
      setStageErrors((prev) => ({ ...prev, [pipelineId]: toUserFacingError(reason).message }));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.pipelines.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.pipelines.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        {canCreatePipeline && (
          <form className={styles.formCard} onSubmit={handleCreate}>
            <h2 className={styles.sectionHeading}>{t("crm.pipelines.create.title")}</h2>
            <label>
              {t("crm.pipelines.create.name")}
              <input name="name" required disabled={busy} />
            </label>
            <label>
              {t("crm.pipelines.create.currency")}
              <input name="currency" defaultValue="SAR" maxLength={3} disabled={busy} />
            </label>
            <label>
              {t("crm.pipelines.create.stages")}
              <input name="stages" defaultValue="New, Qualified, Proposal, Won, Lost" disabled={busy} />
              <small>{t("crm.pipelines.create.stagesHint")}</small>
            </label>
            <button type="submit" disabled={busy}>{t("crm.pipelines.create.submit")}</button>
          </form>
        )}

        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.pipelines.list.title")}</h2>
          {loading ? (
            <CrmLoading rows={3} />
          ) : !hasPipelines ? (
            <CrmEmpty
              title={t("crm.pipelines.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.pipelines.list.name")}</th>
                    <th>{t("crm.pipelines.list.currency")}</th>
                    <th>{t("crm.pipelines.list.active")}</th>
                    <th>{t("crm.pipelines.list.stages")}</th>
                  </tr>
                </thead>
                <tbody>
                  {pipelines.map((pipeline) => {
                    const pipelineStages = (stages[pipeline.id] ?? []).slice().sort((a, b) => a.sequence - b.sequence);
                    return (
                      <tr key={pipeline.id}>
                        <td>{pipeline.name}</td>
                        <td>{pipeline.currency_code ?? "—"}</td>
                        <td>
                          {pipeline.active ? (
                            <span className={`${styles.badge} ${styles.badgeSuccess}`}>{t("crm.common.yes")}</span>
                          ) : (
                            <span className={styles.badge}>{t("crm.common.no")}</span>
                          )}
                        </td>
                        <td>
                          {pipelineStages.length === 0 ? "—" : (
                            <ol style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexWrap: "wrap", gap: 6 }}>
                              {pipelineStages.map((stage, idx) => (
                                <li key={stage.id} style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                                  {editingStageId === stage.id ? (
                                    <>
                                      <input
                                        value={editStageName}
                                        onChange={(e) => setEditStageName(e.target.value)}
                                        style={{ width: 100 }}
                                        disabled={busy}
                                      />
                                      <input
                                        type="number"
                                        min={0}
                                        max={100}
                                        value={editStageProb}
                                        onChange={(e) => setEditStageProb(e.target.value)}
                                        style={{ width: 50 }}
                                        disabled={busy}
                                      />
                                      <button
                                        type="button"
                                        onClick={() => void handleUpdateStage(pipeline.id, stage.id)}
                                        disabled={busy}
                                      >
                                        {t("crm.common.save")}
                                      </button>
                                      <button type="button" onClick={() => setEditingStageId(null)} disabled={busy}>
                                        {t("crm.common.cancel")}
                                      </button>
                                    </>
                                  ) : (
                                    <>
                                      <span className={styles.badge}>
                                        {idx + 1}. {stage.name} ({Math.round(stage.probability)}%)
                                        {stage.terminal_state ? ` [${stage.terminal_state}]` : ""}
                                      </span>
                                      {canCreatePipeline && (
                                        <>
                                          <button
                                            type="button"
                                            onClick={() => {
                                              setEditingStageId(stage.id);
                                              setEditStageName(stage.name);
                                              setEditStageProb(String(stage.probability));
                                            }}
                                            disabled={busy}
                                          >
                                            {t("crm.common.edit")}
                                          </button>
                                          <button
                                            type="button"
                                            onClick={() => void handleDeleteStage(pipeline.id, stage.id)}
                                            disabled={busy}
                                          >
                                            {t("crm.common.delete")}
                                          </button>
                                        </>
                                      )}
                                    </>
                                  )}
                                </li>
                              ))}
                            </ol>
                          )}
                          {canCreatePipeline && (
                            <div style={{ marginTop: 6, display: "flex", alignItems: "center", gap: 4 }}>
                              <input
                                placeholder={t("crm.pipelines.stages.newPlaceholder")}
                                value={newStageName[pipeline.id] ?? ""}
                                onChange={(e) => setNewStageName((prev) => ({ ...prev, [pipeline.id]: e.target.value }))}
                                disabled={busy}
                                style={{ width: 140 }}
                              />
                              <button
                                type="button"
                                onClick={() => void handleCreateStage(pipeline.id)}
                                disabled={busy || !(newStageName[pipeline.id] ?? "").trim()}
                              >
                                {t("crm.pipelines.stages.add")}
                              </button>
                            </div>
                          )}
                          {stageErrors[pipeline.id] && (
                            <div className={styles.error} role="alert" style={{ marginTop: 4 }}>
                              {stageErrors[pipeline.id]}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
