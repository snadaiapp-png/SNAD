"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount, type CrmTask } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatDate } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

const TASK_STATUS_FILTERS = ["", "OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"];

/**
 * CRM Tasks route — /crm/tasks
 *
 * First-class task management with assignee, due date, priority, and a
 * simple OPEN → IN_PROGRESS → COMPLETED|CANCELLED lifecycle.
 *
 * Branch: feature/crm-tasks
 */
export default function CrmTasksPage() {
  const { t } = useI18n();
  const [tasks, setTasks] = useState<CrmTask[]>([]);
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
      const [nextTasks, nextAccounts] = await Promise.all([
        crmApi.tasks(status || undefined),
        crmApi.accounts(),
      ]);
      setTasks(nextTasks);
      setAccounts(nextAccounts);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setTasks([]);
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
    const priorityRaw = optionalValue(form, "priority");
    const priority = priorityRaw ? Number(priorityRaw) : undefined;
    await mutate(
      () =>
        crmApi.createTask({
          title: formValue(form, "title"),
          description: optionalValue(form, "description"),
          relatedType: relatedId ? "ACCOUNT" : undefined,
          relatedId,
          priority,
          dueAt,
        }),
      t("crm.tasks.created"),
    );
    formElement.reset();
  }

  const hasTasks = tasks.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.tasks.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.tasks.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.tasks.create.title")}</h2>
          <label>
            {t("crm.tasks.create.title_label")}
            <input name="title" required maxLength={240} disabled={busy} />
          </label>
          <label>
            {t("crm.tasks.create.description")}
            <textarea name="description" rows={3} maxLength={4000} disabled={busy} />
          </label>
          <label>
            {t("crm.tasks.create.account")}
            <select name="relatedId" defaultValue="" disabled={busy}>
              <option value="">{t("crm.tasks.create.accountNone")}</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.display_name}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.tasks.create.priority")}
            <select name="priority" defaultValue="50" disabled={busy}>
              <option value="20">{t("crm.tasks.priority.low")}</option>
              <option value="50">{t("crm.tasks.priority.medium")}</option>
              <option value="80">{t("crm.tasks.priority.high")}</option>
            </select>
          </label>
          <label>
            {t("crm.tasks.create.dueAt")}
            <input name="dueAt" type="date" disabled={busy} />
          </label>
          <button type="submit" disabled={busy}>{t("crm.tasks.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.tasks.list.title")}</h2>
            <label>
              {t("crm.tasks.filter.status")}
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                disabled={busy}
              >
                {TASK_STATUS_FILTERS.map((value) => (
                  <option key={value} value={value}>
                    {value === "" ? t("crm.tasks.filter.all") : t(`crm.tasks.status.${value}`) !== `crm.tasks.status.${value}` ? t(`crm.tasks.status.${value}`) : value}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasTasks ? (
            <CrmEmpty
              title={t("crm.tasks.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.tasks.list.title")}</th>
                    <th>{t("crm.tasks.list.status")}</th>
                    <th>{t("crm.tasks.list.priority")}</th>
                    <th>{t("crm.tasks.list.dueAt")}</th>
                    <th>{t("crm.tasks.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {tasks.map((task) => {
                    const open = task.status === "OPEN";
                    const inProgress = task.status === "IN_PROGRESS";
                    const actionable = open || inProgress;
                    return (
                      <tr key={task.id}>
                        <td>{task.title}</td>
                        <td>
                          <span className={styles.badge}>{t(`crm.tasks.status.${task.status}`) !== `crm.tasks.status.${task.status}` ? t(`crm.tasks.status.${task.status}`) : task.status}</span>
                        </td>
                        <td>
                          {task.priority !== null && task.priority !== undefined ? (
                            <span className={styles.badge}>{task.priority}</span>
                          ) : "—"}
                        </td>
                        <td>{formatDate(task.due_at)}</td>
                        <td>
                          {actionable ? (
                            <div style={{ display: "flex", gap: "0.25rem", flexWrap: "wrap" }}>
                              {open ? (
                                <button
                                  type="button"
                                  disabled={busy}
                                  onClick={() => void mutate(() => crmApi.startTask(task.id), t("crm.tasks.started"))}
                                >
                                  {t("crm.tasks.list.start")}
                                </button>
                              ) : null}
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void mutate(() => crmApi.completeTask(task.id), t("crm.tasks.completed"))}
                              >
                                {t("crm.tasks.list.complete")}
                              </button>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void mutate(() => crmApi.cancelTask(task.id), t("crm.tasks.cancelled"))}
                              >
                                {t("crm.tasks.list.cancel")}
                              </button>
                            </div>
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
