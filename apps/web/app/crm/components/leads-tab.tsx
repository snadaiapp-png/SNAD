"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmLead } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-shared-styles.module.css";

/* ============================================================================
 *  Lead status constants
 * ============================================================================ */

const LEAD_STATUSES = ["NEW", "ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "CONVERTED", "ARCHIVED"] as const;
type LeadStatus = (typeof LEAD_STATUSES)[number];

/* Terminal statuses — leads in these states cannot transition */
const TERMINAL_STATUSES = new Set<string>(["CONVERTED", "ARCHIVED"]);

const STATUS_COLORS: Record<string, string> = {
  NEW: "var(--snad-info, #3b82f6)",
  ASSIGNED: "var(--snad-warning, #f59e0b)",
  CONTACTED: "var(--snad-primary, #6366f1)",
  QUALIFIED: "var(--snad-success, #10b981)",
  DISQUALIFIED: "var(--snad-error, #ef4444)",
  ARCHIVED: "var(--snad-muted, #6b7280)",
  CONVERTED: "var(--snad-success, #10b981)",
};

/* ============================================================================
 *  LeadsTab — main component
 * ============================================================================ */

export function LeadsTab() {
  const { t } = useCrmI18n();
  const [leads, setLeads] = useState<CrmLead[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<LeadStatus | "">("");
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [convertLead, setConvertLead] = useState<CrmLead | null>(null);

  const fetchLeads = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await crmApi.leads(statusFilter || undefined);
      setLeads(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load leads");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchLeads();
  }, [fetchLeads]);

  const handleStatusChange = useCallback(async (leadId: string, newStatus: string, currentStatus: string) => {
    /* Prevent PATCH request for terminal leads */
    if (TERMINAL_STATUSES.has(currentStatus)) return;
    try {
      await crmApi.changeLeadStatus(leadId, newStatus);
      await fetchLeads();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to change status");
    }
  }, [fetchLeads]);

  const handleLeadCreated = useCallback(() => {
    setShowCreateForm(false);
    fetchLeads();
  }, [fetchLeads]);

  const handleLeadConverted = useCallback(() => {
    setConvertLead(null);
    fetchLeads();
  }, [fetchLeads]);

  return (
    <div className={styles.tabContent}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.leads")}</h2>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={() => setShowCreateForm(true)}
        >
          {t("leads.create")}
        </button>
      </div>

      {/* Status filter */}
      <div className={styles.filterBar}>
        <button
          type="button"
          className={`${styles.filterChip} ${statusFilter === "" ? styles.filterChipActive : ""}`}
          onClick={() => setStatusFilter("")}
        >
          {t("leads.filter.all")}
        </button>
        {LEAD_STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            className={`${styles.filterChip} ${statusFilter === status ? styles.filterChipActive : ""}`}
            onClick={() => setStatusFilter(status)}
          >
            {t(`leads.status.${status.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {/* Error */}
      {error && (
        <div className={styles.errorBanner}>
          {error}
          <button type="button" className={styles.dismissButton} onClick={() => setError(null)}>
            ×
          </button>
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className={styles.loadingState}>
          <div className={styles.spinner} />
          <span>{t("leads.loading")}</span>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && leads.length === 0 && (
        <div className={styles.emptyLeads}>
          <p>{t("leads.empty")}</p>
        </div>
      )}

      {/* Leads table */}
      {!loading && leads.length > 0 && (
        <div className={styles.tableWrapper}>
          <table className={styles.dataTable}>
            <thead>
              <tr>
                <th>{t("leads.column.name")}</th>
                <th>{t("leads.column.company")}</th>
                <th>{t("leads.column.email")}</th>
                <th>{t("leads.column.status")}</th>
                <th>{t("leads.column.score")}</th>
                <th>{t("leads.column.updated")}</th>
                <th>{t("leads.column.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {leads.map((lead) => (
                <tr key={lead.id}>
                  <td className={styles.cellPrimary}>{lead.display_name}</td>
                  <td>{lead.company_name ?? "—"}</td>
                  <td>{lead.email ?? "—"}</td>
                  <td>
                    <span
                      className={styles.statusBadge}
                      style={{ backgroundColor: STATUS_COLORS[lead.status] ?? "var(--snad-muted)" }}
                    >
                      {t(`leads.status.${lead.status.toLowerCase()}`)}
                    </span>
                  </td>
                  <td>{lead.score ?? "—"}</td>
                  <td>{new Date(lead.updated_at).toLocaleDateString()}</td>
                  <td>
                    <div className={styles.actionGroup}>
                      {TERMINAL_STATUSES.has(lead.status) ? (
                        <span
                          className={styles.statusBadge}
                          style={{ backgroundColor: STATUS_COLORS[lead.status] ?? "var(--snad-muted)" }}
                          aria-label={t("leads.action.terminalState")}
                        >
                          {t(`leads.status.${lead.status.toLowerCase()}`)}
                        </span>
                      ) : (
                        <select
                          className={styles.statusSelect}
                          value={lead.status}
                          onChange={(e) => handleStatusChange(lead.id, e.target.value, lead.status)}
                          aria-label={t("leads.action.changeStatus")}
                        >
                          {LEAD_STATUSES.map((s) => (
                            <option key={s} value={s}>
                              {t(`leads.status.${s.toLowerCase()}`)}
                            </option>
                          ))}
                        </select>
                      )}
                      {!TERMINAL_STATUSES.has(lead.status) && lead.status !== "DISQUALIFIED" && (
                        <button
                          type="button"
                          className={styles.convertButton}
                          onClick={() => setConvertLead(lead)}
                        >
                          {t("leads.action.convert")}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create form modal */}
      {showCreateForm && (
        <LeadsCreateForm
          onCreated={handleLeadCreated}
          onCancel={() => setShowCreateForm(false)}
        />
      )}

      {/* Convert dialog */}
      {convertLead && (
        <LeadsConvertDialog
          lead={convertLead}
          onConverted={handleLeadConverted}
          onCancel={() => setConvertLead(null)}
        />
      )}
    </div>
  );
}

/* ============================================================================
 *  LeadsCreateForm — modal form for creating a new lead
 * ============================================================================ */

function LeadsCreateForm({
  onCreated,
  onCancel,
}: {
  onCreated: () => void;
  onCancel: () => void;
}) {
  const { t } = useCrmI18n();
  const [displayName, setDisplayName] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [source, setSource] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!displayName.trim()) {
      setError(t("leads.create.nameRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await crmApi.createLead({
        displayName: displayName.trim(),
        companyName: companyName.trim() || undefined,
        email: email.trim() || undefined,
        phone: phone.trim() || undefined,
        source: source || undefined,
      });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create lead");
    } finally {
      setSubmitting(false);
    }
  }, [displayName, companyName, email, phone, source, onCreated, t]);

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>{t("leads.create.title")}</h3>
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.formGroup}>
            <label htmlFor="lead-name">{t("leads.create.name")} *</label>
            <input
              id="lead-name"
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className={styles.formInput}
              required
            />
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="lead-company">{t("leads.create.company")}</label>
            <input
              id="lead-company"
              type="text"
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              className={styles.formInput}
            />
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label htmlFor="lead-email">{t("leads.create.email")}</label>
              <input
                id="lead-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={styles.formInput}
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="lead-phone">{t("leads.create.phone")}</label>
              <input
                id="lead-phone"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className={styles.formInput}
              />
            </div>
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="lead-source">{t("leads.create.source")}</label>
            <select
              id="lead-source"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              className={styles.formInput}
            >
              <option value="">{t("leads.create.sourceNone")}</option>
              <option value="WEB">{t("leads.source.web")}</option>
              <option value="REFERRAL">{t("leads.source.referral")}</option>
              <option value="IMPORT">{t("leads.source.import")}</option>
              <option value="MANUAL">{t("leads.source.manual")}</option>
            </select>
          </div>
          {error && <div className={styles.formError}>{error}</div>}
          <div className={styles.formActions}>
            <button type="button" className={styles.cancelButton} onClick={onCancel}>
              {t("leads.create.cancel")}
            </button>
            <button type="submit" className={styles.primaryButton} disabled={submitting}>
              {submitting ? t("leads.create.submitting") : t("leads.create.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/* ============================================================================
 *  LeadsConvertDialog — modal for converting a lead
 * ============================================================================ */

function LeadsConvertDialog({
  lead,
  onConverted,
  onCancel,
}: {
  lead: CrmLead;
  onConverted: () => void;
  onCancel: () => void;
}) {
  const { t } = useCrmI18n();
  const [createOpportunity, setCreateOpportunity] = useState(false);
  const [opportunityName, setOpportunityName] = useState(lead.display_name);
  const [currencyCode, setCurrencyCode] = useState("SAR");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleConvert = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      await crmApi.convertLead(lead.id, {
        createOpportunity,
        currencyCode,
        opportunityName: createOpportunity ? opportunityName : undefined,
      });
      onConverted();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to convert lead");
    } finally {
      setSubmitting(false);
    }
  }, [lead.id, createOpportunity, currencyCode, opportunityName, onConverted]);

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>{t("leads.convert.title")}</h3>
        <div className={styles.convertSummary}>
          <p><strong>{t("leads.convert.name")}:</strong> {lead.display_name}</p>
          {lead.company_name && (
            <p><strong>{t("leads.convert.company")}:</strong> {lead.company_name}</p>
          )}
          {lead.email && (
            <p><strong>{t("leads.convert.email")}:</strong> {lead.email}</p>
          )}
        </div>
        <div className={styles.formGroup}>
          <label className={styles.checkboxLabel}>
            <input
              type="checkbox"
              checked={createOpportunity}
              onChange={(e) => setCreateOpportunity(e.target.checked)}
            />
            {t("leads.convert.createOpportunity")}
          </label>
        </div>
        {createOpportunity && (
          <>
            <div className={styles.formGroup}>
              <label htmlFor="opp-name">{t("leads.convert.oppName")}</label>
              <input
                id="opp-name"
                type="text"
                value={opportunityName}
                onChange={(e) => setOpportunityName(e.target.value)}
                className={styles.formInput}
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="opp-currency">{t("leads.convert.currency")}</label>
              <select
                id="opp-currency"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value)}
                className={styles.formInput}
              >
                <option value="SAR">SAR — ريال سعودي</option>
                <option value="USD">USD — US Dollar</option>
                <option value="EUR">EUR — Euro</option>
              </select>
            </div>
          </>
        )}
        {error && <div className={styles.formError}>{error}</div>}
        <div className={styles.formActions}>
          <button type="button" className={styles.cancelButton} onClick={onCancel}>
            {t("leads.convert.cancel")}
          </button>
          <button type="button" className={styles.primaryButton} onClick={handleConvert} disabled={submitting}>
            {submitting ? t("leads.convert.submitting") : t("leads.convert.submit")}
          </button>
        </div>
      </div>
    </div>
  );
}
