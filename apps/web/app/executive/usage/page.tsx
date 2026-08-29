"use client";

import { useCallback, useEffect, useState } from "react";
import { scpApi, type TenantRow, type UsageSnapshot } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import styles from "../scp.module.css";

/**
 * Usage — per-metric current vs limit with percentage, period reset and
 * warning states, from the server-side usage read model.
 */
export default function UsagePage() {
  const { t } = useI18n();
  const { number } = useScpFormat();
  const [tenantQuery, setTenantQuery] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [matches, setMatches] = useState<TenantRow[]>([]);
  const [usage, setUsage] = useState<UsageSnapshot[] | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!tenantQuery.trim()) {
      setMatches([]);
      return;
    }
    const handle = setTimeout(() => {
      scpApi
        .tenants({ search: tenantQuery.trim(), size: 8, sort: "name", direction: "ASC" })
        .then((page) => setMatches(page.content))
        .catch(() => setMatches([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [tenantQuery]);

  const load = useCallback(async () => {
    if (!tenantId) return;
    setBusy(true);
    setError("");
    try {
      setUsage(await scpApi.usage(tenantId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }, [tenantId]);

  return (
    <ScpPage title={t("scp.usage.title")} subtitle={t("scp.usage.subtitle")}>
      <div className={styles.panel}>
        <label className={styles.appCardMeta}>
          <span>{t("scp.entitlements.tenant")}</span>
          <Input
            type="search"
            value={tenantQuery}
            placeholder={t("scp.entitlements.searchTenant")}
            onChange={(event) => {
              setTenantQuery(event.target.value);
              setTenantId("");
              setUsage(null);
            }}
            aria-label={t("scp.entitlements.searchTenant")}
          />
        </label>
        {matches.length > 0 ? (
          <ul className={styles.filters} role="listbox" aria-label={t("scp.entitlements.matches")}>
            {matches.map((tenant) => (
              <li key={tenant.id}>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setTenantId(tenant.id);
                    setTenantQuery(tenant.name);
                    setMatches([]);
                    void load();
                  }}
                >
                  {tenant.name} · {tenant.code}
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
        <Button variant="primary" size="sm" disabled={!tenantId || busy} onClick={() => void load()}>
          {t("scp.usage.load")}
        </Button>
      </div>

      {error ? <ScpError message={error} onRetry={load} /> : null}

      {usage ? (
        usage.length === 0 ? (
          <ScpEmpty message={t("scp.usage.noMetrics")} />
        ) : (
          <div className={styles.metrics}>
            {usage.map((snapshot) => (
              <div key={snapshot.metricCode} className={styles.metricCard}>
                <span className={styles.metricValue}>
                  {number(snapshot.current)}
                  {snapshot.limit !== null ? ` / ${number(snapshot.limit)}` : ""}
                </span>
                <span className={styles.metricLabel}>{snapshot.metricCode}</span>
                {snapshot.percent !== null ? (
                  <>
                    <span className={styles.appCardMeta}>{snapshot.percent}%</span>
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
                  </>
                ) : null}
                <span className={styles.appCardMeta}>
                  {t("scp.usage.limitKind")}: {snapshot.limitKind}
                  {snapshot.warning ? ` · ${t("scp.usage.warning")}` : ""}
                </span>
              </div>
            ))}
          </div>
        )
      ) : null}
    </ScpPage>
  );
}
