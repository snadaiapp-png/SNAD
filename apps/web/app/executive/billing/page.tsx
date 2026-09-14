"use client";

import { useCallback, useEffect, useState } from "react";
import { executiveApi, type BillingInvoice } from "@/lib/api/executive-api";
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
import { useScpFormat } from "../_components/format";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

/**
 * Billing — invoices are always loaded in an explicit tenant context.
 * The backend deliberately returns no rows for an unscoped request, so the
 * UI must never issue one.
 */
export default function BillingPage() {
  const { t } = useI18n();
  const { money, day } = useScpFormat();
  const [tenantQuery, setTenantQuery] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [matches, setMatches] = useState<TenantRow[]>([]);
  const [invoices, setInvoices] = useState<BillingInvoice[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");

  useEffect(() => {
    if (!tenantQuery.trim() || tenantId) {
      setMatches([]);
      return;
    }
    const handle = setTimeout(() => {
      scpApi
        .tenants({ search: tenantQuery.trim(), size: 8, sort: "name", direction: "ASC" })
        .then((page) => {
          setMatches(page.content);
          setError("");
        })
        .catch((reason) => {
          setMatches([]);
          setError(scpErrorMessage(reason));
        });
    }, 250);
    return () => clearTimeout(handle);
  }, [tenantQuery, tenantId]);

  const load = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setError("");
    try {
      setInvoices(await executiveApi.invoices(tenantId));
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  const filtered = (invoices ?? []).filter((invoice) => {
    const matchesSearch =
      !search.trim() ||
      invoice.invoiceNumber.toLowerCase().includes(search.trim().toLowerCase());
    const matchesStatus = !status || invoice.status === status;
    return matchesSearch && matchesStatus;
  });

  return (
    <ScpPage title={t("scp.billing.title")} subtitle={t("scp.billing.subtitle")}>
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
              setInvoices(null);
              setError("");
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
                    setInvoices(null);
                  }}
                >
                  {tenant.name} · {tenant.code}
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
        <Button variant="primary" size="sm" disabled={!tenantId || loading} onClick={() => void load()}>
          {t("scp.billing.load")}
        </Button>
      </div>

      {loading ? <ScpSkeleton lines={6} /> : null}
      {error ? <ScpError message={error} onRetry={load} /> : null}

      {invoices ? (
        <>
          <div className={styles.filters}>
            <Input
              type="search"
              value={search}
              placeholder={t("scp.billing.searchPlaceholder")}
              onChange={(event) => setSearch(event.target.value)}
              aria-label={t("scp.billing.searchPlaceholder")}
            />
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              aria-label={t("scp.billing.statusFilter")}
            >
              <option value="">{t("scp.filters.allStatuses")}</option>
              {["DRAFT", "OPEN", "PAID", "VOID"].map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </div>

          {filtered.length === 0 ? (
            <ScpEmpty message={t("scp.state.empty")} />
          ) : (
            <div className={styles.panel}>
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <caption>{t("scp.billing.count", { count: filtered.length })}</caption>
                  <thead>
                    <tr>
                      <th scope="col">{t("scp.billing.number")}</th>
                      <th scope="col">{t("scp.billing.status")}</th>
                      <th scope="col">{t("scp.billing.total")}</th>
                      <th scope="col">{t("scp.billing.period")}</th>
                      <th scope="col">{t("scp.billing.dueAt")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((invoice) => (
                      <tr key={invoice.id}>
                        <td data-label={t("scp.billing.number")}>{invoice.invoiceNumber}</td>
                        <td data-label={t("scp.billing.status")}><ScpStatusPill value={invoice.status} /></td>
                        <td data-label={t("scp.billing.total")}>{money(invoice.totalMinor, invoice.currencyCode)}</td>
                        <td data-label={t("scp.billing.period")}>{day(invoice.periodStart)} → {day(invoice.periodEnd)}</td>
                        <td data-label={t("scp.billing.dueAt")}>{day(invoice.dueAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      ) : null}
    </ScpPage>
  );
}
