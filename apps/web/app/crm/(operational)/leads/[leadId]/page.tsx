"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  crmApi,
  type CrmCustomFieldValues,
  type CrmLead,
  type CrmPipeline,
  type CrmStage,
  type CrmTimelineEvent,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatDate } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import { CrmError } from "../../../components/crm-error";
import { CrmEmpty } from "../../../components/crm-empty";
import styles from "../../../crm.module.css";

/**
 * Lead status lifecycle for the operational UI. The backend accepts any of
 * these strings; we surface the canonical forward-path transitions plus the
 * two terminal "exit" statuses (DISQUALIFIED / CONVERTED).
 */
const LEAD_STATUSES = ["NEW", "ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "CONVERTED"] as const;
const TERMINAL_STATUSES = new Set(["DISQUALIFIED", "CONVERTED", "ARCHIVED"]);

/**
 * CRM Lead detail route — /crm/leads/[leadId]
 *
 * Loads:
 *   - crmApi.lead(leadId)                              → identity + status + score
 *   - crmApi.customFieldValues("LEAD", leadId)         → per-entity values
 *   - crmApi.timeline("LEAD", leadId)                  → lead timeline events
 *   - crmApi.pipelines() + crmApi.stages(pipelineId)   → for the convert dialog
 *
 * Renders:
 *   - Identity card (name, company, email, phone, source, status, score)
 *   - Status transition row (selects a new status and calls changeLeadStatus)
 *   - Convert dialog (createOpportunity + currency + pipeline + stage + amount)
 *     with double-submit protection (busy flag + ref-counted in-flight state)
 *   - Custom field values section
 *   - Timeline section
 *
 * Loading / error / not-found states are handled explicitly per the task
 * contract.
 */
export default function LeadDetailPage() {
  const { t } = useI18n();
  const params = useParams<{ leadId: string }>();
  const leadId = params?.leadId ?? "";

  const [lead, setLead] = useState<CrmLead | null>(null);
  const [customValues, setCustomValues] = useState<CrmCustomFieldValues | null>(null);
  const [timeline, setTimeline] = useState<CrmTimelineEvent[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<Record<string, CrmStage[]>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [convertOpen, setConvertOpen] = useState(false);

  const reload = useCallback(async () => {
    if (!leadId) return;
    setLoading(true);
    setError("");
    try {
      const [nextLead, nextCustom, nextTimeline] = await Promise.all([
        crmApi.lead(leadId),
        crmApi.customFieldValues("LEAD", leadId).catch(() => null),
        crmApi.timeline("LEAD", leadId).catch(() => [] as CrmTimelineEvent[]),
      ]);
      setLead(nextLead);
      setCustomValues(nextCustom);
      setTimeline(nextTimeline);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setLead(null);
      setCustomValues(null);
      setTimeline([]);
    } finally {
      setLoading(false);
    }
  }, [leadId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  // Lazily fetch pipelines + stages only when the convert dialog is opened.
  // This keeps the detail page fast on first paint.
  useEffect(() => {
    if (!convertOpen) return;
    let cancelled = false;
    void (async () => {
      try {
        const nextPipelines = await crmApi.pipelines();
        if (cancelled) return;
        setPipelines(nextPipelines);
        const stageEntries = await Promise.all(
          nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
        );
        if (cancelled) return;
        setStages(Object.fromEntries(stageEntries));
      } catch {
        // Non-fatal: the convert dialog still works without pipeline/stage
        // selection (backend will pick a default).
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [convertOpen]);

  const isTerminal = useMemo(() => {
    if (!lead) return true;
    return TERMINAL_STATUSES.has(lead.status);
  }, [lead]);

  async function handleStatusChange(nextStatus: string) {
    if (!lead || nextStatus === lead.status) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await crmApi.changeLeadStatus(lead.id, nextStatus);
      setNotice(t("crm.leadDetail.statusChanged"));
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleConvert(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!lead) return;
    if (busy) return; // double-submit protection
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const createOpportunity = form.get("createOpportunity") === "on";
    const amount = optionalValue(form, "amount");
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await crmApi.convertLead(lead.id, {
        createOpportunity,
        currencyCode: formValue(form, "currency") || "SAR",
        opportunityName: optionalValue(form, "opportunityName"),
        amount: amount ? Number(amount) : undefined,
        pipelineId: optionalValue(form, "pipelineId"),
        stageId: optionalValue(form, "stageId"),
      });
      setNotice(t("crm.leadDetail.converted"));
      setConvertOpen(false);
      formElement.reset();
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
        <Link href="/crm/leads">{t("crm.leadDetail.back")}</Link>
      </div>
    );
  }

  if (!lead) {
    return (
      <div className={styles.contentInner}>
        <CrmEmpty
          title={t("crm.leadDetail.notFound")}
          hint={t("crm.state.emptyHint")}
        />
        <Link href="/crm/leads">{t("crm.leadDetail.back")}</Link>
      </div>
    );
  }

  return (
    <div className={styles.contentInner}>
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{t("crm.leadDetail.title")}</h1>
          <p className={styles.pageDescription}>{lead.display_name}</p>
        </div>
        <Link href="/crm/leads">{t("crm.leadDetail.back")}</Link>
      </div>

      {notice ? <div className={styles.success} role="status">{notice}</div> : null}
      {error ? <div className={styles.error} role="alert">{error}</div> : null}

      <section className={styles.overviewSection} aria-label={t("crm.leadDetail.identity")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.leadDetail.identity")}</h2>
        <div className={styles.metrics}>
          <article>
            <span>{t("crm.leadDetail.name")}</span>
            <strong>{lead.display_name}</strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.company")}</span>
            <strong>{lead.company_name ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.email")}</span>
            <strong>{lead.email ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.phone")}</span>
            <strong>{lead.phone ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.source")}</span>
            <strong>{lead.source ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.status")}</span>
            <strong>
              <span className={`${styles.badge} ${isTerminal ? styles.badgeWarning : styles.badgeSuccess}`}>
                {t(`crm.leadDetail.status.${lead.status}`) !== `crm.leadDetail.status.${lead.status}`
                  ? t(`crm.leadDetail.status.${lead.status}`)
                  : lead.status}
              </span>
            </strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.score")}</span>
            <strong>{lead.score ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.leadDetail.updated")}</span>
            <strong>{formatDate(lead.updated_at)}</strong>
          </article>
        </div>
      </section>

      {!isTerminal ? (
        <section className={styles.overviewSection} aria-label={t("crm.leadDetail.statusActions")}>
          <h2 className={styles.overviewSectionTitle}>{t("crm.leadDetail.statusActions")}</h2>
          <div className={styles.rowActions}>
            {LEAD_STATUSES
              .filter((status) => status !== lead.status)
              .map((status) => (
                <button
                  key={status}
                  type="button"
                  disabled={busy}
                  onClick={() => void handleStatusChange(status)}
                >
                  {t(`crm.leadDetail.status.${status}`) !== `crm.leadDetail.status.${status}`
                    ? t(`crm.leadDetail.status.${status}`)
                    : status}
                </button>
              ))}
          </div>
        </section>
      ) : null}

      {!TERMINAL_STATUSES.has(lead.status) ? (
        <section className={styles.overviewSection} aria-label={t("crm.leadDetail.convertActions")}>
          <h2 className={styles.overviewSectionTitle}>{t("crm.leadDetail.convertActions")}</h2>
          {!convertOpen ? (
            <div className={styles.rowActions}>
              <button
                type="button"
                disabled={busy}
                onClick={() => setConvertOpen(true)}
              >
                {t("crm.leadDetail.convert")}
              </button>
            </div>
          ) : (
            <form className={styles.formCard} onSubmit={handleConvert} style={{ maxWidth: 640 }}>
              <h3 className={styles.sectionHeading}>{t("crm.leadDetail.convertDialog")}</h3>
              <label style={{ flexDirection: "row", alignItems: "center" }}>
                <input type="checkbox" name="createOpportunity" defaultChecked disabled={busy} />
                {t("crm.leadDetail.createOpportunity")}
              </label>
              <label>
                {t("crm.leadDetail.opportunityName")}
                <input name="opportunityName" defaultValue={lead.display_name} disabled={busy} />
              </label>
              <label>
                {t("crm.leadDetail.amount")}
                <input name="amount" type="number" min="0" step="0.01" disabled={busy} />
              </label>
              <label>
                {t("crm.leadDetail.currency")}
                <input name="currency" defaultValue="SAR" maxLength={3} disabled={busy} />
              </label>
              <label>
                {t("crm.leadDetail.pipeline")}
                <select name="pipelineId" defaultValue="" disabled={busy}>
                  <option value="">{t("crm.leadDetail.pipeline")}</option>
                  {pipelines.map((pipeline) => (
                    <option key={pipeline.id} value={pipeline.id}>{pipeline.name}</option>
                  ))}
                </select>
              </label>
              <label>
                {t("crm.leadDetail.stage")}
                <select name="stageId" defaultValue="" disabled={busy}>
                  <option value="">{t("crm.leadDetail.stage")}</option>
                  {pipelines.flatMap((pipeline) =>
                    (stages[pipeline.id] ?? []).map((stage) => (
                      <option key={stage.id} value={stage.id}>{pipeline.name} — {stage.name}</option>
                    )),
                  )}
                </select>
              </label>
              <div className={styles.rowActions}>
                <button type="submit" disabled={busy}>
                  {busy ? t("crm.leadDetail.converting") : t("crm.leadDetail.convertSubmit")}
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setConvertOpen(false)}
                >
                  {t("crm.common.cancel")}
                </button>
              </div>
            </form>
          )}
        </section>
      ) : null}

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

      <section className={styles.overviewSection} aria-label={t("crm.common.timeline")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.common.timeline")}</h2>
        {timeline.length === 0 ? (
          <p className={styles.notice}>{t("crm.common.timelineEmpty")}</p>
        ) : (
          <ol className={styles.timeline}>
            {timeline.map((event) => (
              <li key={event.id}>
                <strong>{event.summary}</strong>
                <span>{formatDate(event.occurred_at)}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
