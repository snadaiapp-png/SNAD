"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmContact } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-shared-styles.module.css";

/* ============================================================================
 *  Contact constants
 * ============================================================================ */

const LIFECYCLE_STATUSES = ["ACTIVE", "INACTIVE", "ARCHIVED"] as const;
type LifecycleStatus = (typeof LIFECYCLE_STATUSES)[number];

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: "var(--snad-success, #10b981)",
  INACTIVE: "var(--snad-warning, #f59e0b)",
  ARCHIVED: "var(--snad-muted, #6b7280)",
};

/* ============================================================================
 *  ContactsTab — main component
 * ============================================================================ */

export function ContactsTab() {
  const { t } = useCrmI18n();
  const [contacts, setContacts] = useState<CrmContact[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<LifecycleStatus | "">("");
  const [showCreateForm, setShowCreateForm] = useState(false);

  const fetchContacts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await crmApi.contacts(undefined, search || undefined);
      setContacts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load contacts");
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    fetchContacts();
  }, [fetchContacts]);

  const handleSearchSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    fetchContacts();
  }, [fetchContacts]);

  const handleArchive = useCallback(async (contact: CrmContact) => {
    try {
      if (contact.lifecycle_status === "ARCHIVED") {
        await crmApi.restoreContact(contact.id);
      } else {
        await crmApi.archiveContact(contact.id);
      }
      await fetchContacts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update contact");
    }
  }, [fetchContacts]);

  const handleContactCreated = useCallback(() => {
    setShowCreateForm(false);
    fetchContacts();
  }, [fetchContacts]);

  const filteredContacts = statusFilter
    ? contacts.filter((c) => c.lifecycle_status === statusFilter)
    : contacts;

  return (
    <div className={styles.tabContent}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.contacts")}</h2>
        <button
          type="button"
          className={styles.primaryButton}
          onClick={() => setShowCreateForm(true)}
        >
          {t("contacts.create")}
        </button>
      </div>

      {/* Search */}
      <form onSubmit={handleSearchSubmit} className={styles.filterBar}>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("contacts.searchPlaceholder")}
          className={styles.formInput}
          style={{ maxWidth: 300 }}
        />
        <button type="submit" className={styles.primaryButton} style={{ padding: "6px 14px" }}>
          {t("contacts.search")}
        </button>
      </form>

      {/* Status filter */}
      <div className={styles.filterBar}>
        <button
          type="button"
          className={`${styles.filterChip} ${statusFilter === "" ? styles.filterChipActive : ""}`}
          onClick={() => setStatusFilter("")}
        >
          {t("contacts.filter.all")}
        </button>
        {LIFECYCLE_STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            className={`${styles.filterChip} ${statusFilter === status ? styles.filterChipActive : ""}`}
            onClick={() => setStatusFilter(status)}
          >
            {t(`contacts.status.${status.toLowerCase()}`)}
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
          <span>{t("contacts.loading")}</span>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && filteredContacts.length === 0 && (
        <div className={styles.emptyLeads}>
          <p>{t("contacts.empty")}</p>
        </div>
      )}

      {/* Contacts table */}
      {!loading && filteredContacts.length > 0 && (
        <div className={styles.tableWrapper}>
          <table className={styles.dataTable}>
            <thead>
              <tr>
                <th>{t("contacts.column.name")}</th>
                <th>{t("contacts.column.email")}</th>
                <th>{t("contacts.column.phone")}</th>
                <th>{t("contacts.column.account")}</th>
                <th>{t("contacts.column.consent")}</th>
                <th>{t("contacts.column.status")}</th>
                <th>{t("contacts.column.updated")}</th>
                <th>{t("contacts.column.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {filteredContacts.map((contact) => (
                <tr key={contact.id}>
                  <td className={styles.cellPrimary}>{contact.display_name}</td>
                  <td>{contact.primary_email ?? "—"}</td>
                  <td>{contact.primary_phone ?? "—"}</td>
                  <td>{contact.account_id ?? "—"}</td>
                  <td>{contact.consent_summary}</td>
                  <td>
                    <span
                      className={styles.statusBadge}
                      style={{ backgroundColor: STATUS_COLORS[contact.lifecycle_status] ?? "var(--snad-muted)" }}
                    >
                      {t(`contacts.status.${contact.lifecycle_status.toLowerCase()}`)}
                    </span>
                  </td>
                  <td>{new Date(contact.updated_at).toLocaleDateString()}</td>
                  <td>
                    <div className={styles.actionGroup}>
                      <button
                        type="button"
                        className={styles.convertButton}
                        onClick={() => handleArchive(contact)}
                      >
                        {contact.lifecycle_status === "ARCHIVED"
                          ? t("contacts.action.restore")
                          : t("contacts.action.archive")}
                      </button>
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
        <ContactsCreateForm
          onCreated={handleContactCreated}
          onCancel={() => setShowCreateForm(false)}
        />
      )}
    </div>
  );
}

/* ============================================================================
 *  ContactsCreateForm — modal form for creating a new contact
 * ============================================================================ */

function ContactsCreateForm({
  onCreated,
  onCancel,
}: {
  onCreated: () => void;
  onCancel: () => void;
}) {
  const { t } = useCrmI18n();
  const [givenName, setGivenName] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [consentSummary, setConsentSummary] = useState("PENDING");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!givenName.trim()) {
      setError(t("contacts.create.givenNameRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await crmApi.createContact({
        givenName: givenName.trim(),
        familyName: familyName.trim() || undefined,
        primaryEmail: email.trim() || undefined,
        primaryPhone: phone.trim() || undefined,
        preferredLocale: "ar",
        timeZone: "Asia/Riyadh",
        consentSummary,
      });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create contact");
    } finally {
      setSubmitting(false);
    }
  }, [givenName, familyName, email, phone, consentSummary, onCreated, t]);

  return (
    <div className={styles.modalOverlay} onClick={onCancel}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>{t("contacts.create.title")}</h3>
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label htmlFor="contact-given">{t("contacts.create.givenName")} *</label>
              <input
                id="contact-given"
                type="text"
                value={givenName}
                onChange={(e) => setGivenName(e.target.value)}
                className={styles.formInput}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="contact-family">{t("contacts.create.familyName")}</label>
              <input
                id="contact-family"
                type="text"
                value={familyName}
                onChange={(e) => setFamilyName(e.target.value)}
                className={styles.formInput}
              />
            </div>
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label htmlFor="contact-email">{t("contacts.create.email")}</label>
              <input
                id="contact-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={styles.formInput}
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="contact-phone">{t("contacts.create.phone")}</label>
              <input
                id="contact-phone"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className={styles.formInput}
              />
            </div>
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="contact-consent">{t("contacts.create.consent")}</label>
            <select
              id="contact-consent"
              value={consentSummary}
              onChange={(e) => setConsentSummary(e.target.value)}
              className={styles.formInput}
            >
              <option value="PENDING">{t("contacts.consent.pending")}</option>
              <option value="GRANTED">{t("contacts.consent.granted")}</option>
              <option value="DENIED">{t("contacts.consent.denied")}</option>
              <option value="WITHDRAWN">{t("contacts.consent.withdrawn")}</option>
            </select>
          </div>
          {error && <div className={styles.formError}>{error}</div>}
          <div className={styles.formActions}>
            <button type="button" className={styles.cancelButton} onClick={onCancel}>
              {t("contacts.create.cancel")}
            </button>
            <button type="submit" className={styles.primaryButton} disabled={submitting}>
              {submitting ? t("contacts.create.submitting") : t("contacts.create.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
