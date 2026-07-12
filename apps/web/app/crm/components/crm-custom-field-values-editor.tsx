"use client";

import { type ChangeEvent, type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  crmApi,
  type CrmCustomField,
  type CrmCustomFieldValueEntry,
  type CrmCustomFieldValues,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { CrmLoading } from "./crm-loading";
import { CrmError } from "./crm-error";
import { CrmEmpty } from "./crm-empty";
import styles from "../crm.module.css";

type DataType = "TEXT" | "NUMBER" | "BOOLEAN" | "DATE" | "DATETIME" | "EMAIL" | "URL";

const REDACTED_PLACEHOLDER = "[REDACTED]";

interface CrmCustomFieldValuesEditorProps {
  /** The entity type, e.g. "ACCOUNT", "CONTACT", "LEAD". */
  entityType: string;
  /** The entity UUID whose custom-field values are being edited. */
  entityId: string;
  /** Optional callback invoked after a successful save. */
  onSaved?: () => void;
}

interface FieldRowState {
  definition: CrmCustomField;
  /** The current editor value (string for inputs, boolean for checkboxes). */
  inputValue: string;
  /** Whether the user has touched the field since the last save. */
  dirty: boolean;
  /** Whether the value is redacted because the user lacks CRM.ADMIN. */
  redacted: boolean;
  /** Validation error message (empty string when valid). */
  validationError: string;
}

/**
 * CrmCustomFieldValuesEditor — reusable editor for per-entity custom-field values.
 *
 * Behaviour:
 *   1. Fetches definitions via `crmApi.customFields(entityType)`.
 *   2. Fetches current values via `crmApi.customFieldValues(entityType, entityId)`.
 *   3. Renders one input per definition, with the widget chosen by `dataType`:
 *        TEXT     → <input type="text">
 *        NUMBER   → <input type="number">
 *        BOOLEAN  → <input type="checkbox">
 *        DATE     → <input type="date">
 *        DATETIME → <input type="datetime-local">
 *        EMAIL    → <input type="email">
 *        URL      → <input type="url">
 *   4. Sensitive fields whose value the user cannot read (no CRM.ADMIN)
 *      are rendered as a disabled input showing [REDACTED]. The
 *      [REDACTED] sentinel is NEVER sent to the backend on save —
 *      dirty redacted fields are skipped.
 *   5. Required-field validation: a required field with an empty value
 *      blocks save and surfaces an inline error.
 *   6. Save button calls `crmApi.upsertCustomFieldValues(entityType, entityId, values)`.
 *   7. Pending / success / error states are surfaced via ARIA roles
 *      (role="status" for success, role="alert" for errors).
 *
 * The backend remains authoritative for RBAC and validation; the
 * frontend checks are best-effort UX.
 */
export function CrmCustomFieldValuesEditor({
  entityType,
  entityId,
  onSaved,
}: CrmCustomFieldValuesEditorProps) {
  const { t } = useI18n();
  const { me } = useAuth();

  const [definitions, setDefinitions] = useState<CrmCustomField[]>([]);
  const [values, setValues] = useState<CrmCustomFieldValues | null>(null);
  const [rows, setRows] = useState<FieldRowState[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // The user can edit custom-field values when they have an active ADMIN
  // or CRM_ADMIN role grant. The backend will still enforce CRM.CUSTOM_FIELD.WRITE.
  const canEdit = useMemo(() => {
    if (!me) return false;
    return me.roleGrants.some(
      (grant) => grant.status === "ACTIVE" && (grant.roleCode === "ADMIN" || grant.roleCode === "CRM_ADMIN"),
    );
  }, [me]);

  // The user can read sensitive values when they have CRM.ADMIN, which
  // the ADMIN role grant implies. Non-admin users see [REDACTED].
  const canReadSensitive = canEdit;

  const reload = useCallback(async () => {
    if (!entityType || !entityId) return;
    setLoading(true);
    setError("");
    try {
      const [nextDefinitions, nextValues] = await Promise.all([
        crmApi.customFields(entityType),
        crmApi.customFieldValues(entityType, entityId).catch(() => null),
      ]);
      // Only show active definitions.
      const active = nextDefinitions.filter((d) => d.active);
      setDefinitions(active);
      setValues(nextValues);
      setRows(buildRows(active, nextValues, canReadSensitive));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setDefinitions([]);
      setValues(null);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [entityType, entityId, canReadSensitive]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  function handleInputChange(fieldKey: string, event: ChangeEvent<HTMLInputElement>) {
    const target = event.target;
    const next = rows.map((row) => {
      if (row.definition.fieldKey !== fieldKey) return row;
      const value =
        row.definition.dataType === "BOOLEAN" ? (target.checked ? "true" : "false") : target.value;
      const validationError = validateRow({ ...row, inputValue: value });
      return { ...row, inputValue: value, dirty: true, validationError };
    });
    setRows(next);
  }

  function validateRow(row: FieldRowState): string {
    if (row.definition.required && !row.redacted) {
      if (row.definition.dataType === "BOOLEAN") {
        // Booleans are "present" when true; a required boolean must be true.
        if (row.inputValue !== "true") {
          return t("crm.customFieldValues.requiredBoolean");
        }
      } else if (!row.inputValue.trim()) {
        return t("crm.customFieldValues.required");
      }
    }
    if (row.definition.dataType === "EMAIL" && row.inputValue.trim()) {
      const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailPattern.test(row.inputValue.trim())) {
        return t("crm.customFieldValues.invalidEmail");
      }
    }
    if (row.definition.dataType === "URL" && row.inputValue.trim()) {
      try {
        new URL(row.inputValue.trim());
      } catch {
        return t("crm.customFieldValues.invalidUrl");
      }
    }
    if (row.definition.dataType === "NUMBER" && row.inputValue.trim()) {
      if (!Number.isFinite(Number(row.inputValue))) {
        return t("crm.customFieldValues.invalidNumber");
      }
    }
    return "";
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEdit || saving) return;

    // Run validation across all rows; block save if any required field is empty.
    const validated = rows.map((row) => ({ ...row, validationError: validateRow(row) }));
    setRows(validated);
    const hasErrors = validated.some((row) => row.validationError);
    if (hasErrors) {
      setError(t("crm.customFieldValues.validationFailed"));
      return;
    }

    // Build the payload. Skip rows that are not dirty OR that are
    // redacted (the [REDACTED] sentinel must never be sent as a value).
    const payload: Record<string, unknown> = {};
    for (const row of validated) {
      if (!row.dirty) continue;
      if (row.redacted) continue; // never send [REDACTED]
      payload[row.definition.fieldKey] = coerceValue(row.definition.dataType, row.inputValue);
    }

    if (Object.keys(payload).length === 0) {
      setNotice(t("crm.customFieldValues.noChanges"));
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    try {
      await crmApi.upsertCustomFieldValues(entityType, entityId, payload);
      setNotice(t("crm.customFieldValues.saved"));
      // Reload to pick up server-side normalization (e.g. redaction).
      await reload();
      onSaved?.();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setSaving(false);
    }
  }

  function handleReset() {
    setRows(buildRows(definitions, values, canReadSensitive));
    setError("");
    setNotice("");
  }

  if (loading) {
    return <CrmLoading rows={3} />;
  }

  if (error && rows.length === 0) {
    return <CrmError message={error} onRetry={() => void reload()} />;
  }

  if (rows.length === 0) {
    return (
      <CrmEmpty
        title={t("crm.customFieldValues.noFields")}
        hint={t("crm.customFieldValues.noFieldsHint")}
      />
    );
  }

  const hasDirtyRows = rows.some((r) => r.dirty && !r.redacted);

  return (
    <form className={styles.formCard} onSubmit={handleSubmit} aria-label={t("crm.common.customFields")}>
      <h2 className={styles.sectionHeading}>{t("crm.common.customFields")}</h2>

      {!canEdit ? (
        <div className={styles.notice} role="alert">
          {t("crm.customFieldValues.readOnly")}
        </div>
      ) : null}

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <fieldset
        disabled={!canEdit || saving}
        style={{ border: 0, padding: 0, margin: 0, display: "grid", gap: 12 }}
      >
        {rows.map((row) => {
          const def = row.definition;
          const label = def.labelEn || def.fieldKey;
          const inputId = `cfv-${def.fieldKey}`;
          const isCheckbox = def.dataType === "BOOLEAN";
          return (
            <div key={def.id} className={styles.formRow}>
              <label htmlFor={inputId} className={isCheckbox ? styles.checkboxLabel : styles.fieldLabel}>
                <span>
                  {label}
                  {def.required ? <span aria-hidden="true" style={{ color: "var(--snad-color-status-error, var(--snad-error))" }}> *</span> : null}
                  {def.sensitive ? (
                    <span className={`${styles.badge} ${styles.badgeWarning}`} style={{ marginLeft: 6 }}>
                      {t("crm.customFields.list.sensitive")}
                    </span>
                  ) : null}
                </span>
                {renderInput(row, inputId, canEdit, handleInputChange)}
              </label>
              {row.validationError ? (
                <small className={styles.error} role="alert" style={{ marginTop: 4 }}>
                  {row.validationError}
                </small>
              ) : null}
              {row.redacted ? (
                <small className={styles.notice} style={{ marginTop: 4 }}>
                  {t("crm.customFieldValues.redactedHint")}
                </small>
              ) : null}
            </div>
          );
        })}
      </fieldset>

      {canEdit ? (
        <div className={styles.rowActions} style={{ marginTop: 12 }}>
          <button type="submit" disabled={saving || !hasDirtyRows}>
            {saving ? t("crm.customFieldValues.saving") : t("crm.customFieldValues.save")}
          </button>
          <button type="button" onClick={handleReset} disabled={saving || !hasDirtyRows}>
            {t("crm.common.cancel")}
          </button>
        </div>
      ) : null}
    </form>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────────

function buildRows(
  definitions: CrmCustomField[],
  values: CrmCustomFieldValues | null,
  canReadSensitive: boolean,
): FieldRowState[] {
  const valueByKey = new Map<string, CrmCustomFieldValueEntry | undefined>();
  if (values) {
    for (const entry of values.values) {
      valueByKey.set(entry.fieldKey, entry);
    }
  }
  return definitions.map((definition) => {
    const entry = valueByKey.get(definition.fieldKey);
    const redacted = !!definition.sensitive && !canReadSensitive;
    let inputValue = "";
    if (entry) {
      if (entry.sensitive && !canReadSensitive) {
        inputValue = REDACTED_PLACEHOLDER;
      } else if (definition.dataType === "BOOLEAN") {
        inputValue = entry.value === true || entry.value === "true" ? "true" : "false";
      } else if (entry.value == null) {
        inputValue = entry.displayValue ?? "";
      } else {
        inputValue = String(entry.value);
      }
    }
    return {
      definition,
      inputValue,
      dirty: false,
      redacted,
      validationError: "",
    };
  });
}

function renderInput(
  row: FieldRowState,
  inputId: string,
  canEdit: boolean,
  onChange: (fieldKey: string, event: ChangeEvent<HTMLInputElement>) => void,
) {
  const def = row.definition;
  const disabled = !canEdit || row.redacted;
  const commonProps = {
    id: inputId,
    name: def.fieldKey,
    disabled,
    onChange: (event: ChangeEvent<HTMLInputElement>) => onChange(def.fieldKey, event),
    "aria-label": def.labelEn || def.fieldKey,
    "aria-invalid": row.validationError ? true : undefined,
    "aria-describedby": row.validationError ? `${inputId}-error` : undefined,
  };

  switch (def.dataType as DataType) {
    case "BOOLEAN":
      return (
        <input
          {...commonProps}
          type="checkbox"
          checked={row.inputValue === "true"}
        />
      );
    case "NUMBER":
      return <input {...commonProps} type="number" step="any" value={row.inputValue} />;
    case "DATE":
      return <input {...commonProps} type="date" value={row.inputValue} />;
    case "DATETIME":
      return <input {...commonProps} type="datetime-local" value={row.inputValue} />;
    case "EMAIL":
      return <input {...commonProps} type="email" value={row.inputValue} placeholder="name@example.com" />;
    case "URL":
      return <input {...commonProps} type="url" value={row.inputValue} placeholder="https://example.com" />;
    case "TEXT":
    default:
      return <input {...commonProps} type="text" value={row.inputValue} />;
  }
}

function coerceValue(dataType: string, raw: string): unknown {
  switch (dataType as DataType) {
    case "BOOLEAN":
      return raw === "true";
    case "NUMBER":
      return raw.trim() === "" ? null : Number(raw);
    case "DATE":
    case "DATETIME":
      return raw.trim() === "" ? null : raw;
    case "TEXT":
    case "EMAIL":
    case "URL":
    default:
      return raw;
  }
}
