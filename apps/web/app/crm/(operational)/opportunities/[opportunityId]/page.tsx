"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  crmApi,
  type CrmAccount,
  type CrmActivity,
  type CrmCustomFieldValues,
  type CrmOpportunity,
  type CrmPipeline,
  type CrmStage,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formatDate, formatNumber } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import { CrmError } from "../../../components/crm-error";
import { CrmEmpty } from "../../../components/crm-empty";
import styles from "../../../crm.module.css";

/**
 * CRM Opportunity detail route — /crm/opportunities/[opportunityId]
 *
 * Loads:
 *   - crmApi.opportunity(opportunityId)                          → summary
 *   - crmApi.accounts()                                          → account name
 *   - crmApi.pipelines() + crmApi.stages(pipelineId)            → stage picker
 *   - crmApi.customFieldValues("OPPORTUNITY", opportunityId)     → values
 *   - crmApi.activities("OPPORTUNITY", opportunityId)            → related
 *
 * Renders:
 *   - Summary card (name, account, pipeline, stage, amount, currency,
 *     probability, expected close date, status)
 *   - Stage movement dropdown (calls crmApi.moveOpportunity)
 *   - Custom field values section
 *   - Related activities table
 *
 * Loading / error / not-found states are handled explicitly per the task
 * contract.
 */
