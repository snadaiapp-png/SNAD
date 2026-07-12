"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmLead } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

const LEAD_STATUS_FILTERS = ["", "NEW", "QUALIFIED", "DISQUALIFIED", "CONVERTED", "ARCHIVED"];

/**
 * CRM Leads route — /crm/leads
 *
 * Loads `crmApi.leads(status?)` and supports:
 *   - Create lead
 *   - Filter by status
 *   - Qualify / Disqualify (change status)
 *   - Convert (calls /api/v1/crm/leads/{id}/convert)
 */
export default function CrmLeadsPage() {
  const { t } = useI18n();
  const [leads, setLeads] = useState<CrmLead[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const reload = useCallback(async (status?: string) => {
    setLoading(true);
    setError("");
    try {
      const next = await crmApi.leads(status || undefined);
      setLeads(next);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setLeads([]);
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
    await mutate(
      () =>
        crmApi.createLead({
          displayName: formValue(form, "displayName"),
          companyName: optionalValue(form, "companyName"),
          email: optionalValue(form, "email"),
          phone: optionalValue(form, "phone"),
          source: "CRM_WEB",
        }),
      t("crm.leads.created"),
    );
    formElement.reset();
  }

  const hasLeads = leads.length > 0;
  const terminalStates = ["CONVERTED", "ARCHIVED", "DISQUALIFIED"];

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.leads.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.leads.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.leads.create.title")}</h2>
          <label>
            {t("crm.leads.create.displayName")}
            <input name="displayName" required disabled={busy} />
          </label>
          <label>
            {t("crm.leads.create.companyName")}
            <input name="companyName" disabled={busy} />
          </label>
          <label>
            {t("crm.leads.create.email")}
            <input name="email" type="email" disabled={busy} />
          </label>
          <label>
            {t("crm.leads.create.phone")}
            <input name="phone" disabled={busy} />
          </label>
          <button type="submit" disabled={busy}>{t("crm.leads.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.leads.list.title")}</h2>
            <label>
              {t("crm.leads.filter.status")}
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                disabled={busy}
              >
                {LEAD_STATUS_FILTERS.map((value) => (
                  <option key={value} value={value}>
                    {value === "" ? t("crm.leads.filter.all") : value}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {loading ? (
            <CrmLoading rows={4} />
          ) : !hasLeads ? (
            <CrmEmpty
              title={t("crm.leads.empty")}
              hint={t("crm.state.emptyHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.leads.list.name")}</th>
                    <th>{t("crm.leads.list.company")}</th>
                    <th>{t("crm.leads.list.status")}</th>
                    <th>{t("crm.leads.list.score")}</th>
                    <th>{t("crm.leads.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {leads.map((lead) => (
                    <tr key={lead.id}>
                      <td>{lead.display_name}</td>
                      <td>{lead.company_name ?? "—"}</td>
                      <td>
                        <span className={styles.badge}>{lead.status}</span>
                      </td>
                      <td>{lead.score ?? "—"}</td>
                      <td className={styles.rowActions}>
                        {lead.status === "NEW" ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void mutate(() => crmApi.changeLeadStatus(lead.id, "QUALIFIED"), t("crm.leads.qualified"))}
                          >
                            {t("crm.leads.list.qualify")}
                          </button>
                        ) : null}
                        {!terminalStates.includes(lead.status) ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void mutate(() => crmApi.changeLeadStatus(lead.id, "DISQUALIFIED"), t("crm.leads.disqualified"))}
                          >
                            {t("crm.leads.list.disqualify")}
                          </button>
                        ) : null}
                        {!terminalStates.includes(lead.status) ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void mutate(() => crmApi.convertLead(lead.id, { createOpportunity: true, currencyCode: "SAR" }), t("crm.leads.converted"))}
                          >
                            {t("crm.leads.list.convert")}
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
