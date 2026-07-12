"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount, type CrmContact } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/**
 * CRM Contacts route — /crm/contacts
 *
 * Loads `crmApi.contacts()` and `crmApi.accounts()` (for the create-form
 * account picker). Renders:
 *   - Create-contact form
 *   - Search input
 *   - Contacts table with archive buttons
 */
export default function CrmContactsPage() {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<CrmContact[]>([]);
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
      const [nextContacts, nextAccounts] = await Promise.all([
        crmApi.contacts(undefined, search),
        crmApi.accounts(),
      ]);
      setContacts(nextContacts);
      setAccounts(nextAccounts);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setContacts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(searchCommitted), 0);
    return () => window.clearTimeout(timer);
  }, [reload, searchCommitted]);

  const accountNames = new Map(accounts.map((account) => [account.id, account.display_name]));

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
        crmApi.createContact({
          accountId: optionalValue(form, "accountId"),
          givenName: formValue(form, "givenName"),
          familyName: optionalValue(form, "familyName"),
          primaryEmail: optionalValue(form, "email"),
          primaryPhone: optionalValue(form, "phone"),
          preferredLocale: "ar-SA",
          timeZone: "Asia/Riyadh",
          consentSummary: "UNKNOWN",
        }),
      t("crm.contacts.created"),
    );
    formElement.reset();
  }

  function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearchCommitted(searchInput.trim());
  }

  const hasContacts = contacts.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.contacts.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.contacts.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.contacts.create.title")}</h2>
          <label>
            {t("crm.contacts.create.account")}
            <select name="accountId" defaultValue="" disabled={busy}>
              <option value="">{t("crm.contacts.create.accountNone")}</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.display_name}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.contacts.create.givenName")}
            <input name="givenName" required disabled={busy} />
          </label>
          <label>
            {t("crm.contacts.create.familyName")}
            <input name="familyName" disabled={busy} />
          </label>
          <label>
            {t("crm.contacts.create.email")}
            <input name="email" type="email" disabled={busy} />
          </label>
          <label>
            {t("crm.contacts.create.phone")}
            <input name="phone" disabled={busy} />
          </label>
          <button type="submit" disabled={busy}>{t("crm.contacts.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.contacts.list.title")}</h2>
            <form onSubmit={handleSearchSubmit} role="search">
              <input
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={t("crm.contacts.search")}
                aria-label={t("crm.contacts.search")}
                disabled={busy}
              />
              <button type="submit" disabled={busy}>{t("common.search")}</button>
            </form>
          </div>

          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasContacts ? (
            <CrmEmpty
              title={t("crm.contacts.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.contacts.list.name")}</th>
                    <th>{t("crm.contacts.list.account")}</th>
                    <th>{t("crm.contacts.list.email")}</th>
                    <th>{t("crm.contacts.list.status")}</th>
                    <th>{t("crm.contacts.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {contacts.map((contact) => (
                    <tr key={contact.id}>
                      <td>{contact.display_name}</td>
                      <td>{contact.account_id ? accountNames.get(contact.account_id) ?? "—" : "—"}</td>
                      <td>{contact.primary_email ?? "—"}</td>
                      <td>
                        <span className={styles.badge}>{contact.lifecycle_status}</span>
                      </td>
                      <td>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void mutate(() => crmApi.archiveContact(contact.id), t("crm.contacts.archived"))}
                        >
                          {t("crm.contacts.list.archive")}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
