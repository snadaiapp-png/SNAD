"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount, type CrmActivity } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatDate } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

const ACTIVITY_TYPES = ["TASK", "CALL", "MEETING", "NOTE"];
const ACTIVITY_STATUS_FILTERS = ["", "OPEN", "IN_PROGRESS", "DONE", "CANCELLED"];

/**
 * CRM Activities route — /crm/activities
 *
 * Loads `crmApi.activities()` and `crmApi.accounts()` (for the create form
 * account picker). Renders:
 *   - Create-activity form
 *   - Status filter
 *   - Activities list with Complete buttons
 */
export default function CrmActivitiesPage() {
  const { t } = useI18n();
  const [activities, setActivities] = useState<CrmActivity[]>([]);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const reload = useCallback(async (status?: string) => {
    setLoading(true);
    setError("");
    try {
      const [nextActivities, nextAccounts] = await Promise.all([
        crmApi.activities(undefined, undefined, status || undefined),
        crmApi.accounts(),
      ]);
      setActivities(nextActivities);
      setAccounts(nextAccounts);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setActivities([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(statusFilter), 0);
    return () => window.clearTimeout(timer);
  }, [reload, statusFilter]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload(statusFilter);
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
    const relatedId = optionalValue(form, "relatedId");
    const dueAt = optionalValue(form, "dueAt");
    await mutate(
      () =>
        crmApi.createActivity({
          activityType: formValue(form, "activityType") || "TASK",
          subject: formValue(form, "subject"),
          relatedType: relatedId ? "ACCOUNT" : undefined,
          relatedId,
          priority: 50,
          dueAt,
        }),
      t("crm.activities.created"),
    );
    formElement.reset();
  }

  const hasActivities = activities.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.activities.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.activities.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.activities.create.title")}</h2>
          <label>
            {t("crm.activities.create.type")}
            <select name="activityType" defaultValue="TASK" disabled={busy}>
              {ACTIVITY_TYPES.map((type) => (
                <option key={type} value={type}>{t(`crm.activities.type.${type}`)}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.activities.create.subject")}
            <input name="subject" required disabled={busy} />
          </label>
          <label>
            {t("crm.activities.create.account")}
            <select name="relatedId" defaultValue="" disabled={busy}>
              <option value="">{t("crm.activities.create.accountNone")}</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.display_name}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.activities.create.dueAt")}
            <input name="dueAt" type="date" disabled={busy} />
          </label>
          <button type="submit" disabled={busy}>{t("crm.activities.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.activities.list.title")}</h2>
            <label>
              {t("crm.activities.filter.status")}
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                disabled={busy}
              >
                {ACTIVITY_STATUS_FILTERS.map((value) => (
                  <option key={value} value={value}>
                    {value === "" ? t("crm.activities.filter.all") : value}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasActivities ? (
            <CrmEmpty
              title={t("crm.activities.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.activities.list.subject")}</th>
                    <th>{t("crm.activities.list.type")}</th>
                    <th>{t("crm.activities.list.status")}</th>
                    <th>{t("crm.activities.list.dueAt")}</th>
                    <th>{t("crm.activities.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {activities.map((activity) => {
                    const open = ["OPEN", "IN_PROGRESS"].includes(activity.status);
                    return (
                      <tr key={activity.id}>
                        <td>{activity.subject}</td>
                        <td>{t(`crm.activities.type.${activity.activity_type}`) !== `crm.activities.type.${activity.activity_type}` ? t(`crm.activities.type.${activity.activity_type}`) : activity.activity_type}</td>
                        <td>
                          <span className={styles.badge}>{activity.status}</span>
                        </td>
                        <td>{formatDate(activity.due_at)}</td>
                        <td>
                          {open ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void mutate(() => crmApi.completeActivity(activity.id), t("crm.activities.completed"))}
                            >
                              {t("crm.activities.list.complete")}
                            </button>
                          ) : null}
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
