"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmAccount } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import { Customer360View } from "./customer-360-view";
import styles from "../crm-shared-styles.module.css";

/* ============================================================================
 *  Account constants
 * ============================================================================ */

const ACCOUNT_TYPES = ["CUSTOMER", "PARTNER", "VENDOR", "COMPETITOR", "OTHER"] as const;
type AccountType = (typeof ACCOUNT_TYPES)[number];

const LIFECYCLE_STATUSES = ["ACTIVE", "INACTIVE", "ARCHIVED"] as const;
type LifecycleStatus = (typeof LIFECYCLE_STATUSES)[number];

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: "var(--snad-success, #10b981)",
  INACTIVE: "var(--snad-warning, #f59e0b)",
  ARCHIVED: "var(--snad-muted, #6b7280)",
};

/* ============================================================================
 *  CustomersTab — main component
 * ============================================================================ */

export function CustomersTab() {
  const { t } = useCrmI18n();
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<LifecycleStatus | "">("");
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);

  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await crmApi.accounts(search || undefined);
      setAccounts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load customers");
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  const handleSearchSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    fetchAccounts();
  }, [fetchAccounts]);

  const handleArchive = useCallback(async (account: CrmAccount) => {
    try {
      if (account.lifecycle_status === "ARCHIVED") {
        await crmApi.restoreAccount(account.id);
      } else {
        await crmApi.archiveAccount(account.id);
      }
      await fetchAccounts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update account");
    }
  }, [fetchAccounts]);

  const handleAccountCreated = useCallback(() => {
    setShowCreateForm(false);
    fetchAccounts();
  }, [fetchAccounts]);

  /* ── Customer-360 navigation ──────────────────────────────────────────── */
  if (selectedAccountId) {
    return (
      <Customer360View
        accountId={selectedAccountId}
        onBack={() => setSelectedAccountId(null)}
      />
    );
  }

  const filteredAccounts = statusFilter
    ? accounts.filter((a) => a.lifecycle_status === statusFilter)
    : accounts;

  return (
    <div className={styles.tabContent}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.customers")}</h2>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={() => setShowCreateForm(true)}
        >
          {t("customers.create")}
        </button>
      </div>

      {/* Search */}
      <form onSubmit={handleSearchSubmit} className={styles.filterBar}>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("customers.searchPlaceholder")}
          className={styles.formInput}
          style={{ maxWidth: 300 }}
        />
        <button type="submit" className={styles.primaryButton} style={{ padding: "6px 14px" }}>
          {t("customers.search")}
        </button>
      </form>

      {/* Status filter */}
      <div className={styles.filterBar}>
        <button
          type="button"
          className={`${styles.filterChip} ${statusFilter === "" ? styles.filterChipActive : ""}`}
          onClick={() => setStatusFilter("")}
        >
          {t("customers.filter.all")}
        </button>
        {LIFECYCLE_STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            className={`${styles.filterChip} ${statusFilter === status ? styles.filterChipActive : ""}`}
            onClick={() => setStatusFilter(status)}
          >
            {t(`customers.status.${status.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {/* Error */}
      {error && (
        <div className={styles.errorBanner}>
          {error}
          <button type="button" className={styles.dismissButton} onClick={() => setError(null)}>
            ×
          </button>
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className={styles.loadingState}>
          <div className={styles.spinner} />
          <span>{t("customers.loading")}</span>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && filteredAccounts.length === 0 && (
        <div className={styles.emptyLeads}>
          <p>{t("customers.empty")}</p>
        </div>
      )}

      {/* Accounts table */}
      {!loading && filteredAccounts.length > 0 && (
        <div className={styles.tableWrapper}>
          <table className={styles.dataTable}>
            <thead>
              <tr>
                <th>{t("customers.column.name")}</th>
                <th>{t("customers.column.type")}</th>
                <th>{t("customers.column.status")}</th>
                <th>{t("customers.column.currency")}</th>
                <th>{t("customers.column.owner")}</th>
                <th>{t("customers.column.updated")}</th>
                <th>{t("customers.column.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {filteredAccounts.map((account) => (
                <tr key={account.id}>
                  <td className={styles.cellPrimary}>{account.display_name}</td>
                  <td>{t(`customers.type.${account.account_type.toLowerCase()}`)}</td>
                  <td>
                    <span
                      className={styles.statusBadge}
                      style={{ backgroundColor: STATUS_COLORS[account.lifecycle_status] ?? "var(--snad-muted)" }}
                    >
                      {t(`customers.status.${account.lifecycle_status.toLowerCase()}`)}
                    </span>
                  </td>
                  <td>{account.primary_currency_code ?? "—"}</td>
                  <td>{account.owner_user_id ?? "—"}</td>
                  <td>{new Date(account.updated_at).toLocaleDateString()}</td>
                  <td>
                    <div className={styles.actionGroup}>
                      <button
                        type="button"
                        className={styles.primaryButton}
                        style={{ padding: "4px 10px", fontSize: "0.8rem" }}
                        onClick={() => setSelectedAccountId(account.id)}
                      >
                        {t("customers.action.view")}
                      </button>
                      <button
                        type="button"
                        className={styles.convertButton}
                        onClick={() => handleArchive(account)}
                      >
                        {account.lifecycle_status === "ARCHIVED"
                          ? t("customers.action.restore")
                          : t("customers.action.archive")}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create form modal */}
      {showCreateForm && (
        <CustomersCreateForm
          onCreated={handleAccountCreated}
          onCancel={() => setShowCreateForm(false)}
        />
      )}
    </div>
  );
}

/* ============================================================================
 *  CustomersCreateForm — modal form for creating a new account
 * ============================================================================ */

function CustomersCreateForm({
  onCreated,
  onCancel,
}: {
  onCreated: () => void;
  onCancel: () => void;
}) {
  const { t } = useCrmI18n();
  const [displayName, setDisplayName] = useState("");
  const [accountType, setAccountType] = useState<AccountType>("CUSTOMER");
  const [currencyCode, setCurrencyCode] = useState("SAR");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!displayName.trim()) {
      setError(t("customers.create.nameRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await crmApi.createAccount({
        displayName: displayName.trim(),
        accountType,
        primaryCurrencyCode: currencyCode,
        preferredLocale: "ar",
        timeZone: "Asia/Riyadh",
      });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create account");
    } finally {
      setSubmitting(false);
    }
  }, [displayName, accountType, currencyCode, onCreated, t]);

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>{t("customers.create.title")}</h3>
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.formGroup}>
            <label htmlFor="account-name">{t("customers.create.name")} *</label>
            <input
              id="account-name"
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className={styles.formInput}
              required
            />
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label htmlFor="account-type">{t("customers.create.type")}</label>
              <select
                id="account-type"
                value={accountType}
                onChange={(e) => setAccountType(e.target.value as AccountType)}
                className={styles.formInput}
              >
                {ACCOUNT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {t(`customers.type.${type.toLowerCase()}`)}
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="account-currency">{t("customers.create.currency")}</label>
              <select
                id="account-currency"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value)}
                className={styles.formInput}
              >
                <option value="SAR">SAR — ريال سعودي</option>
                <option value="USD">USD — US Dollar</option>
                <option value="EUR">EUR — Euro</option>
              </select>
            </div>
          </div>
          {error && <div className={styles.formError}>{error}</div>}
          <div className={styles.formActions}>
            <button type="button" className={styles.cancelButton} onClick={onCancel}>
              {t("customers.create.cancel")}
            </button>
            <button type="submit" className={styles.primaryButton} disabled={submitting}>
              {submitting ? t("customers.create.submitting") : t("customers.create.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
