"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { executiveApi, type ExecutiveBillingRow } from "@/lib/api/executive-api";
import { scpApi, type TenantRow } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpNotice,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

/**
 * Billing — invoices are always loaded in an explicit tenant context.
 * Finance is the accounting source of truth. The SCP invoice is shown as the
 * subscription billing/dunning projection and is never presented as a second
 * accounting ledger.
 */
export default function BillingPage() {
  const { t } = useI18n();
  const { money, day } = useScpFormat();
  const [tenantQuery, setTenantQuery] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [matches, setMatches] = useState<TenantRow[]>([]);
  const tenantSearchGeneration = useRef(0);
  const [invoices, setInvoices] = useState<ExecutiveBillingRow[] | null>(null);
  const [searchError, setSearchError] = useState("");
  const [invoiceError, setInvoiceError] = useState("");
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");

  useEffect(() => {
    if (!tenantQuery.trim() || tenantId) {
      setMatches([]);
      return;
    }
    const generation = tenantSearchGeneration.current;
    const handle = setTimeout(() => {
      scpApi
        .tenants({ search: tenantQuery.trim(), size: 8, sort: "name", direction: "ASC" })
        .then((page) => {
          if (generation !== tenantSearchGeneration.current) return;
          setMatches(page.content);
          setSearchError("");
        })
        .catch((reason) => {
          if (generation !== tenantSearchGeneration.current) return;
          setMatches([]);
          setSearchError(scpErrorMessage(reason));
        });
    }, 250);
    return () => clearTimeout(handle);
  }, [tenantQuery, tenantId]);

  const load = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setInvoiceError("");
    try {
      setInvoices(await executiveApi.billingV2(tenantId));
    } catch (reason) {
      setInvoiceError(scpErrorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  const filtered = (invoices ?? []).filter((invoice) => {
    const matchesSearch =
      !search.trim() ||
      invoice.invoiceNumber.toLowerCase().includes(search.trim().toLowerCase());
    const matchesStatus = !status || invoice.projectionStatus === status;
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
              tenantSearchGeneration.current += 1;
              setTenantQuery(event.target.value);
              setTenantId("");
              setInvoices(null);
              setSearchError("");
              setInvoiceError("");
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
                    tenantSearchGeneration.current += 1;
                    setTenantId(tenant.id);
                    setTenantQuery(tenant.name);
                    setMatches([]);
                    setSearchError("");
                    setInvoiceError("");
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

      <ScpNotice>{t("scp.billing.financeTruthNotice")}</ScpNotice>

      {loading ? <ScpSkeleton lines={6} /> : null}
      {searchError ? <ScpError message={searchError} /> : null}
      {invoiceError ? <ScpError message={invoiceError} onRetry={load} /> : null}

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
                      <th scope="col">{t("scp.billing.projectionStatus")}</th>
                      <th scope="col">{t("scp.billing.financeStatus")}</th>
                      <th scope="col">{t("scp.billing.total")}</th>
                      <th scope="col">{t("scp.billing.paid")}</th>
                      <th scope="col">{t("scp.billing.outstanding")}</th>
                      <th scope="col">{t("scp.billing.tax")}</th>
                      <th scope="col">{t("scp.billing.credit")}</th>
                      <th scope="col">{t("scp.billing.settlement")}</th>
                      <th scope="col">{t("scp.billing.reconciliation")}</th>
                      <th scope="col">{t("scp.billing.period")}</th>
                      <th scope="col">{t("scp.billing.dueAt")}</th>
                      <th scope="col">{t("scp.billing.paidAt")}</th>
                      <th scope="col">{t("scp.billing.paymentReference")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((invoice) => (
                      <tr key={invoice.id}>
                        <td data-label={t("scp.billing.number")}>
                          {invoice.invoiceNumber}
                          <div className={styles.appCardMeta}>{invoice.projectionSource}</div>
                        </td>
                        <td data-label={t("scp.billing.projectionStatus")}>
                          <ScpStatusPill value={invoice.projectionStatus} />
                        </td>
                        <td data-label={t("scp.billing.financeStatus")}>
                          {invoice.financeLinkId ? <ScpStatusPill value={invoice.financeStatus} /> : t("scp.billing.notLinked")}
                          <div className={styles.appCardMeta}>{invoice.accountingSourceOfTruth}</div>
                        </td>
                        <td data-label={t("scp.billing.total")}>{money(invoice.totalMinor, invoice.currencyCode)}</td>
                        <td data-label={t("scp.billing.paid")}>{money(invoice.amountPaidMinor, invoice.currencyCode)}</td>
                        <td data-label={t("scp.billing.outstanding")}>{money(invoice.outstandingMinor, invoice.currencyCode)}</td>
                        <td data-label={t("scp.billing.tax")}>{money(invoice.taxMinor, invoice.currencyCode)}</td>
                        <td data-label={t("scp.billing.credit")}>{money(invoice.creditAppliedMinor, invoice.currencyCode)}</td>
                        <td data-label={t("scp.billing.settlement")}><ScpStatusPill value={invoice.settlementState} /></td>
                        <td data-label={t("scp.billing.reconciliation")}>
                          <ScpStatusPill value={invoice.reconciliationState} />
                          {invoice.reconciliationClassification ? (
                            <div className={styles.appCardMeta}>{invoice.reconciliationClassification}</div>
                          ) : null}
                        </td>
                        <td data-label={t("scp.billing.period")}>{day(invoice.periodStart)} → {day(invoice.periodEnd)}</td>
                        <td data-label={t("scp.billing.dueAt")}>{day(invoice.dueAt)}</td>
                        <td data-label={t("scp.billing.paidAt")}>{invoice.paidAt ? day(invoice.paidAt) : "—"}</td>
                        <td data-label={t("scp.billing.paymentReference")}>{invoice.paymentReference || "—"}</td>
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
