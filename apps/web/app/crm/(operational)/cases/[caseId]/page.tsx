"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { crmApi, type CrmCase } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formatDate } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import styles from "../../../crm.module.css";

/**
 * CRM Case Detail route — /crm/cases/[caseId]
 *
 * Shows full case details with status badges and action buttons.
 *
 * MOD-001: Case/Ticket Management
 */
export default function CaseDetailPage() {
  const { t } = useI18n();
  const params = useParams();
  const caseId = params.caseId as string;
  const [caseData, setCaseData] = useState<CrmCase | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editing, setEditing] = useState(false);

  const reload = useCallback(async () => {
    if (!caseId) return;
    setLoading(true);
    setError("");
    try {
      const data = await crmApi.case(caseId);
      setCaseData(data);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const priorityRaw = form.get("priority") as string | null;
    const priority = priorityRaw ? Number(priorityRaw) : undefined;
    await mutate(
      () =>
        crmApi.updateCase(caseId, {
          subject: (form.get("subject") as string) || undefined,
          description: (form.get("description") as string) || undefined,
          caseType: (form.get("caseType") as string) || undefined,
          priority,
        }),
      t("crm.cases.updated"),
    );
    setEditing(false);
  }

  if (loading) return <CrmLoading rows={4} />;
  if (!caseData) return <div className={styles.error}>{error || t("crm.cases.notFound")}</div>;

  const isOpen = caseData.status === "OPEN";
  const inProgress = caseData.status === "IN_PROGRESS";
  const resolved = caseData.status === "RESOLVED";
  const closed = caseData.status === "CLOSED";

  return (
    <div className={styles.contentInner}>
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <div className={styles.rowHeader}>
        <h1 className={styles.pageTitle}>{caseData.subject}</h1>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <span className={styles.badge}>{t(`crm.cases.status.${caseData.status}`) !== `crm.cases.status.${caseData.status}` ? t(`crm.cases.status.${caseData.status}`) : caseData.status}</span>
          {caseData.case_type ? (
            <span className={styles.badge}>{t(`crm.cases.type.${caseData.case_type}`) !== `crm.cases.type.${caseData.case_type}` ? t(`crm.cases.type.${caseData.case_type}`) : caseData.case_type}</span>
          ) : null}
          <span className={styles.badge}>{t("crm.cases.priority.label")}: {caseData.priority}</span>
        </div>
      </div>

      <section className={styles.workspace}>
        {/* Action buttons */}
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginBottom: "1rem" }}>
          {!editing ? (
            <button type="button" disabled={busy} onClick={() => setEditing(true)}>
              {t("crm.cases.detail.edit")}
            </button>
          ) : (
            <button type="button" disabled={busy} onClick={() => setEditing(false)}>
              {t("crm.cases.detail.cancelEdit")}
            </button>
          )}
          {isOpen ? (
            <button type="button" disabled={busy} onClick={() => void mutate(() => crmApi.startCase(caseId), t("crm.cases.started"))}>
              {t("crm.cases.detail.start")}
            </button>
          ) : null}
          {(isOpen || inProgress) ? (
            <button type="button" disabled={busy} onClick={() => void mutate(() => crmApi.resolveCase(caseId), t("crm.cases.resolved"))}>
              {t("crm.cases.detail.resolve")}
            </button>
          ) : null}
          {resolved ? (
            <button type="button" disabled={busy} onClick={() => void mutate(() => crmApi.closeCase(caseId), t("crm.cases.closed"))}>
              {t("crm.cases.detail.close")}
            </button>
          ) : null}
          {closed ? (
            <button type="button" disabled={busy} onClick={() => void mutate(() => crmApi.reopenCase(caseId), t("crm.cases.reopened"))}>
              {t("crm.cases.detail.reopen")}
            </button>
          ) : null}
        </div>

        {/* Edit form or detail view */}
        {editing ? (
          <form className={styles.formCard} onSubmit={handleUpdate}>
            <h2 className={styles.sectionHeading}>{t("crm.cases.detail.editTitle")}</h2>
            <label>
              {t("crm.cases.create.subject")}
              <input name="subject" defaultValue={caseData.subject} required maxLength={240} disabled={busy} />
            </label>
            <label>
              {t("crm.cases.create.description")}
              <textarea name="description" defaultValue={caseData.description || ""} rows={4} maxLength={4000} disabled={busy} />
            </label>
            <label>
              {t("crm.cases.create.type")}
              <select name="caseType" defaultValue={caseData.case_type || ""} disabled={busy}>
                <option value="">—</option>
                <option value="BUG">{t("crm.cases.type.BUG")}</option>
                <option value="FEATURE">{t("crm.cases.type.FEATURE")}</option>
                <option value="QUESTION">{t("crm.cases.type.QUESTION")}</option>
                <option value="SUPPORT">{t("crm.cases.type.SUPPORT")}</option>
              </select>
            </label>
            <label>
              {t("crm.cases.create.priority")}
              <select name="priority" defaultValue={String(caseData.priority)} disabled={busy}>
                <option value="20">{t("crm.cases.priority.low")}</option>
                <option value="50">{t("crm.cases.priority.medium")}</option>
                <option value="80">{t("crm.cases.priority.high")}</option>
              </select>
            </label>
            <button type="submit" disabled={busy}>{t("crm.cases.detail.save")}</button>
          </form>
        ) : (
          <div className={styles.formCard}>
            <h2 className={styles.sectionHeading}>{t("crm.cases.detail.info")}</h2>
            <dl>
              <dt>{t("crm.cases.detail.description")}</dt>
              <dd>{caseData.description || "—"}</dd>
              <dt>{t("crm.cases.detail.createdAt")}</dt>
              <dd>{formatDate(caseData.created_at)}</dd>
              <dt>{t("crm.cases.detail.updatedAt")}</dt>
              <dd>{formatDate(caseData.updated_at)}</dd>
              {caseData.resolved_at ? (
                <>
                  <dt>{t("crm.cases.detail.resolvedAt")}</dt>
                  <dd>{formatDate(caseData.resolved_at)}</dd>
                </>
              ) : null}
              {caseData.closed_at ? (
                <>
                  <dt>{t("crm.cases.detail.closedAt")}</dt>
                  <dd>{formatDate(caseData.closed_at)}</dd>
                </>
              ) : null}
              {caseData.due_at ? (
                <>
                  <dt>{t("crm.cases.detail.dueAt")}</dt>
                  <dd>{formatDate(caseData.due_at)}</dd>
                </>
              ) : null}
              <dt>{t("crm.cases.detail.version")}</dt>
              <dd>{caseData.version}</dd>
            </dl>
          </div>
        )}
      </section>
    </div>
  );
}
