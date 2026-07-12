"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  crmApi,
  type CrmAccount,
  type CrmActivity,
  type CrmContact,
  type CrmCustomFieldValues,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formatDate } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import { CrmError } from "../../../components/crm-error";
import { CrmEmpty } from "../../../components/crm-empty";
import styles from "../../../crm.module.css";

/**
 * CRM Contact detail route — /crm/contacts/[contactId]
 *
 * Loads:
 *   - crmApi.contact(contactId)        → identity + lifecycle + consent
 *   - crmApi.accounts()                → resolves the linked account name
 *   - crmApi.customFieldValues("CONTACT", contactId) → per-entity values
 *   - crmApi.activities("CONTACT", contactId)       → related activities
 *
 * Renders:
 *   - Identity card (name, email, phone, account link, lifecycle status)
 *   - Consent summary
 *   - Custom field values section
 *   - Related activities table
 *   - Archive / restore buttons (only one is shown depending on lifecycle state)
 *
 * Loading / error / not-found states are handled explicitly per the task
 * contract.
 */
export default function ContactDetailPage() {
  const { t } = useI18n();
  const params = useParams<{ contactId: string }>();
  const contactId = params?.contactId ?? "";

  const [contact, setContact] = useState<CrmContact | null>(null);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [customValues, setCustomValues] = useState<CrmCustomFieldValues | null>(null);
  const [activities, setActivities] = useState<CrmActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    if (!contactId) return;
    setLoading(true);
    setError("");
    try {
      const [nextContact, nextAccounts, nextCustom, nextActivities] = await Promise.all([
        crmApi.contact(contactId),
        crmApi.accounts(),
        crmApi.customFieldValues("CONTACT", contactId).catch(() => null),
        crmApi.activities("CONTACT", contactId).catch(() => [] as CrmActivity[]),
      ]);
      setContact(nextContact);
      setAccounts(nextAccounts);
      setCustomValues(nextCustom);
      setActivities(nextActivities);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setContact(null);
      setCustomValues(null);
      setActivities([]);
    } finally {
      setLoading(false);
    }
  }, [contactId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  async function handleArchiveRestore() {
    if (!contact) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const archived = contact.lifecycle_status === "ARCHIVED";
      if (archived) {
        await crmApi.restoreContact(contact.id);
        setNotice(t("crm.contactDetail.restored"));
      } else {
        await crmApi.archiveContact(contact.id);
        setNotice(t("crm.contactDetail.archived"));
      }
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <div className={styles.contentInner}>
        <CrmLoading />
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.contentInner}>
        <CrmError message={error} onRetry={() => void reload()} />
        <Link href="/crm/contacts">{t("crm.contactDetail.back")}</Link>
      </div>
    );
  }

  if (!contact) {
    return (
      <div className={styles.contentInner}>
        <CrmEmpty
          title={t("crm.contactDetail.notFound")}
          hint={t("crm.state.emptyHint")}
        />
        <Link href="/crm/contacts">{t("crm.contactDetail.back")}</Link>
      </div>
    );
  }

  const archived = contact.lifecycle_status === "ARCHIVED";
  const accountName = contact.account_id
    ? accounts.find((account) => account.id === contact.account_id)?.display_name ?? contact.account_id
    : null;

  return (
    <div className={styles.contentInner}>
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{t("crm.contactDetail.title")}</h1>
          <p className={styles.pageDescription}>{contact.display_name}</p>
        </div>
        <Link href="/crm/contacts">{t("crm.contactDetail.back")}</Link>
      </div>

      {notice ? <div className={styles.success} role="status">{notice}</div> : null}
      {error ? <div className={styles.error} role="alert">{error}</div> : null}

      <section className={styles.overviewSection} aria-label={t("crm.contactDetail.identity")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.contactDetail.identity")}</h2>
        <div className={styles.metrics}>
          <article>
            <span>{t("crm.contactDetail.givenName")}</span>
            <strong>{contact.given_name}</strong>
          </article>
          <article>
            <span>{t("crm.contactDetail.familyName")}</span>
            <strong>{contact.family_name ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.contactDetail.lifecycleStatus")}</span>
            <strong>
              <span className={`${styles.badge} ${archived ? styles.badgeWarning : styles.badgeSuccess}`}>
                {contact.lifecycle_status}
              </span>
            </strong>
          </article>
          <article>
            <span>{t("crm.contactDetail.consentSummary")}</span>
            <strong>{contact.consent_summary}</strong>
          </article>
          <article>
            <span>{t("crm.contactDetail.updated")}</span>
            <strong>{formatDate(contact.updated_at)}</strong>
          </article>
        </div>
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.contactDetail.contactInfo")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.contactDetail.contactInfo")}</h2>
        <div className={styles.metrics}>
          <article>
            <span>{t("crm.contactDetail.email")}</span>
            <strong>{contact.primary_email ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.contactDetail.phone")}</span>
            <strong>{contact.primary_phone ?? "—"}</strong>
          </article>
          <article>
            <span>{t("crm.contactDetail.account")}</span>
            <strong>
              {accountName ? (
                <Link href={`/crm/accounts/${contact.account_id}`}>{accountName}</Link>
              ) : (
                t("crm.common.accountUnknown")
              )}
            </strong>
          </article>
        </div>
        <div className={styles.rowActions}>
          <button
            type="button"
            disabled={busy}
            onClick={() => void handleArchiveRestore()}
          >
            {archived
              ? t("crm.contactDetail.restore")
              : t("crm.contactDetail.archive")}
          </button>
        </div>
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.common.customFields")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.common.customFields")}</h2>
        {!customValues || customValues.values.length === 0 ? (
          <p className={styles.notice}>{t("crm.common.customFieldsEmpty")}</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.customFields.list.fieldKey")}</th>
                  <th>{t("crm.customFields.list.dataType")}</th>
                  <th>{t("crm.common.customFields")}</th>
                </tr>
              </thead>
              <tbody>
                {customValues.values.map((entry) => (
                  <tr key={entry.fieldKey}>
                    <td><code>{entry.fieldKey}</code></td>
                    <td>{entry.sensitive ? t("crm.customFields.list.sensitive") : "—"}</td>
                    <td>
                      {entry.sensitive
                        ? <span className={styles.redacted}>{t("crm.customFields.redacted")}</span>
                        : (entry.displayValue ?? (entry.value == null || entry.value === "" ? "—" : String(entry.value)))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.common.activities")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.common.activities")}</h2>
        {activities.length === 0 ? (
          <p className={styles.notice}>{t("crm.common.activitiesEmpty")}</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.activities.list.subject")}</th>
                  <th>{t("crm.activities.list.type")}</th>
                  <th>{t("crm.activities.list.status")}</th>
                  <th>{t("crm.activities.list.dueAt")}</th>
                </tr>
              </thead>
              <tbody>
                {activities.map((activity) => (
                  <tr key={activity.id}>
                    <td>{activity.subject}</td>
                    <td>{activity.activity_type}</td>
                    <td>
                      <span className={styles.badge}>{activity.status}</span>
                    </td>
                    <td>{formatDate(activity.due_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
