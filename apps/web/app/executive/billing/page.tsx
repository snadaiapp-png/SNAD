"use client";

import { useCallback, useEffect, useState } from "react";
import { executiveApi, type BillingInvoice } from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Input } from "@/components/sds";
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
 * Billing — read experience over the existing invoices contract (semantics
 * preserved). Client-side search filters the loaded set; accounting rules
 * are untouched.
 */
export default function BillingPage() {
  const { t } = useI18n();
  const { money, day } = useScpFormat();
  const [invoices, setInvoices] = useState<BillingInvoice[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setInvoices(await executiveApi.invoices());
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
      <ScpPage title={t("scp.billing.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  const filtered = (invoices ?? []).filter((invoice) => {
    const matchesSearch =
      !search.trim() ||
      invoice.invoiceNumber.toLowerCase().includes(search.trim().toLowerCase());
    const matchesStatus = !status || invoice.status === status;
    return matchesSearch && matchesStatus;
  });

  return (
    <ScpPage title={t("scp.billing.title")} subtitle={t("scp.billing.subtitle")}>
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
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </div>

      {error ? <ScpError message={error} onRetry={load} /> : null}

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
                    <td data-label={t("scp.billing.status")}>
                      <ScpStatusPill value={invoice.status} />
                    </td>
                    <td data-label={t("scp.billing.total")}>
                      {money(invoice.totalMinor, invoice.currencyCode)}
                    </td>
                    <td data-label={t("scp.billing.period")}>
                      {day(invoice.periodStart)} → {day(invoice.periodEnd)}
                    </td>
                    <td data-label={t("scp.billing.dueAt")}>{day(invoice.dueAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </ScpPage>
  );
}