export default function OpportunityDetailPage() {
  const { t } = useI18n();
  const params = useParams<{ opportunityId: string }>();
  const opportunityId = params?.opportunityId ?? "";

  const [opportunity, setOpportunity] = useState<CrmOpportunity | null>(null);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stagesByPipeline, setStagesByPipeline] = useState<Record<string, CrmStage[]>>({});
  const [customValues, setCustomValues] = useState<CrmCustomFieldValues | null>(null);
  const [activities, setActivities] = useState<CrmActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedStageId, setSelectedStageId] = useState("");

  const reload = useCallback(async () => {
    if (!opportunityId) return;
    setLoading(true);
    setError("");
    try {
      const [nextOpportunity, nextAccounts, nextPipelines, nextCustom, nextActivities] = await Promise.all([
        crmApi.opportunity(opportunityId),
        crmApi.accounts(),
        crmApi.pipelines(),
        crmApi.customFieldValues("OPPORTUNITY", opportunityId).catch(() => null),
        crmApi.activities("OPPORTUNITY", opportunityId).catch(() => [] as CrmActivity[]),
      ]);
      setOpportunity(nextOpportunity);
      setAccounts(nextAccounts);
      setPipelines(nextPipelines);
      setCustomValues(nextCustom);
      setActivities(nextActivities);
      setSelectedStageId(nextOpportunity.stage_id);
      const stageEntries = await Promise.all(
        nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
      );
      setStagesByPipeline(Object.fromEntries(stageEntries));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setOpportunity(null);
      setCustomValues(null);
      setActivities([]);
    } finally {
      setLoading(false);
    }
  }, [opportunityId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  const accountName = useMemo(() => {
    if (!opportunity) return null;
    return accounts.find((account) => account.id === opportunity.account_id)?.display_name ?? opportunity.account_id;
  }, [accounts, opportunity]);

  const pipeline = useMemo(() => {
    if (!opportunity) return null;
    return pipelines.find((p) => p.id === opportunity.pipeline_id) ?? null;
  }, [pipelines, opportunity]);

  const stages = useMemo(() => {
    if (!opportunity) return [] as CrmStage[];
    return stagesByPipeline[opportunity.pipeline_id] ?? [];
  }, [opportunity, stagesByPipeline]);

  const currentStage = useMemo(() => {
    if (!opportunity) return null;
    return stages.find((stage) => stage.id === opportunity.stage_id) ?? null;
  }, [opportunity, stages]);

  async function handleMoveStage() {
    if (!opportunity || !selectedStageId || selectedStageId === opportunity.stage_id) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await crmApi.moveOpportunity(opportunity.id, selectedStageId);
      setNotice(t("crm.opportunityDetail.moved"));
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <div className={styles.contentInner}>
        <CrmLoading />
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.contentInner}>
        <CrmError message={error} onRetry={() => void reload()} />
        <Link href="/crm/opportunities">{t("crm.opportunityDetail.back")}</Link>
      </div>
    );
  }

  if (!opportunity) {
    return (
      <div className={styles.contentInner}>
        <CrmEmpty
          title={t("crm.opportunityDetail.notFound")}
          hint={t("crm.state.emptyHint")}
        />
        <Link href="/crm/opportunities">{t("crm.opportunityDetail.back")}</Link>
      </div>
    );
  }

  return (
    <div className={styles.contentInner}>
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{t("crm.opportunityDetail.title")}</h1>
          <p className={styles.pageDescription}>{opportunity.name}</p>
        </div>
        <Link href="/crm/opportunities">{t("crm.opportunityDetail.back")}</Link>
      </div>

      {notice ? <div className={styles.success} role="status">{notice}</div> : null}
      {error ? <div className={styles.error} role="alert">{error}</div> : null}

      <section className={styles.overviewSection} aria-label={t("crm.opportunityDetail.summary")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.opportunityDetail.summary")}</h2>
        <div className={styles.metrics}>
          <article>
            <span>{t("crm.opportunityDetail.name")}</span>
            <strong>{opportunity.name}</strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.account")}</span>
            <strong>
              <Link href={`/crm/accounts/${opportunity.account_id}`}>{accountName ?? opportunity.account_id}</Link>
            </strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.pipeline")}</span>
            <strong>{pipeline?.name ?? opportunity.pipeline_id}</strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.stage")}</span>
            <strong>{currentStage?.name ?? opportunity.stage_id}</strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.amount")}</span>
            <strong>{formatNumber(opportunity.amount)} {opportunity.currency_code}</strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.probability")}</span>
            <strong>{opportunity.probability}%</strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.expectedClose")}</span>
            <strong>{formatDate(opportunity.expected_close_date)}</strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.status")}</span>
            <strong>
              <span className={styles.badge}>{opportunity.status}</span>
            </strong>
          </article>
          <article>
            <span>{t("crm.opportunityDetail.updated")}</span>
            <strong>{formatDate(opportunity.updated_at)}</strong>
          </article>
        </div>
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.opportunityDetail.moveStage")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.opportunityDetail.moveStage")}</h2>
        <p className={styles.notice}>{t("crm.opportunityDetail.moveStageHint")}</p>
        <div className={styles.rowActions}>
          <select
            value={selectedStageId}
            onChange={(e) => setSelectedStageId(e.target.value)}
            disabled={busy || stages.length === 0}
            aria-label={t("crm.opportunityDetail.moveStage")}
          >
            {stages.length === 0 ? (
              <option value={opportunity.stage_id}>{currentStage?.name ?? opportunity.stage_id}</option>
            ) : (
              stages.map((stage) => (
                <option key={stage.id} value={stage.id}>{stage.name}</option>
              ))
            )}
          </select>
          <button
            type="button"
            disabled={busy || !selectedStageId || selectedStageId === opportunity.stage_id}
            onClick={() => void handleMoveStage()}
          >
            {t("crm.opportunityDetail.moveStage")}
          </button>
        </div>
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.common.customFields")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.common.customFields")}</h2>
        {!customValues || customValues.values.length === 0 ? (
          <p className={styles.notice}>{t("crm.common.customFieldsEmpty")}</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.customFields.list.fieldKey")}</th>
                  <th>{t("crm.customFields.list.dataType")}</th>
                  <th>{t("crm.common.customFields")}</th>
                </tr>
              </thead>
              <tbody>
                {customValues.values.map((entry) => (
                  <tr key={entry.fieldKey}>
                    <td><code>{entry.fieldKey}</code></td>
                    <td>{entry.sensitive ? t("crm.customFields.list.sensitive") : "—"}</td>
                    <td>
                      {entry.sensitive
                        ? <span className={styles.redacted}>{t("crm.customFields.redacted")}</span>
                        : (entry.displayValue ?? (entry.value == null || entry.value === "" ? "—" : String(entry.value)))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.common.activities")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.common.activities")}</h2>
        {activities.length === 0 ? (
          <p className={styles.notice}>{t("crm.common.activitiesEmpty")}</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.activities.list.subject")}</th>
                  <th>{t("crm.activities.list.type")}</th>
                  <th>{t("crm.activities.list.status")}</th>
                  <th>{t("crm.activities.list.dueAt")}</th>
                </tr>
              </thead>
              <tbody>
                {activities.map((activity) => (
                  <tr key={activity.id}>
                    <td>{activity.subject}</td>
                    <td>{activity.activity_type}</td>
                    <td>
                      <span className={styles.badge}>{activity.status}</span>
                    </td>
                    <td>{formatDate(activity.due_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
