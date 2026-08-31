"use client";

import { useCallback, useEffect, useState } from "react";
import { executiveApi, type SaasPlan } from "@/lib/api/executive-api";
import { scpApi, type PlanVersion } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import styles from "../scp.module.css";

/**
 * Plans & Pricing — explicit separation of Plan → Version → Price. Creating
 * a new version never mutates existing subscribers (they stay pinned to the
 * version they contracted).
 */
export default function PlansPage() {
  const { t } = useI18n();
  const { money } = useScpFormat();
  const [plans, setPlans] = useState<SaasPlan[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  if (loading) {
    return (
      <ScpPage title={t("scp.plans.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.plans.title")} subtitle={t("scp.plans.subtitle")}>
      {error ? <ScpError message={error} /> : null}
      {plans && plans.length === 0 ? <ScpEmpty message={t("scp.state.empty")} /> : null}
      <div className={styles.cards}>
        {(plans ?? []).map((plan) => (
          <PlanCard
            key={plan.id}
            plan={plan}
            onPlans={(next) => setPlans(next)}
            onError={setError}
          />
        ))}
      </div>
      <LoadPlans onError={setError} onLoaded={setPlans} setLoading={setLoading} />
    </ScpPage>
  );
}

function LoadPlans({
  onLoaded,
  onError,
  setLoading,
}: {
  onLoaded: (plans: SaasPlan[]) => void;
  onError: (message: string) => void;
  setLoading: (loading: boolean) => void;
}) {
  const load = useCallback(async () => {
    setLoading(true);
    try {
      onLoaded(await executiveApi.plans());
    } catch (reason) {
      onError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, [onLoaded, onError, setLoading]);

  useEffect(() => {
    void load();
  }, [load]);
  return null;
}

function PlanCard({
  plan,
  onPlans,
  onError,
}: {
  plan: SaasPlan;
  onPlans: (plans: SaasPlan[]) => void;
  onError: (message: string) => void;
}) {
  const { t } = useI18n();
  const { money } = useScpFormat();
  const [versions, setVersions] = useState<PlanVersion[] | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);

  async function toggleVersions() {
    setExpanded((value) => !value);
    if (!versions) {
      try {
        setVersions(await scpApi.planVersions(plan.id));
      } catch (reason) {
        onError(reason instanceof Error ? reason.message : String(reason));
      }
    }
  }

  async function activate(version: PlanVersion) {
    setBusy(true);
    try {
      await scpApi.activatePlanVersion(plan.id, version.id);
      setVersions(await scpApi.planVersions(plan.id));
    } catch (reason) {
      onError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function createVersion(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    try {
      await scpApi.createPlanVersion(plan.id, {
        currencyCode: String(data.get("currencyCode") ?? plan.currencyCode),
        monthlyPriceMinor: Number(data.get("monthlyPriceMinor") ?? 0),
        annualPriceMinor: Number(data.get("annualPriceMinor") ?? 0),
        trialDays: Number(data.get("trialDays") ?? 0),
        maxUsers: Number(data.get("maxUsers") ?? 1),
        maxOrganizations: Number(data.get("maxOrganizations") ?? 1),
        storageMb: Number(data.get("storageMb") ?? 0),
      });
      setCreating(false);
      setVersions(await scpApi.planVersions(plan.id));
    } catch (reason) {
      onError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className={styles.appCard}>
      <h2 className={styles.appCardTitle}>{plan.name}</h2>
      <span className={styles.appCardMeta}>{plan.code}</span>
      <div className={styles.filters}>
        <ScpStatusPill value={plan.status} />
        <span className={styles.appCardMeta}>
          {t("scp.plans.monthly")}: {money(plan.monthlyPriceMinor, plan.currencyCode)}
        </span>
        <span className={styles.appCardMeta}>
          {t("scp.plans.annual")}: {money(plan.annualPriceMinor, plan.currencyCode)}
        </span>
      </div>
      <div className={styles.filters}>
        <Button variant="secondary" size="sm" onClick={() => void toggleVersions()}>
          {expanded ? t("scp.plans.hideVersions") : t("scp.plans.showVersions")}
        </Button>
        <Button variant="secondary" size="sm" onClick={() => setCreating((value) => !value)}>
          {t("scp.plans.newVersion")}
        </Button>
      </div>

      {creating ? (
        <form className={styles.panel} onSubmit={(event) => void createVersion(event)}>
          <label>
            <span>{t("scp.plans.currency")}</span>
            <Input name="currencyCode" defaultValue={plan.currencyCode} required maxLength={3} />
          </label>
          <label>
            <span>{t("scp.plans.monthlyMinor")}</span>
            <Input name="monthlyPriceMinor" type="number" defaultValue={plan.monthlyPriceMinor} min={0} required />
          </label>
          <label>
            <span>{t("scp.plans.annualMinor")}</span>
            <Input name="annualPriceMinor" type="number" defaultValue={plan.annualPriceMinor} min={0} required />
          </label>
          <label>
            <span>{t("scp.plans.trialDays")}</span>
            <Input name="trialDays" type="number" defaultValue={plan.trialDays} min={0} max={365} />
          </label>
          <Button type="submit" variant="primary" size="sm" disabled={busy}>
            {t("scp.plans.submitVersion")}
          </Button>
        </form>
      ) : null}

      {expanded && versions ? (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <caption>{t("scp.plans.versionsCaption")}</caption>
            <thead>
              <tr>
                <th scope="col">{t("scp.plans.version")}</th>
                <th scope="col">{t("scp.plans.status")}</th>
                <th scope="col">{t("scp.plans.effectiveFrom")}</th>
                <th scope="col">{t("scp.common.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {versions.map((version) => (
                <tr key={version.id}>
                  <td>v{version.versionNumber}</td>
                  <td>
                    <ScpStatusPill value={version.status} />
                  </td>
                  <td>{version.effectiveFrom ? String(version.effectiveFrom).slice(0, 10) : "—"}</td>
                  <td>
                    {version.status === "DRAFT" ? (
                      <Button variant="primary" size="sm" disabled={busy} onClick={() => void activate(version)}>
                        {t("scp.plans.activate")}
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </article>
  );
}
