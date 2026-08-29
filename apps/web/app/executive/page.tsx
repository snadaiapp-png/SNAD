"use client";

import { useCallback, useEffect, useState } from "react";
import { scpApi, type ScpOverview } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
} from "./_components/ScpStates";
import { useScpFormat } from "./_components/format";
import styles from "./scp.module.css";

/**
 * Control-plane overview — every metric comes from the server-side read
 * model. Metrics that are not computable (churn, expansion) render as N/A;
 * the UI never derives business metrics from client-side arrays.
 */
export default function ExecutiveOverviewPage() {
  const { t } = useI18n();
  const { number, money } = useScpFormat();
  const [overview, setOverview] = useState<ScpOverview | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setOverview(await scpApi.overview());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <ScpPage title={t("scp.overview.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.overview.title")} subtitle={t("scp.overview.subtitle")}>
      {error ? (
        <ScpError message={error} onRetry={load} />
      ) : overview ? (
        <>
          <div className={styles.metrics}>
            <MetricCard label={t("scp.overview.totalTenants")} value={number(overview.totalTenants)} />
            <MetricCard label={t("scp.overview.activeSubscriptions")} value={number(overview.activeSubscriptions)} />
            <MetricCard label={t("scp.overview.trials")} value={number(overview.trials)} />
            <MetricCard label={t("scp.overview.pastDue")} value={number(overview.pastDue)} />
            <MetricCard label={t("scp.overview.renewals")} value={number(overview.renewalsNext30Days)} />
          </div>

          <div className={styles.panel}>
            <h2 className={styles.pageSubtitle}>{t("scp.overview.mrrByCurrency")}</h2>
            {Object.keys(overview.mrrMinorByCurrency).length === 0 ? (
              <ScpEmpty message={t("scp.state.empty")} />
            ) : (
              <div className={styles.metrics}>
                {Object.entries(overview.mrrMinorByCurrency).map(([currency, minor]) => (
                  <MetricCard
                    key={`mrr-${currency}`}
                    label={`${t("scp.overview.mrr")} · ${currency}`}
                    value={money(minor, currency)}
                  />
                ))}
                {Object.entries(overview.arrMinorByCurrency).map(([currency, minor]) => (
                  <MetricCard
                    key={`arr-${currency}`}
                    label={`${t("scp.overview.arr")} · ${currency}`}
                    value={money(minor, currency)}
                  />
                ))}
              </div>
            )}
          </div>

          <div className={styles.panel}>
            <h2 className={styles.pageSubtitle}>{t("scp.overview.notYetAvailable")}</h2>
            <p className={styles.pageSubtitle}>{t("scp.overview.naExplanation")}</p>
            <div className={styles.metrics}>
              <MetricCard label={t("scp.overview.churn")} value="N/A" />
              <MetricCard label={t("scp.overview.expansion")} value="N/A" />
            </div>
          </div>
        </>
      ) : (
        <ScpEmpty message={t("scp.state.empty")} />
      )}
    </ScpPage>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.metricCard}>
      <span className={styles.metricValue}>{value}</span>
      <span className={styles.metricLabel}>{label}</span>
    </div>
  );
}
