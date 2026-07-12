"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { crmApi, type CrmAccount, type CrmOpportunity, type CrmPipeline, type CrmStage } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatNumber } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import { CrmPipelineBoard } from "../../crm-pipeline-board";
import { CrmVirtualTable } from "../../crm-virtual-table";
import styles from "../../crm.module.css";

/**
 * CRM Opportunities route — /crm/opportunities
 *
 * Loads `crmApi.opportunities()`, `crmApi.pipelines()`, `crmApi.stages()`,
 * and `crmApi.accounts()`. Renders:
 *   - The CrmPipelineBoard drag-and-drop kanban (reused from CRM-002)
 *   - The CrmVirtualTable for a long-scrollable list view
 *   - A create-opportunity form
 *
 * Stage moves call `crmApi.moveOpportunity()` and reload.
 */
export default function CrmOpportunitiesPage() {
  const { t } = useI18n();
  const [opportunities, setOpportunities] = useState<CrmOpportunity[]>([]);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<Record<string, CrmStage[]>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [nextOpportunities, nextAccounts, nextPipelines] = await Promise.all([
        crmApi.opportunities(),
        crmApi.accounts(),
        crmApi.pipelines(),
      ]);
      const stageEntries = await Promise.all(
        nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
      );
      setOpportunities(nextOpportunities);
      setAccounts(nextAccounts);
      setPipelines(nextPipelines);
      setStages(Object.fromEntries(stageEntries));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  const accountNames = useMemo(
    () => new Map(accounts.map((account) => [account.id, account.display_name])),
    [accounts],
  );

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
    const amount = optionalValue(form, "amount");
    await mutate(
      () =>
        crmApi.createOpportunity({
          accountId: formValue(form, "accountId"),
          pipelineId: formValue(form, "pipelineId"),
          stageId: formValue(form, "stageId"),
          name: formValue(form, "name"),
          amount: amount ? Number(amount) : undefined,
          currencyCode: formValue(form, "currency") || "SAR",
          expectedCloseDate: optionalValue(form, "expectedCloseDate"),
        }),
      t("crm.opportunities.created"),
    );
    formElement.reset();
  }

  const hasOpportunities = opportunities.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.opportunities.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.opportunities.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.salesGrid}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.opportunities.create.title")}</h2>
          <label>
            {t("crm.opportunities.create.account")}
            <select name="accountId" required defaultValue="" disabled={busy}>
              <option value="" disabled>{t("crm.opportunities.create.account")}</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.display_name}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.opportunities.create.pipeline")}
            <select name="pipelineId" required defaultValue="" disabled={busy}>
              <option value="" disabled>{t("crm.opportunities.create.pipeline")}</option>
              {pipelines.map((pipeline) => (
                <option key={pipeline.id} value={pipeline.id}>{pipeline.name}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.opportunities.create.stage")}
            <select name="stageId" required defaultValue="" disabled={busy}>
              <option value="" disabled>{t("crm.opportunities.create.stage")}</option>
              {pipelines.flatMap((pipeline) =>
                (stages[pipeline.id] ?? []).map((stage) => (
                  <option key={stage.id} value={stage.id}>{pipeline.name} — {stage.name}</option>
                )),
              )}
            </select>
          </label>
          <label>
            {t("crm.opportunities.create.name")}
            <input name="name" required disabled={busy} />
          </label>
          <label>
            {t("crm.opportunities.create.amount")}
            <input name="amount" type="number" min="0" step="0.01" disabled={busy} />
          </label>
          <label>
            {t("crm.opportunities.create.currency")}
            <input name="currency" defaultValue="SAR" maxLength={3} disabled={busy} />
          </label>
          <label>
            {t("crm.opportunities.create.expectedClose")}
            <input name="expectedCloseDate" type="date" disabled={busy} />
          </label>
          <button type="submit" disabled={busy}>{t("crm.opportunities.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.opportunities.table")}</h2>
          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasOpportunities ? (
            <CrmEmpty
              title={t("crm.opportunities.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <CrmVirtualTable<CrmOpportunity>
              rows={opportunities}
              label={t("crm.opportunities.table")}
              columns={[
                { key: "name", header: t("crm.opportunities.list.name"), render: (item) => item.name },
                { key: "account", header: t("crm.opportunities.list.account"), render: (item) => accountNames.get(item.account_id) ?? "—" },
                { key: "amount", header: t("crm.opportunities.list.amount"), render: (item) => `${formatNumber(item.amount)} ${item.currency_code}` },
                { key: "status", header: t("crm.opportunities.list.status"), render: (item) => item.status },
              ]}
            />
          )}
        </div>

        <div className={styles.pipelineBoardSection}>
          <h2 className={styles.sectionHeading}>{t("crm.opportunities.board")}</h2>
          {loading ? (
            <CrmLoading rows={2} />
          ) : pipelines.length === 0 ? (
            <CrmEmpty
              title={t("crm.pipelines.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <CrmPipelineBoard
              pipelines={pipelines}
              stages={stages}
              opportunities={opportunities}
              accountNames={accountNames}
              busy={busy}
              onMove={async (opportunityId, stageId) => {
                await mutate(() => crmApi.moveOpportunity(opportunityId, stageId), t("crm.opportunities.moved"));
              }}
            />
          )}
        </div>
      </section>
    </div>
  );
}
