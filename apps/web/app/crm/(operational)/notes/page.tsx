"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount, type CrmNote } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatDateTime } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/**
 * CRM Notes route — /crm/notes
 *
 * Append-only notes attached to any CRM entity. User picks the subject type
 * (account/contact/lead/opportunity/activity/task) and the subject ID (via
 * account picker for V1 simplicity), then writes a note body.
 *
 * Branch: feature/crm-notes
 */
export default function CrmNotesPage() {
  const { t } = useI18n();
  const [notes, setNotes] = useState<CrmNote[]>([]);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedAccountId, setSelectedAccountId] = useState("");

  const reload = useCallback(async (accountId?: string) => {
    setLoading(true);
    setError("");
    try {
      const [nextAccounts, ...rest] = await Promise.all([
        crmApi.accounts(),
        accountId ? crmApi.notes("ACCOUNT", accountId, true) : Promise.resolve([] as CrmNote[]),
      ]);
      setAccounts(nextAccounts);
      setNotes(rest[0]);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setNotes([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(selectedAccountId), 0);
    return () => window.clearTimeout(timer);
  }, [reload, selectedAccountId]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload(selectedAccountId);
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
    const subjectId = formValue(form, "subjectId");
    if (!subjectId) {
      setError(t("crm.notes.error.noSubject"));
      return;
    }
    const body = formValue(form, "body");
    if (!body.trim()) {
      setError(t("crm.notes.error.emptyBody"));
      return;
    }
    await mutate(
      () => crmApi.createNote({ subjectType: "ACCOUNT", subjectId, body }),
      t("crm.notes.created"),
    );
    formElement.reset();
  }

  const hasNotes = notes.length > 0;
  const hasSelectedAccount = Boolean(selectedAccountId);

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.notes.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.notes.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.notes.create.title")}</h2>
          <label>
            {t("crm.notes.create.account")}
            <select
              name="subjectId"
              defaultValue=""
              disabled={busy}
              onChange={(e) => setSelectedAccountId(e.target.value)}
            >
              <option value="">{t("crm.notes.create.accountNone")}</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.display_name}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.notes.create.body")}
            <textarea name="body" rows={5} maxLength={10000} required disabled={busy || !hasSelectedAccount} />
          </label>
          <button type="submit" disabled={busy || !hasSelectedAccount}>{t("crm.notes.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.notes.list.title")}</h2>
            {hasSelectedAccount ? (
              <span className={styles.badge}>{t("crm.notes.list.filtered")}: {accounts.find((a) => a.id === selectedAccountId)?.display_name ?? selectedAccountId}</span>
            ) : (
              <span className={styles.badge}>{t("crm.notes.list.noFilter")}</span>
            )}
          </div>

          {!hasSelectedAccount ? (
            <CrmEmpty
              title={t("crm.notes.empty.noSubject")}
              hint={t("crm.notes.empty.noSubjectHint")}
            />
          ) : loading ? (
            <CrmLoading rows={4} />
          ) : !hasNotes ? (
            <CrmEmpty
              title={t("crm.notes.empty.noNotes")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.notes.list.body")}</th>
                    <th>{t("crm.notes.list.createdAt")}</th>
                    <th>{t("crm.notes.list.status")}</th>
                    <th>{t("crm.notes.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {notes.map((note) => (
                    <tr key={note.id}>
                      <td style={{ maxWidth: "400px", whiteSpace: "pre-wrap" }}>
                        {note.body.length > 200 ? note.body.substring(0, 200) + "..." : note.body}
                      </td>
                      <td>{formatDateTime(note.created_at)}</td>
                      <td>
                        <span className={styles.badge}>{note.archived ? t("crm.notes.status.archived") : t("crm.notes.status.active")}</span>
                      </td>
                      <td>
                        {!note.archived ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void mutate(() => crmApi.archiveNote(note.id), t("crm.notes.archived"))}
                          >
                            {t("crm.notes.list.archive")}
                          </button>
                        ) : null}
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
