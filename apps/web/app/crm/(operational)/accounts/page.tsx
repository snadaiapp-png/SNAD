"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { crmApi, type CrmAccount } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, formatDate } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/**
 * CRM Accounts route — /crm/accounts
 *
 * Loads ONLY `crmApi.accounts()` (search-aware). Renders:
 *   - A create-account form (POST /api/v1/crm/accounts)
 *   - A search input that re-queries the backend
 *   - A list with archive/restore buttons and a link to Customer 360
 *
 * Loading/error/empty states are handled explicitly.
 */
export default function CrmAccountsPage() {
  const { t } = useI18n();
  const router = useRouter();
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [searchCommitted, setSearchCommitted] = useState("");

  const reload = useCallback(async (search?: string) => {
    setLoading(true);
    setError("");
    try {
      const next = await crmApi.accounts(search);
      setAccounts(next);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setAccounts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(searchCommitted), 0);
    return () => window.clearTimeout(timer);
  }, [reload, searchCommitted]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload(searchCommitted);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () =>
        crmApi.createAccount({
          displayName: formValue(form, "displayName"),
          accountType: formValue(form, "accountType") || "BUSINESS",
          primaryCurrencyCode: formValue(form, "currency") || "SAR",
          preferredLocale: "ar-SA",
          timeZone: "Asia/Riyadh",
          source: "CRM_WEB",
        }),
      t("crm.accounts.created"),
    );
    formElement.reset();
  }

  function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearchCommitted(searchInput.trim());
  }

  const hasAccounts = accounts.length > 0;

  const accountTypes = useMemo(
    () => [
      { value: "BUSINESS", key: "crm.accounts.type.BUSINESS" },
      { value: "PERSON", key: "crm.accounts.type.PERSON" },
      { value: "PARTNER", key: "crm.accounts.type.PARTNER" },
      { value: "PROSPECT", key: "crm.accounts.type.PROSPECT" },
    ],
    [],
  );

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.accounts.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.accounts.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.accounts.create.title")}</h2>
          <label>
            {t("crm.accounts.create.displayName")}
            <input name="displayName" required disabled={busy} />
          </label>
          <label>
            {t("crm.accounts.create.type")}
            <select name="accountType" defaultValue="BUSINESS" disabled={busy}>
              {accountTypes.map((opt) => (
                <option key={opt.value} value={opt.value}>{t(opt.key)}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.accounts.create.currency")}
            <input name="currency" defaultValue="SAR" maxLength={3} disabled={busy} />
          </label>
          <button type="submit" disabled={busy}>{t("crm.accounts.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.accounts.list.title")}</h2>
            <form onSubmit={handleSearchSubmit} role="search">
              <input
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={t("crm.accounts.search")}
                aria-label={t("crm.accounts.search")}
                disabled={busy}
              />
              <button type="submit" disabled={busy}>{t("common.search")}</button>
            </form>
          </div>

          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasAccounts ? (
            <CrmEmpty
              title={t("crm.accounts.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.accounts.list.name")}</th>
                    <th>{t("crm.accounts.list.type")}</th>
                    <th>{t("crm.accounts.list.currency")}</th>
                    <th>{t("crm.accounts.list.status")}</th>
                    <th>{t("crm.accounts.list.updated")}</th>
                    <th>{t("crm.accounts.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => {
                    const archived = account.lifecycle_status === "ARCHIVED";
                    return (
                      <tr key={account.id}>
                        <td>
                          <Link href={`/crm/accounts/${account.id}`}>{account.display_name}</Link>
                        </td>
                        <td>{t(`crm.accounts.type.${account.account_type}`) !== `crm.accounts.type.${account.account_type}` ? t(`crm.accounts.type.${account.account_type}`) : account.account_type}</td>
                        <td>{account.primary_currency_code ?? "—"}</td>
                        <td>
                          <span className={`${styles.badge} ${archived ? styles.badgeWarning : styles.badgeSuccess}`}>
                            {account.lifecycle_status}
                          </span>
                        </td>
                        <td>{formatDate(account.updated_at)}</td>
                        <td className={styles.rowActions}>
                          <button type="button" onClick={() => router.push(`/crm/accounts/${account.id}`)}>
                            {t("crm.accounts.list.view360")}
                          </button>
                          {!archived ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void mutate(() => crmApi.archiveAccount(account.id), t("crm.accounts.archived"))}
                            >
                              {t("crm.accounts.list.archive")}
                            </button>
                          ) : (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void mutate(() => crmApi.restoreAccount(account.id), t("crm.accounts.restored"))}
                            >
                              {t("crm.accounts.list.restore")}
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
