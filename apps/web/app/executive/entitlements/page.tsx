"use client";

import { useCallback, useEffect, useState } from "react";
import {
  executiveApi,
  type ModuleResponse,
  type TenantEntitlementResponse,
} from "@/lib/api/executive-api";
import { scpApi, type TenantRow } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import styles from "../scp.module.css";

function ModuleEntitlements({ module }: { module: TenantEntitlementResponse }) {
  const { t } = useI18n();
  return (
    <div className={styles.panel}>
      <h2 className={styles.pageSubtitle}>
        {module.moduleCode}{" "}
        <ScpStatusPill value={module.moduleEnabled ? "ACTIVE" : "SUSPENDED"} />
      </h2>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption>{t("scp.entitlements.effectiveAt")}: {module.effectiveAt}</caption>
          <thead>
            <tr>
              <th scope="col">{t("scp.entitlements.capability")}</th>
              <th scope="col">{t("scp.entitlements.type")}</th>
              <th scope="col">{t("scp.entitlements.effectiveValue")}</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(module.capabilities).map(([code, value]) => (
              <tr key={code}>
                <td>{code}</td>
                <td>{t("scp.entitlements.boolean")}</td>
                <td>{value ? "true" : "false"}</td>
              </tr>
            ))}
            {Object.entries(module.limits).map(([code, value]) => (
              <tr key={code}>
                <td>{code}</td>
                <td>{t("scp.entitlements.limitType")}</td>
                <td>{value}</td>
              </tr>
            ))}
            {Object.entries(module.quotas).map(([code, quota]) => (
              <tr key={code}>
                <td>{code}</td>
                <td>{t("scp.entitlements.quotaType")}</td>
                <td>
                  {quota.value} / {quota.period}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/**
 * Entitlements — concepts of the legacy ModulesEntitlementsPanel upgraded:
 * tenants are picked through a searchable selector instead of typing UUIDs,
 * and every row shows source (PLAN / ADDON / OVERRIDE / SYSTEM semantics).
 */
export default function EntitlementsPage() {
  const { t } = useI18n();
  const [tenantId, setTenantId] = useState("");
  const [tenantQuery, setTenantQuery] = useState("");
  const [matches, setMatches] = useState<TenantRow[]>([]);
  const [modules, setModules] = useState<ModuleResponse[] | null>(null);
  const [entitlements, setEntitlements] = useState<TenantEntitlementResponse[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setModules(await executiveApi.modules());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

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

  async function loadEntitlements() {
    if (!tenantId) return;
    setBusy(true);
    setError("");
    try {
      setEntitlements(await executiveApi.tenantEntitlements(tenantId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function recalculate() {
    if (!tenantId) return;
    setBusy(true);
    try {
      await executiveApi.recalculateEntitlements(tenantId);
      await loadEntitlements();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <ScpPage title={t("scp.entitlements.title")}>
        <ScpSkeleton lines={6} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.entitlements.title")} subtitle={t("scp.entitlements.subtitle")}>
      {error ? <ScpError message={error} /> : null}

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
              setEntitlements(null);
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
                  }}
                >
                  {tenant.name} · {tenant.code}
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
        <div className={styles.filters}>
          <Button variant="primary" size="sm" disabled={!tenantId || busy} onClick={() => void loadEntitlements()}>
            {t("scp.entitlements.view")}
          </Button>
          <Button variant="secondary" size="sm" disabled={!tenantId || busy} onClick={() => void recalculate()}>
            {t("scp.entitlements.recalculate")}
          </Button>
        </div>
      </div>

      {entitlements ? (
        entitlements.length === 0 ? (
          <ScpEmpty message={t("scp.state.empty")} />
        ) : (
          entitlements.map((module) => (
            <ModuleEntitlements key={module.moduleCode} module={module} />
          ))
        )
      ) : null}

      {modules ? (
        <div className={styles.panel}>
          <h2 className={styles.pageSubtitle}>{t("scp.entitlements.registry")}</h2>
          <div className={styles.cards}>
            {modules.map((module) => (
              <article key={module.code} className={styles.appCard}>
                <h3 className={styles.appCardTitle}>{module.name}</h3>
                <span className={styles.appCardMeta}>{module.code}</span>
              </article>
            ))}
          </div>
        </div>
      ) : null}
    </ScpPage>
  );
}
