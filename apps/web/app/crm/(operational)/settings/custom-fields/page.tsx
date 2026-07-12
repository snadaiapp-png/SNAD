"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { crmApi, type CrmCustomField } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import { CrmEmpty } from "../../../components/crm-empty";
import styles from "../../../crm.module.css";

const ENTITY_TYPES = ["ACCOUNT", "CONTACT", "LEAD", "OPPORTUNITY", "ACTIVITY"];
const DATA_TYPES = ["TEXT", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "EMAIL", "URL"];
const FIELD_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_]{1,119}$/;

/**
 * CRM Custom Fields admin route — /crm/settings/custom-fields
 *
 * Connects to:
 *   - GET /api/v1/crm/custom-fields  (list definitions, optional ?entityType filter)
 *   - POST /api/v1/crm/custom-fields  (create definition, CRM.CUSTOM_FIELD.WRITE)
 *
 * Frontend enforces:
 *   - CRM.ADMIN (or CRM.CUSTOM_FIELD.WRITE) capability for the create form
 *   - Validation: sensitive XOR searchable — a field cannot be both
 *   - Validation: fieldKey pattern (letter prefix, alphanumeric + underscore)
 *
 * Backend remains authoritative for both RBAC and validation; the frontend
 * checks are best-effort UX, not security.
 */
