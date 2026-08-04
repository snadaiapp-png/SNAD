"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount, type CrmCase } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatDate } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

const CASE_STATUS_FILTERS = ["", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];
const CASE_TYPE_FILTERS = ["", "BUG", "FEATURE", "QUESTION", "SUPPORT"];

/**
 * CRM Cases route — /crm/cases
 *
 * Case/ticket management with lifecycle: OPEN → IN_PROGRESS → RESOLVED → CLOSED.
 * A CLOSED case may be reopened back to IN_PROGRESS.
 *
 * MOD-001: Case/Ticket Management
 */
export default function CrmCasesPage() {
  const { t } = useI18n();
  const [cases, setCases] = useState<CrmCase[]>([]);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [showCreateForm, setShowCreateForm] = useState(false);

  const reload = useCallback(async (status?: string) => {
    setLoading(true);
    setError("");
    try {
      const [nextCases, nextAccounts] = await Promise.all([
        crmApi.cases(status || undefined),
        crmApi.accounts(),
      ]);
      setCases(nextCases);
      setAccounts(nextAccounts);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setCases([]);
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
    const customerId = optionalValue(form, "customerId");
    const dueAt = optionalValue(form, "dueAt");
    const priorityRaw = optionalValue(form, "priority");
    const priority = priorityRaw ? Number(priorityRaw) : undefined;
    await mutate(
      () =>
        crmApi.createCase({
          subject: formValue(form, "subject"),
          description: optionalValue(form, "description"),
          caseType: optionalValue(form, "caseType") || undefined,
          priority,
          customerId,
          dueAt,
        }),
      t("crm.cases.created"),
    );
    formElement.reset();
    setShowCreateForm(false);
  }

  const hasCases = cases.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.cases.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.cases.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        {showCreateForm ? (
          <form className={styles.formCard} onSubmit={handleCreate}>
            <h2 className={styles.sectionHeading}>{t("crm.cases.create.title")}</h2>
            <label>
              {t("crm.cases.create.subject")}
              <input name="subject" required maxLength={240} disabled={busy} />
            </label>
            <label>
              {t("crm.cases.create.description")}
              <textarea name="description" rows={3} maxLength={4000} disabled={busy} />
            </label>
            <label>
              {t("crm.cases.create.type")}
              <select name="caseType" defaultValue="" disabled={busy}>
                <option value="">{t("crm.cases.type.none")}</option>
                {CASE_TYPE_FILTERS.filter(Boolean).map((value) => (
                  <option key={value} value={value}>{t(`crm.cases.type.${value}`) !== `crm.cases.type.${value}` ? t(`crm.cases.type.${value}`) : value}</option>
                ))}
              </select>
            </label>
            <label>
              {t("crm.cases.create.account")}
              <select name="customerId" defaultValue="" disabled={busy}>
                <option value="">{t("crm.cases.create.accountNone")}</option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>{account.display_name}</option>
                ))}
              </select>
            </label>
            <label>
              {t("crm.cases.create.priority")}
              <select name="priority" defaultValue="50" disabled={busy}>
                <option value="20">{t("crm.cases.priority.low")}</option>
                <option value="50">{t("crm.cases.priority.medium")}</option>
                <option value="80">{t("crm.cases.priority.high")}</option>
              </select>
            </label>
            <label>
              {t("crm.cases.create.dueAt")}
              <input name="dueAt" type="date" disabled={busy} />
            </label>
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button type="submit" disabled={busy}>{t("crm.cases.create.submit")}</button>
              <button type="button" disabled={busy} onClick={() => setShowCreateForm(false)}>{t("crm.cases.create.cancel")}</button>
            </div>
          </form>
        ) : (
          <button type="button" className={styles.primaryButton} onClick={() => setShowCreateForm(true)}>
            {t("crm.cases.new")}
          </button>
        )}

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.cases.list.title")}</h2>
            <label>
              {t("crm.cases.filter.status")}
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                disabled={busy}
              >
                {CASE_STATUS_FILTERS.map((value) => (
                  <option key={value} value={value}>
                    {value === "" ? t("crm.cases.filter.all") : t(`crm.cases.status.${value}`) !== `crm.cases.status.${value}` ? t(`crm.cases.status.${value}`) : value}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasCases ? (
            <CrmEmpty
              title={t("crm.cases.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.cases.list.subject")}</th>
                    <th>{t("crm.cases.list.type")}</th>
                    <th>{t("crm.cases.list.status")}</th>
                    <th>{t("crm.cases.list.priority")}</th>
                    <th>{t("crm.cases.list.dueAt")}</th>
                    <th>{t("crm.cases.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {cases.map((c) => {
                    const isOpen = c.status === "OPEN";
                    const inProgress = c.status === "IN_PROGRESS";
                    const resolved = c.status === "resolved" || c.status === "RESOLVED";
                    const closed = c.status === "CLOSED";
                    return (
                      <tr key={c.id}>
                        <td>
                          <a href={`/crm/cases/${c.id}`}>{c.subject}</a>
                        </td>
                        <td>
                          {c.case_type ? (
                            <span className={styles.badge}>{t(`crm.cases.type.${c.case_type}`) !== `crm.cases.type.${c.case_type}` ? t(`crm.cases.type.${c.case_type}`) : c.case_type}</span>
                          ) : "—"}
                        </td>
                        <td>
                          <span className={styles.badge}>{t(`crm.cases.status.${c.status}`) !== `crm.cases.status.${c.status}` ? t(`crm.cases.status.${c.status}`) : c.status}</span>
                        </td>
                        <td>
                          <span className={styles.badge}>{c.priority}</span>
                        </td>
                        <td>{formatDate(c.due_at)}</td>
                        <td>
                          <div style={{ display: "flex", gap: "0.25rem", flexWrap: "wrap" }}>
                            {isOpen ? (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void mutate(() => crmApi.startCase(c.id), t("crm.cases.started"))}
                              >
                                {t("crm.cases.list.start")}
                              </button>
                            ) : null}
                            {(isOpen || inProgress) ? (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void mutate(() => crmApi.resolveCase(c.id), t("crm.cases.resolved"))}
                              >
                                {t("crm.cases.list.resolve")}
                              </button>
                            ) : null}
                            {resolved ? (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void mutate(() => crmApi.closeCase(c.id), t("crm.cases.closed"))}
                              >
                                {t("crm.cases.list.close")}
                              </button>
                            ) : null}
                            {closed ? (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void mutate(() => crmApi.reopenCase(c.id), t("crm.cases.reopened"))}
                              >
                                {t("crm.cases.list.reopen")}
                              </button>
                            ) : null}
                          </div>
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
