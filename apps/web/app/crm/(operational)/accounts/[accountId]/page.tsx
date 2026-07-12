"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { crmApi, type Customer360 } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formatDate, formatNumber } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import { CrmError } from "../../../components/crm-error";
import styles from "../../../crm.module.css";

/**
 * Customer 360 route — /crm/accounts/[accountId]
 *
 * Loads `crmApi.customer360(accountId)` and renders the account summary,
 * related contacts, opportunities, activities, and timeline as separate
 * sections. Each section degrades gracefully when empty.
 */
export default function Customer360Page() {
  const { t } = useI18n();
  const params = useParams<{ accountId: string }>();
  const accountId = params?.accountId ?? "";

  const [data, setData] = useState<Customer360 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    if (!accountId) return;
    setLoading(true);
    setError("");
    try {
      const next = await crmApi.customer360(accountId);
      setData(next);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

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
        <Link href="/crm/accounts">{t("crm.customer360.back")}</Link>
      </div>
    );
  }

  if (!data) {
    return (
      <div className={styles.contentInner}>
        <p className={styles.notice}>{t("crm.customer360.notFound")}</p>
        <Link href="/crm/accounts">{t("crm.customer360.back")}</Link>
      </div>
    );
  }

  const account = data.account;
  const archived = account.lifecycle_status === "ARCHIVED";

  return (
    <div className={styles.contentInner}>
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{t("crm.customer360.title")}</h1>
          <p className={styles.pageDescription}>{account.display_name}</p>
        </div>
        <Link href="/crm/accounts">{t("crm.customer360.back")}</Link>
      </div>

      <section className={styles.overviewSection} aria-label={t("crm.customer360.account")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.customer360.account")}</h2>
        <div className={styles.metrics}>
          <article><span>{t("crm.accounts.list.name")}</span><strong>{account.display_name}</strong></article>
          <article><span>{t("crm.accounts.list.type")}</span><strong>{account.account_type}</strong></article>
          <article><span>{t("crm.accounts.list.currency")}</span><strong>{account.primary_currency_code ?? "—"}</strong></article>
          <article><span>{t("crm.accounts.list.status")}</span>
            <strong>
              <span className={`${styles.badge} ${archived ? styles.badgeWarning : styles.badgeSuccess}`}>
                {account.lifecycle_status}
              </span>
            </strong>
          </article>
          <article><span>{t("crm.accounts.list.updated")}</span><strong>{formatDate(account.updated_at)}</strong></article>
        </div>
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.customer360.contacts")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.customer360.contacts")}</h2>
        {data.contacts.length === 0 ? (
          <p className={styles.notice}>{t("crm.customer360.empty.contacts")}</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.contacts.list.name")}</th>
                  <th>{t("crm.contacts.list.email")}</th>
                  <th>{t("crm.contacts.list.status")}</th>
                </tr>
              </thead>
              <tbody>
                {data.contacts.map((contact) => (
                  <tr key={contact.id}>
                    <td>{contact.display_name}</td>
                    <td>{contact.primary_email ?? "—"}</td>
                    <td>{contact.lifecycle_status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.customer360.opportunities")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.customer360.opportunities")}</h2>
        {data.opportunities.length === 0 ? (
          <p className={styles.notice}>{t("crm.customer360.empty.opportunities")}</p>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.opportunities.list.name")}</th>
                  <th>{t("crm.opportunities.list.amount")}</th>
                  <th>{t("crm.opportunities.list.status")}</th>
                  <th>{t("crm.opportunities.list.stage")}</th>
                </tr>
              </thead>
              <tbody>
                {data.opportunities.map((opp) => (
                  <tr key={opp.id}>
                    <td>{opp.name}</td>
                    <td>{formatNumber(opp.amount)} {opp.currency_code}</td>
                    <td>{opp.status}</td>
                    <td>{opp.stage_name ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.customer360.activities")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.customer360.activities")}</h2>
        {data.activities.length === 0 ? (
          <p className={styles.notice}>{t("crm.customer360.empty.activities")}</p>
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
                {data.activities.map((activity) => (
                  <tr key={activity.id}>
                    <td>{activity.subject}</td>
                    <td>{activity.activity_type}</td>
                    <td>{activity.status}</td>
                    <td>{formatDate(activity.due_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.customer360.timeline")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.customer360.timeline")}</h2>
        {data.timeline.length === 0 ? (
          <p className={styles.notice}>{t("crm.customer360.empty.timeline")}</p>
        ) : (
          <ol className={styles.timeline}>
            {data.timeline.map((event) => (
              <li key={event.id}>
                <strong>{event.summary}</strong>
                <span>{formatDate(event.occurred_at)}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
