"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  scpApi,
  type ChangePreview,
  type CommandResult,
  type PlanVersion,
  type SubscriptionDetail,
  type SubscriptionItem,
  type UsageSnapshot,
} from "@/lib/api/scp-api";
import { executiveApi, type SaasPlan } from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpNotice,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../../_components/ScpStates";
import { useScpFormat } from "../../_components/format";
import styles from "../../scp.module.css";

/**
 * Subscription detail — overview, items, usage, invoices, changes,
 * provisioning and audit sections, plus lifecycle commands and the
 * preview→confirm plan change flow. Status is never set directly: every
 * mutation goes through backend commands.
 */
export default function SubscriptionDetailPage() {
  const params = useParams<{ id: string }>();
  const subscriptionId = params.id;
  const { t } = useI18n();
  const { money, day, number } = useScpFormat();

  const [detail, setDetail] = useState<SubscriptionDetail | null>(null);
  const [items, setItems] = useState<SubscriptionItem[] | null>(null);
  const [usage, setUsage] = useState<UsageSnapshot[] | null>(null);
  const [plans, setPlans] = useState<SaasPlan[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");

  const [commandReason, setCommandReason] = useState("");
  const [busy, setBusy] = useState(false);

  const [changePlanId, setChangePlanId] = useState("");
  const [changePreview, setChangePreview] = useState<ChangePreview | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [detailResult, itemsResult] = await Promise.all([
        scpApi.subscriptionDetail(subscriptionId),
        scpApi.subscriptionItems(subscriptionId, true),
      ]);
      setDetail(detailResult);
      setItems(itemsResult);
      const tenantId = String(detailResult.overview.tenantId ?? "");
      if (tenantId) {
        scpApi.usage(tenantId).then(setUsage).catch(() => setUsage(null));
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, [subscriptionId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function runCommand(command: string) {
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const result: CommandResult = await scpApi.lifecycleCommand(
        subscriptionId,
        command as Parameters<typeof scpApi.lifecycleCommand>[1],
        commandReason || command,
      );
      setNotice(t("scp.detail.commandApplied", { command: result.command, from: result.fromStatus, to: result.toStatus }));
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function previewPlanChange() {
    if (!changePlanId) return;
    setBusy(true);
    setNotice("");
    try {
      if (!plans) {
        setPlans(await executiveApi.plans());
      }
      const versions: PlanVersion[] = await scpApi.planVersions(changePlanId);
      const active = versions.find((version) => version.status === "ACTIVE") ?? versions[0];
      if (!active) {
        setNotice(t("scp.detail.noVersionForPlan"));
        return;
      }
      const overview = detail?.overview ?? {};
      const country = String(overview.tenantCountry ?? overview.countryCode ?? "GLOBAL");
      setChangePreview(
        await scpApi.previewChange(subscriptionId, active.id, country),
      );
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function confirmPlanChange() {
    if (!changePreview) return;
    setBusy(true);
    setError("");
    try {
      const overview = detail?.overview ?? {};
      const country = String(overview.tenantCountry ?? overview.countryCode ?? "GLOBAL");
      await scpApi.executeChange(
        subscriptionId,
        changePreview.targetPlanVersionId,
        country,
        "Executive plan change",
      );
      setChangePreview(null);
      setNotice(t("scp.detail.changeExecuted"));
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <ScpPage title={t("scp.detail.title")}>
        <ScpSkeleton lines={10} />
      </ScpPage>
    );
  }

  const overview = detail?.overview ?? {};
  const currency = String(overview.currencyCode ?? "SAR");

  return (
    <ScpPage
      title={`${t("scp.detail.title")} — ${String(overview.tenantName ?? overview.tenantCode ?? subscriptionId)}`}
      subtitle={String(overview.planName ?? "")}
    >
      <Link href="/executive/subscriptions" className={styles.appCardMeta}>
        ← {t("scp.subscriptions.title")}
      </Link>

      {notice ? <ScpNotice>{notice}</ScpNotice> : null}
      {error ? <ScpError message={error} onRetry={load} /> : null}

      <div className={styles.metrics}>
        <div className={styles.metricCard}>
          <ScpStatusPill value={String(overview.status ?? "—")} />
          <span className={styles.metricLabel}>{t("scp.subscriptions.status")}</span>
        </div>
        <div className={styles.metricCard}>
          <span className={styles.metricValue}>{String(overview.billingCycle ?? "—")}</span>
          <span className={styles.metricLabel}>{t("scp.subscriptions.cycle")}</span>
        </div>
        <div className={styles.metricCard}>
          <span className={styles.metricValue}>{number(Number(overview.seatQuantity ?? 0))}</span>
          <span className={styles.metricLabel}>{t("scp.detail.seats")}</span>
        </div>
        <div className={styles.metricCard}>
          <span className={styles.metricValue}>{day(String(overview.currentPeriodEnd ?? ""))}</span>
          <span className={styles.metricLabel}>{t("scp.detail.periodEnd")}</span>
        </div>
      </div>

      <section className={styles.panel} aria-labelledby="scp-items-heading">
        <h2 id="scp-items-heading" className={styles.pageSubtitle}>
          {t("scp.detail.items")}
        </h2>
        {items && items.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.detail.itemType")}</th>
                  <th scope="col">{t("scp.detail.itemName")}</th>
                  <th scope="col">{t("scp.detail.quantity")}</th>
                  <th scope="col">{t("scp.detail.amount")}</th>
                  <th scope="col">{t("scp.subscriptions.status")}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.itemType}</td>
                    <td>{item.nameSnapshot ?? item.id}</td>
                    <td>{item.quantity}</td>
                    <td>{money(item.unitAmountMinor, item.currencyCode)}</td>
                    <td>
                      <ScpStatusPill value={item.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      {usage && usage.length > 0 ? (
        <section className={styles.panel} aria-labelledby="scp-usage-heading">
          <h2 id="scp-usage-heading" className={styles.pageSubtitle}>
            {t("scp.detail.usage")}
          </h2>
          <div className={styles.metrics}>
            {usage.map((snapshot) => (
              <div key={snapshot.metricCode} className={styles.metricCard}>
                <span className={styles.metricValue}>{number(snapshot.current)}</span>
                <span className={styles.metricLabel}>
                  {snapshot.metricCode}
                  {snapshot.limit !== null ? ` / ${number(snapshot.limit)}` : ""}
                </span>
                {snapshot.percent !== null ? (
                  <div
                    className={styles.usageBar}
                    role="progressbar"
                    aria-valuenow={snapshot.percent}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={snapshot.metricCode}
                  >
                    <div
                      className={styles.usageBarFill}
                      data-warning={snapshot.warning}
                      style={{ inlineSize: `${Math.min(snapshot.percent, 100)}%` }}
                    />
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <section className={styles.panel} aria-labelledby="scp-invoices-heading">
        <h2 id="scp-invoices-heading" className={styles.pageSubtitle}>
          {t("scp.detail.invoices")}
        </h2>
        {detail && detail.invoices.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.billing.number")}</th>
                  <th scope="col">{t("scp.billing.status")}</th>
                  <th scope="col">{t("scp.billing.total")}</th>
                  <th scope="col">{t("scp.billing.dueAt")}</th>
                </tr>
              </thead>
              <tbody>
                {detail.invoices.map((invoice) => (
                  <tr key={String(invoice.id)}>
                    <td>{String(invoice.invoiceNumber)}</td>
                    <td>
                      <ScpStatusPill value={String(invoice.status)} />
                    </td>
                    <td>{money(Number(invoice.totalMinor), String(invoice.currencyCode))}</td>
                    <td>{day(String(invoice.dueAt))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      <section className={styles.panel} aria-labelledby="scp-changes-heading">
        <h2 id="scp-changes-heading" className={styles.pageSubtitle}>
          {t("scp.detail.changes")}
        </h2>
        {detail && detail.changes.length > 0 ? (
          <ul>
            {detail.changes.slice(0, 10).map((change, index) => (
              <li key={index} className={styles.appCardMeta}>
                {day(String(change.createdAt ?? ""))} — {String(change.action)}{" "}
                {change.fromStatus ? `${String(change.fromStatus)} → ${String(change.toStatus)}` : ""}
              </li>
            ))}
          </ul>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      <section className={styles.panel} aria-labelledby="scp-provisioning-heading">
        <h2 id="scp-provisioning-heading" className={styles.pageSubtitle}>
          {t("scp.detail.provisioning")}
        </h2>
        {detail && detail.provisioningJobs.length > 0 ? (
          <ul>
            {detail.provisioningJobs.map((job) => (
              <li key={String(job.id)} className={styles.appCardMeta}>
                <ScpStatusPill value={String(job.status)} /> {String(job.action)} ·{" "}
                {t("scp.provisioning.attempts")}: {number(Number(job.attempts))}
              </li>
            ))}
          </ul>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      <section className={styles.panel} aria-labelledby="scp-lifecycle-heading">
        <h2 id="scp-lifecycle-heading" className={styles.pageSubtitle}>
          {t("scp.detail.lifecycleCommands")}
        </h2>
        <label className={styles.appCardMeta}>
          <span>{t("scp.detail.reason")}</span>
          <Input
            value={commandReason}
            onChange={(event) => setCommandReason(event.target.value)}
            maxLength={200}
          />
        </label>
        <div className={styles.filters}>
          {(["ACTIVATE", "RENEW", "PAUSE", "RESUME", "SUSPEND", "CANCEL", "TERMINATE"] as const).map(
            (command) => (
              <Button
                key={command}
                variant="secondary"
                size="sm"
                disabled={busy}
                onClick={() => void runCommand(command)}
              >
                {command}
              </Button>
            ),
          )}
        </div>
      </section>

      <section className={styles.panel} aria-labelledby="scp-change-heading">
        <h2 id="scp-change-heading" className={styles.pageSubtitle}>
          {t("scp.detail.changePlan")}
        </h2>
        <div className={styles.filters}>
          <select
            value={changePlanId}
            onChange={(event) => setChangePlanId(event.target.value)}
            aria-label={t("scp.detail.targetPlan")}
          >
            <option value="">{t("scp.detail.targetPlan")}</option>
            {(plans ?? []).map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name} ({plan.code})
              </option>
            ))}
          </select>
          <Button variant="secondary" size="sm" disabled={busy || !changePlanId} onClick={() => void previewPlanChange()}>
            {t("scp.detail.preview")}
          </Button>
        </div>
        {changePreview ? (
          <div>
            <p className={styles.pageSubtitle}>
              {t("scp.detail.currentTotal")}: {money(changePreview.currentMonthlyMinor, changePreview.currencyCode)}
              {" · "}
              {t("scp.detail.targetTotal")}: {money(changePreview.targetMonthlyMinor, changePreview.currencyCode)}
              {" · "}
              {t("scp.detail.delta")}: {money(changePreview.deltaMonthlyMinor, changePreview.currencyCode)}
            </p>
            {changePreview.warnings.length > 0 ? (
              <ul>
                {changePreview.warnings.map((warning, index) => (
                  <li key={index} className={styles.appCardMeta}>
                    ⚠ {warning}
                  </li>
                ))}
              </ul>
            ) : (
              <Button variant="primary" size="sm" disabled={busy} onClick={() => void confirmPlanChange()}>
                {t("scp.detail.confirmChange")}
              </Button>
            )}
          </div>
        ) : null}
      </section>

      <section className={styles.panel} aria-labelledby="scp-audit-heading">
        <h2 id="scp-audit-heading" className={styles.pageSubtitle}>
          {t("scp.detail.audit")}
        </h2>
        {detail && detail.audit.length > 0 ? (
          <ul>
            {detail.audit.map((entry, index) => (
              <li key={index} className={styles.appCardMeta}>
                {day(String(entry.createdAt ?? ""))} — {String(entry.action)} ({String(entry.resourceType)})
              </li>
            ))}
          </ul>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>
    </ScpPage>
  );
}