export default function CrmCustomFieldsPage() {
  const { t } = useI18n();
  const { me } = useAuth();

  const [fields, setFields] = useState<CrmCustomField[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [entityFilter, setEntityFilter] = useState("");
  const [formError, setFormError] = useState("");

  // The backend uses CRM.CUSTOM_FIELD.WRITE for create. We expose the form
  // when the user has CRM.ADMIN OR a role code typically associated with
  // custom-field authoring. The backend will still reject unauthorized calls.
  const canCreate = useMemo(() => {
    if (!me) return false;
    return me.roleGrants.some(
      (grant) => grant.status === "ACTIVE" && (grant.roleCode === "ADMIN" || grant.roleCode === "CRM_ADMIN"),
    );
  }, [me]);

  const reload = useCallback(async (filter?: string) => {
    setLoading(true);
    setError("");
    try {
      const next = await crmApi.customFields(filter || undefined);
      setFields(next);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setFields([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(entityFilter), 0);
    return () => window.clearTimeout(timer);
  }, [reload, entityFilter]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const fieldKey = formValue(form, "fieldKey");
    const sensitive = form.get("sensitive") === "on";
    const searchable = form.get("searchable") === "on";

    setFormError("");
    if (!FIELD_KEY_PATTERN.test(fieldKey)) {
      setFormError(t("crm.customFields.validation.fieldKeyPattern"));
      return;
    }
    if (sensitive && searchable) {
      setFormError(t("crm.customFields.validation.sensitiveSearchable"));
      return;
    }

    setBusy(true);
    setError("");
    setNotice("");
    try {
      await crmApi.createCustomField({
        entityType: formValue(form, "entityType"),
        fieldKey,
        labelAr: formValue(form, "labelAr"),
        labelEn: formValue(form, "labelEn"),
        dataType: formValue(form, "dataType"),
        sensitive,
        searchable,
        required: form.get("required") === "on",
      });
      setNotice(t("crm.customFields.created"));
      formElement.reset();
      await reload(entityFilter);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  const hasFields = fields.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.customFields.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.customFields.description")}</p>
      </div>

      {!canCreate ? (
        <div className={styles.notice} role="alert">
          {t("crm.customFields.adminRequired")}
        </div>
      ) : null}

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      {canCreate ? (
        <form className={styles.formCard} onSubmit={handleCreate} style={{ maxWidth: 640 }}>
          <h2 className={styles.sectionHeading}>{t("crm.customFields.create.title")}</h2>
          {formError ? <div className={styles.error} role="alert">{formError}</div> : null}
          <label>
            {t("crm.customFields.create.entityType")}
            <select name="entityType" defaultValue="ACCOUNT" disabled={busy}>
              {ENTITY_TYPES.map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label>
            {t("crm.customFields.create.fieldKey")}
            <input name="fieldKey" required disabled={busy} pattern="[A-Za-z][A-Za-z0-9_]{1,119}" />
            <small>{t("crm.customFields.create.fieldKeyHint")}</small>
          </label>
          <label>
            {t("crm.customFields.create.labelAr")}
            <input name="labelAr" required disabled={busy} dir="rtl" />
          </label>
          <label>
            {t("crm.customFields.create.labelEn")}
            <input name="labelEn" required disabled={busy} dir="ltr" />
          </label>
          <label>
            {t("crm.customFields.create.dataType")}
            <select name="dataType" defaultValue="TEXT" disabled={busy}>
              {DATA_TYPES.map((value) => (
                <option key={value} value={value}>{t(`crm.customFields.dataType.${value}`)}</option>
              ))}
            </select>
          </label>
          <fieldset style={{ border: 0, padding: 0, margin: 0, display: "grid", gap: 8 }}>
            <label style={{ flexDirection: "row", alignItems: "center" }}>
              <input type="checkbox" name="sensitive" disabled={busy} />
              {t("crm.customFields.create.sensitive")}
            </label>
            <label style={{ flexDirection: "row", alignItems: "center" }}>
              <input type="checkbox" name="searchable" disabled={busy} />
              {t("crm.customFields.create.searchable")}
            </label>
            <label style={{ flexDirection: "row", alignItems: "center" }}>
              <input type="checkbox" name="required" disabled={busy} />
              {t("crm.customFields.create.required")}
            </label>
          </fieldset>
          <button type="submit" disabled={busy}>{t("crm.customFields.create.submit")}</button>
        </form>
      ) : null}

      <div className={styles.listCard}>
        <div className={styles.rowHeader}>
          <h2 className={styles.sectionHeading}>{t("crm.customFields.list.title")}</h2>
          <label>
            {t("crm.customFields.filter.entityType")}
            <select value={entityFilter} onChange={(e) => setEntityFilter(e.target.value)} disabled={busy}>
              <option value="">{t("crm.customFields.filter.all")}</option>
              {ENTITY_TYPES.map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
        </div>

        {loading ? (
          <CrmLoading rows={3} />
        ) : !hasFields ? (
          <CrmEmpty
            title={t("crm.customFields.list.empty")}
            hint={t("crm.state.emptyHint")}
          />
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{t("crm.customFields.list.fieldKey")}</th>
                  <th>{t("crm.customFields.list.entityType")}</th>
                  <th>{t("crm.customFields.list.labelAr")}</th>
                  <th>{t("crm.customFields.list.labelEn")}</th>
                  <th>{t("crm.customFields.list.dataType")}</th>
                  <th>{t("crm.customFields.list.flags")}</th>
                </tr>
              </thead>
              <tbody>
                {fields.map((field) => (
                  <tr key={field.id}>
                    <td><code>{field.fieldKey}</code></td>
                    <td>{field.entityType}</td>
                    <td>{field.sensitive ? <span className={styles.redacted}>{t("crm.customFields.redacted")}</span> : field.labelAr}</td>
                    <td>{field.sensitive ? <span className={styles.redacted}>{t("crm.customFields.redacted")}</span> : field.labelEn}</td>
                    <td>{t(`crm.customFields.dataType.${field.dataType}`) !== `crm.customFields.dataType.${field.dataType}` ? t(`crm.customFields.dataType.${field.dataType}`) : field.dataType}</td>
                    <td>
                      <span style={{ display: "inline-flex", gap: 6, flexWrap: "wrap" }}>
                        {field.sensitive ? <span className={`${styles.badge} ${styles.badgeWarning}`}>{t("crm.customFields.list.sensitive")}</span> : null}
                        {field.searchable ? <span className={styles.badge}>{t("crm.customFields.list.searchable")}</span> : null}
                        {field.required ? <span className={`${styles.badge} ${styles.badgeError}`}>{t("crm.customFields.list.required")}</span> : null}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
