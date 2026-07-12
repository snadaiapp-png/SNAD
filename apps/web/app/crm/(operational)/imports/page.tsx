"use client";

import { type ChangeEvent, type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
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
 * Per-entity-type target fields for the import mapping builder.
 * Required fields are marked with `required: true` and must be mapped
 * (or have a default value) before the upload can proceed.
 */
interface TargetField {
  key: string;
  labelKey: string;
  required?: boolean;
}

const TARGET_FIELDS: Record<string, TargetField[]> = {
  ACCOUNT: [
    { key: "displayName", labelKey: "crm.imports.mapping.target.displayName", required: true },
    { key: "accountType", labelKey: "crm.imports.mapping.target.accountType" },
    { key: "primaryCurrencyCode", labelKey: "crm.imports.mapping.target.currency" },
    { key: "source", labelKey: "crm.imports.mapping.target.source" },
  ],
  CONTACT: [
    { key: "givenName", labelKey: "crm.imports.mapping.target.givenName", required: true },
    { key: "familyName", labelKey: "crm.imports.mapping.target.familyName" },
    { key: "primaryEmail", labelKey: "crm.imports.mapping.target.email" },
    { key: "primaryPhone", labelKey: "crm.imports.mapping.target.phone" },
    { key: "accountId", labelKey: "crm.imports.mapping.target.accountId" },
  ],
  LEAD: [
    { key: "displayName", labelKey: "crm.imports.mapping.target.displayName", required: true },
    { key: "companyName", labelKey: "crm.imports.mapping.target.companyName" },
    { key: "email", labelKey: "crm.imports.mapping.target.email" },
    { key: "phone", labelKey: "crm.imports.mapping.target.phone" },
    { key: "source", labelKey: "crm.imports.mapping.target.source" },
  ],
  OPPORTUNITY: [
    { key: "name", labelKey: "crm.imports.mapping.target.name", required: true },
    { key: "accountId", labelKey: "crm.imports.mapping.target.accountId", required: true },
    { key: "pipelineId", labelKey: "crm.imports.mapping.target.pipelineId", required: true },
    { key: "stageId", labelKey: "crm.imports.mapping.target.stageId", required: true },
    { key: "amount", labelKey: "crm.imports.mapping.target.amount" },
    { key: "currencyCode", labelKey: "crm.imports.mapping.target.currency" },
    { key: "expectedCloseDate", labelKey: "crm.imports.mapping.target.expectedCloseDate" },
  ],
  ACTIVITY: [
    { key: "activityType", labelKey: "crm.imports.mapping.target.activityType", required: true },
    { key: "subject", labelKey: "crm.imports.mapping.target.subject", required: true },
    { key: "dueAt", labelKey: "crm.imports.mapping.target.dueAt" },
    { key: "relatedType", labelKey: "crm.imports.mapping.target.relatedType" },
    { key: "relatedId", labelKey: "crm.imports.mapping.target.relatedId" },
  ],
};

const IGNORE_SENTINEL = "__IGNORE__";

interface ParsedColumn {
  /** Header text from the CSV file (or "Column N" when no header row). */
  header: string;
  /** First 5 sample values from this column (for preview). */
  samples: string[];
}

interface ParsedFile {
  /** The columns detected in the file (header + samples). */
  columns: ParsedColumn[];
  /** Total row count (excluding header). */
  rowCount: number;
  /** Whether the file was parsed as CSV. XLSX files are not parsed client-side. */
  parsedAsCsv: boolean;
}

/**
 * CRM Imports route — /crm/imports
 *
 * Connects to:
 *   - GET /api/v1/crm/imports  (list jobs)
 *   - POST /api/v1/crm/imports/upload (multipart, file + entityType + mapping)
 *   - GET /api/v1/crm/imports/{jobId}  (job detail)
 *   - GET /api/v1/crm/imports/{jobId}/errors  (error rows)
 *   - GET /api/v1/crm/imports/{jobId}/errors.csv  (download link)
 *   - POST /api/v1/crm/imports/{jobId}/run
 *   - POST /api/v1/crm/imports/{jobId}/cancel
 *
 * The UI surfaces:
 *   - An upload form with a client-side CSV column-mapping builder
 *   - Column preview (first 5 rows)
 *   - Per-column target-field dropdown (with an "ignore" option)
 *   - Required-field validation + duplicate-mapping prevention
 *   - A mapping summary
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

  // Upload form state
  const [entityType, setEntityType] = useState<string>("ACCOUNT");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [parsedFile, setParsedFile] = useState<ParsedFile | null>(null);
  const [parseError, setParseError] = useState("");
  /** Mapping from column index → target field key (or IGNORE_SENTINEL). */
  const [columnMapping, setColumnMapping] = useState<Record<number, string>>({});
  const [mappingError, setMappingError] = useState("");

  // Selected job detail + its error rows
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [selectedJob, setSelectedJob] = useState<CrmImportJob | null>(null);
  const [selectedErrors, setSelectedErrors] = useState<CrmImportErrorRow[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");

  const targetFields = useMemo(() => TARGET_FIELDS[entityType] ?? [], [entityType]);
  const requiredFields = useMemo(() => targetFields.filter((f) => f.required), [targetFields]);

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

  /**
   * Parse a CSV file client-side using FileReader. Extracts the header row
   * and the first 5 data rows for each column. XLSX files are NOT parsed
   * client-side (the mapping builder is disabled for them — the backend
   * will parse them and the user can omit the mapping).
   */
  function parseCsvFile(file: File): Promise<ParsedFile> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const text = String(reader.result ?? "");
          const lines = text.split(/\r\n|\n|\r/).filter((line) => line.length > 0);
          if (lines.length === 0) {
            reject(new Error(t("crm.imports.mapping.emptyFile")));
            return;
          }
          const headerRow = parseCsvLine(lines[0]);
          const dataLines = lines.slice(1, 6); // up to 5 sample rows
          const columns: ParsedColumn[] = headerRow.map((header, idx) => ({
            header: header || `${t("crm.imports.mapping.column")} ${idx + 1}`,
            samples: dataLines.map((line) => parseCsvLine(line)[idx] ?? ""),
          }));
          resolve({
            columns,
            rowCount: Math.max(0, lines.length - 1),
            parsedAsCsv: true,
          });
        } catch (err) {
          reject(err instanceof Error ? err : new Error(String(err)));
        }
      };
      reader.onerror = () => reject(reader.error ?? new Error("FileReader error"));
      reader.readAsText(file);
    });
  }

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    setSelectedFile(file);
    setParsedFile(null);
    setParseError("");
    setColumnMapping({});
    setMappingError("");
    if (!file) return;

    // Only CSV files are parsed client-side. XLSX files are sent as-is;
    // the mapping builder is hidden and the backend will parse them.
    const isCsv =
      file.type === "text/csv" ||
      file.name.toLowerCase().endsWith(".csv");
    if (!isCsv) {
      // XLSX or other — we still allow the upload, just without a mapping.
      return;
    }
    try {
      const parsed = await parseCsvFile(file);
      setParsedFile(parsed);
      // Auto-map: try to match each column header to a target field by
      // case-insensitive name comparison.
      const auto: Record<number, string> = {};
      parsed.columns.forEach((col, idx) => {
        const normalizedHeader = col.header.trim().toLowerCase().replace(/[\s_-]+/g, "");
        const match = targetFields.find((field) => {
          const normalizedField = field.key.trim().toLowerCase().replace(/[\s_-]+/g, "");
          return normalizedHeader === normalizedField || normalizedHeader.includes(normalizedField);
        });
        auto[idx] = match ? match.key : IGNORE_SENTINEL;
      });
      setColumnMapping(auto);
    } catch (err) {
      setParseError(err instanceof Error ? err.message : String(err));
    }
  }

  function handleMappingChange(columnIndex: number, targetKey: string) {
    setColumnMapping((prev) => ({ ...prev, [columnIndex]: targetKey }));
    setMappingError("");
  }

  /**
   * Validate the mapping before upload. Returns an error message string
   * (empty when valid).
   */
  function validateMapping(): string {
    if (!parsedFile) return "";
    // 1. Required fields must be mapped (not ignored).
    const mappedTargets = new Set(
      Object.values(columnMapping).filter((v) => v !== IGNORE_SENTINEL),
    );
    for (const required of requiredFields) {
      if (!mappedTargets.has(required.key)) {
        return t("crm.imports.mapping.requiredMissing", { field: t(required.labelKey) });
      }
    }
    // 2. No duplicate target field mappings.
    const seen = new Set<string>();
    for (const target of Object.values(columnMapping)) {
      if (target === IGNORE_SENTINEL) continue;
      if (seen.has(target)) {
        return t("crm.imports.mapping.duplicate", { field: target });
      }
      seen.add(target);
    }
    return "";
  }

  function buildMappingPayload(): Record<string, unknown> | undefined {
    if (!parsedFile) return undefined;
    const mapping: Record<string, unknown> = {};
    parsedFile.columns.forEach((col, idx) => {
      const target = columnMapping[idx];
      if (!target || target === IGNORE_SENTINEL) return;
      mapping[target] = { column: idx, header: col.header };
    });
    return Object.keys(mapping).length > 0 ? mapping : undefined;
  }

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!entityType) return;
    if (!selectedFile || selectedFile.size === 0) {
      setError(t("crm.imports.upload.noFile"));
      return;
    }
    // Validate the mapping (if a CSV was parsed).
    const mappingValidation = validateMapping();
    if (mappingValidation) {
      setMappingError(mappingValidation);
      return;
    }
    const mappingPayload = buildMappingPayload();
    await mutate(
      () => crmApi.uploadImport(selectedFile, entityType, mappingPayload),
      t("crm.imports.uploaded"),
    );
    // Reset the form on success.
    setSelectedFile(null);
    setParsedFile(null);
    setColumnMapping({});
    setMappingError("");
    event.currentTarget.reset();
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

  const mappingValidationError = mappingError || validateMapping();
  const canSubmit = !!selectedFile && !mappingValidationError;

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
            <select
              name="entityType"
              value={entityType}
              onChange={(e) => {
                setEntityType(e.target.value);
                // Reset the mapping when the entity type changes so the
                // required-field validation is recomputed against the new
                // target field set.
                setColumnMapping({});
                setMappingError("");
              }}
              disabled={busy}
            >
              {ENTITY_TYPES.map((value) => (
                <option key={value} value={value}>{t(`crm.imports.entityType.${value}`)}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.imports.upload.file")}
            <input
              name="file"
              type="file"
              accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              required
              disabled={busy}
              onChange={handleFileChange}
            />
            <small>{t("crm.imports.upload.fileHint")}</small>
          </label>

          {parseError ? (
            <div className={styles.error} role="alert">{parseError}</div>
          ) : null}

          {parsedFile ? (
            <MappingBuilder
              parsedFile={parsedFile}
              columnMapping={columnMapping}
              targetFields={targetFields}
              requiredFields={requiredFields}
              onMappingChange={handleMappingChange}
              t={t}
            />
          ) : selectedFile && !parseError ? (
            <p className={styles.notice}>{t("crm.imports.mapping.xlsxNotParsed")}</p>
          ) : null}

          {mappingValidationError ? (
            <div className={styles.error} role="alert">{mappingValidationError}</div>
          ) : null}

          <button type="submit" disabled={busy || !canSubmit}>
            {t("crm.imports.upload.submit")}
          </button>
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

// ────────────────────────────────────────────────────────────────────────────
// Mapping Builder sub-component
// ────────────────────────────────────────────────────────────────────────────

interface MappingBuilderProps {
  parsedFile: ParsedFile;
  columnMapping: Record<number, string>;
  targetFields: TargetField[];
  requiredFields: TargetField[];
  onMappingChange: (columnIndex: number, targetKey: string) => void;
  t: (key: string, params?: Record<string, string>) => string;
}

function MappingBuilder({
  parsedFile,
  columnMapping,
  targetFields,
  requiredFields,
  onMappingChange,
  t,
}: MappingBuilderProps) {
  const mappedTargets = new Set(
    Object.values(columnMapping).filter((v) => v !== IGNORE_SENTINEL),
  );
  const unmappedRequired = requiredFields.filter((f) => !mappedTargets.has(f.key));

  return (
    <div className={styles.listCard} style={{ marginTop: 8 }}>
      <h3 className={styles.sectionHeading}>{t("crm.imports.mapping.title")}</h3>
      <p className={styles.notice}>
        {t("crm.imports.mapping.summary", {
          columns: String(parsedFile.columns.length),
          rows: String(parsedFile.rowCount),
        })}
      </p>
      <div className={styles.tableWrap}>
        <table>
          <thead>
            <tr>
              <th>{t("crm.imports.mapping.columnHeader")}</th>
              <th>{t("crm.imports.mapping.samples")}</th>
              <th>{t("crm.imports.mapping.targetField")}</th>
            </tr>
          </thead>
          <tbody>
            {parsedFile.columns.map((col, idx) => {
              const selected = columnMapping[idx] ?? IGNORE_SENTINEL;
              return (
                <tr key={`col-${idx}`}>
                  <td><code>{col.header}</code></td>
                  <td>
                    <small style={{ display: "block", maxWidth: 320, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {col.samples.length > 0 ? col.samples.join(" | ") : t("crm.imports.mapping.noSamples")}
                    </small>
                  </td>
                  <td>
                    <select
                      value={selected}
                      onChange={(e) => onMappingChange(idx, e.target.value)}
                      aria-label={t("crm.imports.mapping.targetField")}
                    >
                      <option value={IGNORE_SENTINEL}>{t("crm.imports.mapping.ignore")}</option>
                      {targetFields.map((field) => (
                        <option key={field.key} value={field.key} disabled={mappedTargets.has(field.key)}>
                          {t(field.labelKey)}
                          {field.required ? " *" : ""}
                          {mappedTargets.has(field.key) ? ` (${t("crm.imports.mapping.alreadyMapped")})` : ""}
                        </option>
                      ))}
                    </select>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {unmappedRequired.length > 0 ? (
        <div className={styles.error} role="alert" style={{ marginTop: 8 }}>
          {t("crm.imports.mapping.requiredMissing", {
            field: unmappedRequired.map((f) => t(f.labelKey)).join(", "),
          })}
        </div>
      ) : null}

      <div className={styles.metrics} style={{ marginTop: 8 }}>
        <article>
          <span>{t("crm.imports.mapping.mappedCount")}</span>
          <strong>{String(mappedTargets.size)}</strong>
        </article>
        <article>
          <span>{t("crm.imports.mapping.ignoredCount")}</span>
          <strong>{String(parsedFile.columns.length - mappedTargets.size)}</strong>
        </article>
        <article>
          <span>{t("crm.imports.mapping.requiredRemaining")}</span>
          <strong>{String(unmappedRequired.length)}</strong>
        </article>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// CSV parsing helpers
// ────────────────────────────────────────────────────────────────────────────

/**
 * Parse a single CSV line into cells. Handles quoted values with embedded
 * commas and escaped double-quotes. This is a minimal RFC 4180-compliant
 * parser sufficient for the import mapping preview — the backend does
 * the authoritative parsing.
 */
function parseCsvLine(line: string): string[] {
  const cells: string[] = [];
  let current = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];
    if (inQuotes) {
      if (char === '"') {
        if (line[i + 1] === '"') {
          current += '"';
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        current += char;
      }
    } else if (char === '"') {
      inQuotes = true;
    } else if (char === ",") {
      cells.push(current);
      current = "";
    } else {
      current += char;
    }
  }
  cells.push(current);
  return cells;
}
