"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  crmApi,
  type CrmImportErrorRow,
  type CrmImportJob,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formatDate } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

const ENTITY_TYPES = ["ACCOUNT", "CONTACT", "LEAD", "OPPORTUNITY", "ACTIVITY"];

/**
 * CRM Imports route — /crm/imports
 *
 * Connects to:
 *   - GET /api/v1/crm/imports  (list jobs)
 *   - POST /api/v1/crm/imports/upload (multipart, file + entityType)
 *   - GET /api/v1/crm/imports/{jobId}  (job detail)
 *   - GET /api/v1/crm/imports/{jobId}/errors  (error rows)
 *   - GET /api/v1/crm/imports/{jobId}/errors.csv  (download link)
 *   - POST /api/v1/crm/imports/{jobId}/run
 *   - POST /api/v1/crm/imports/{jobId}/cancel
 *
 * The UI surfaces:
 *   - An upload form (file picker + entity type selector)
 *   - The list of import jobs with progress and actions
 *   - A detail panel for the selected job (status, counts, error rows)
 */
export default function CrmImportsPage() {
  const { t } = useI18n();
  const [jobs, setJobs] = useState<CrmImportJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Selected job detail + its error rows
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [selectedJob, setSelectedJob] = useState<CrmImportJob | null>(null);
  const [selectedErrors, setSelectedErrors] = useState<CrmImportErrorRow[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const next = await crmApi.imports();
      setJobs(next);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setJobs([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  const reloadDetail = useCallback(async (jobId: string) => {
    setDetailLoading(true);
    setDetailError("");
    try {
      const [job, errors] = await Promise.all([
        crmApi.importJob(jobId),
        crmApi.importJobErrors(jobId),
      ]);
      setSelectedJob(job);
      setSelectedErrors(errors);
    } catch (reason) {
      setDetailError(toUserFacingError(reason).message);
      setSelectedJob(null);
      setSelectedErrors([]);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!selectedJobId) {
      // Defer the reset to a timer so we don't trigger a cascading render
      // from inside the effect body (react-hooks/set-state-in-effect).
      const timer = window.setTimeout(() => {
        setSelectedJob(null);
        setSelectedErrors([]);
      }, 0);
      return () => window.clearTimeout(timer);
    }
    const timer = window.setTimeout(() => void reloadDetail(selectedJobId), 0);
    return () => window.clearTimeout(timer);
  }, [selectedJobId, reloadDetail]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload();
      if (selectedJobId) await reloadDetail(selectedJobId);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const entityType = String(form.get("entityType") ?? "").trim();
    const file = form.get("file");
    if (!entityType) return;
    if (!(file instanceof File) || file.size === 0) {
      setError(t("crm.imports.upload.noFile"));
      return;
    }
    await mutate(() => crmApi.uploadImport(file, entityType), t("crm.imports.uploaded"));
    formElement.reset();
  }

  const hasJobs = jobs.length > 0;

  async function downloadCsv(jobId: string) {
    setBusy(true);
    setError("");
    try {
      const blob = await crmApi.downloadImportErrorsCsv(jobId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `crm-import-errors-${jobId}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.imports.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.imports.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleUpload}>
          <h2 className={styles.sectionHeading}>{t("crm.imports.upload.title")}</h2>
          <label>
            {t("crm.imports.upload.entityType")}
            <select name="entityType" defaultValue="ACCOUNT" disabled={busy}>
              {ENTITY_TYPES.map((value) => (
                <option key={value} value={value}>{t(`crm.imports.entityType.${value}`)}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.imports.upload.file")}
            <input name="file" type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" required disabled={busy} />
            <small>{t("crm.imports.upload.fileHint")}</small>
          </label>
          <button type="submit" disabled={busy}>{t("crm.imports.upload.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.imports.list.title")}</h2>
          {loading ? (
            <CrmLoading rows={3} />
          ) : !hasJobs ? (
            <CrmEmpty
              title={t("crm.imports.list.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.imports.list.id")}</th>
                    <th>{t("crm.imports.list.entityType")}</th>
                    <th>{t("crm.imports.list.status")}</th>
                    <th>{t("crm.imports.list.progress")}</th>
                    <th>{t("crm.imports.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map((job) => {
                    const processed = job.processedRows ?? 0;
                    const total = job.totalRows ?? 0;
                    const isRunning = job.status === "PENDING" || job.status === "RUNNING";
                    return (
                      <tr key={job.id}>
                        <td><code>{job.id.slice(0, 8)}</code></td>
                        <td>{t(`crm.imports.entityType.${job.entityType}`) !== `crm.imports.entityType.${job.entityType}` ? t(`crm.imports.entityType.${job.entityType}`) : job.entityType}</td>
                        <td>
                          <span className={`${styles.badge} ${job.status === "DONE" ? styles.badgeSuccess : job.status === "FAILED" || job.status === "CANCELLED" ? styles.badgeError : styles.badgeWarning}`}>
                            {job.status}
                          </span>
                        </td>
                        <td>
                          {total > 0 ? `${processed}/${total}` : processed > 0 ? String(processed) : "—"}
                          {job.failedRows ? ` (${t("crm.imports.detail.failed")}: ${job.failedRows})` : ""}
                        </td>
                        <td className={styles.rowActions}>
                          <button type="button" onClick={() => setSelectedJobId(job.id)}>
                            {t("crm.imports.list.view")}
                          </button>
                          {isRunning ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void mutate(() => crmApi.cancelImport(job.id), t("crm.imports.cancelled"))}
                            >
                              {t("crm.imports.list.cancel")}
                            </button>
                          ) : null}
                          {job.status === "PENDING" ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void mutate(() => crmApi.runImport(job.id), t("crm.imports.runStarted"))}
                            >
                              {t("crm.imports.list.run")}
                            </button>
                          ) : null}
                          {job.failedRows ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void downloadCsv(job.id)}
                            >
                              {t("crm.imports.list.downloadErrors")}
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

      {selectedJobId ? (
        <section className={styles.overviewSection} aria-label={t("crm.imports.detail.title")}>
          <div className={styles.rowHeader}>
            <h2 className={styles.overviewSectionTitle}>{t("crm.imports.detail.title")}</h2>
            <button type="button" onClick={() => setSelectedJobId(null)}>{t("crm.common.close")}</button>
          </div>
          {detailError ? <div className={styles.error} role="alert">{detailError}</div> : null}
          {detailLoading ? (
            <CrmLoading rows={2} />
          ) : !selectedJob ? (
            <p className={styles.notice}>{t("crm.imports.list.empty")}</p>
          ) : (
            <>
              <div className={styles.metrics}>
                <article><span>{t("crm.imports.detail.status")}</span><strong>{selectedJob.status}</strong></article>
                <article><span>{t("crm.imports.detail.total")}</span><strong>{selectedJob.totalRows ?? 0}</strong></article>
                <article><span>{t("crm.imports.detail.processed")}</span><strong>{selectedJob.processedRows ?? 0}</strong></article>
                <article><span>{t("crm.imports.detail.succeeded")}</span><strong>{selectedJob.succeededRows ?? 0}</strong></article>
                <article><span>{t("crm.imports.detail.failed")}</span><strong>{selectedJob.failedRows ?? 0}</strong></article>
                <article><span>{t("crm.accounts.list.updated")}</span><strong>{formatDate(selectedJob.completedAt ?? selectedJob.startedAt ?? selectedJob.uploadedAt)}</strong></article>
              </div>

              <div>
                <div className={styles.rowHeader}>
                  <h3 className={styles.sectionHeading}>{t("crm.imports.detail.errors")}</h3>
                  {selectedJob.failedRows ? (
                    <button type="button" disabled={busy} onClick={() => void downloadCsv(selectedJob.id)}>
                      {t("crm.imports.list.downloadErrors")}
                    </button>
                  ) : null}
                </div>
                {selectedErrors.length === 0 ? (
                  <p className={styles.notice}>{t("crm.imports.detail.errorsEmpty")}</p>
                ) : (
                  <div className={styles.tableWrap}>
                    <table>
                      <thead>
                        <tr>
                          <th>{t("crm.imports.detail.row")}</th>
                          <th>{t("crm.imports.detail.rawData")}</th>
                          <th>{t("crm.imports.detail.errorMessage")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedErrors.map((row, idx) => (
                          <tr key={`${row.rowNumber}-${idx}`}>
                            <td>{row.rowNumber}</td>
                            <td><code>{row.rawData ?? "—"}</code></td>
                            <td>{row.errorMessage ?? "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      ) : null}
    </div>
  );
}
