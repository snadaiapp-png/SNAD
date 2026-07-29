"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { Customer360, CrmContact, CrmOpportunity, CrmActivity, CrmTimelineEvent } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-command-center.module.css";

/* ============================================================================
 *  Customer360View — full customer-360 detail view
 * ============================================================================
 *  Props:
 *    • accountId — the account to display
 *    • onBack    — callback to return to the accounts list
 * ============================================================================ */

export function Customer360View({
  accountId,
  onBack,
}: {
  accountId: string;
  onBack: () => void;
}) {
  const { t } = useCrmI18n();
  const [data, setData] = useState<Customer360 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await crmApi.customer360(accountId);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load customer data");
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  /* ── Loading ──────────────────────────────────────────────────────────── */
  if (loading) {
    return (
      <div className={styles.tabContent}>
        <div className={styles.tabHeader}>
          <button type="button" className={styles.cancelButton} onClick={onBack}>
            {t("customer360.back")}
          </button>
        </div>
        <div className={styles.loadingState}>
          <div className={styles.spinner} />
          <span>{t("customer360.loading")}</span>
        </div>
      </div>
    );
  }

  /* ── Error ────────────────────────────────────────────────────────────── */
  if (error || !data) {
    return (
      <div className={styles.tabContent}>
        <div className={styles.tabHeader}>
          <button type="button" className={styles.cancelButton} onClick={onBack}>
            {t("customer360.back")}
          </button>
        </div>
        <div className={styles.errorBanner}>
          {error ?? t("customer360.error")}
          <button type="button" className={styles.dismissButton} onClick={onBack}>
            ×
          </button>
        </div>
      </div>
    );
  }

  const { account, contacts, opportunities, activities, timeline } = data;
  const sortedTimeline = [...timeline].sort(
    (a, b) => new Date(b.occurred_at).getTime() - new Date(a.occurred_at).getTime(),
  );

  return (
    <div className={styles.tabContent}>
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className={styles.tabHeader}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button type="button" className={styles.cancelButton} onClick={onBack}>
            {t("customer360.back")}
          </button>
          <h2 className={styles.tabTitle}>{account.display_name}</h2>
        </div>
        <button type="button" className={styles.primaryButton} onClick={fetchData}>
          {t("customer360.refresh")}
        </button>
      </div>

      {/* ── Account Summary ────────────────────────────────────────────── */}
      <section className={styles.kpiGrid}>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>{t("customer360.account.type")}</span>
          <span className={styles.kpiValue}>{account.account_type}</span>
        </div>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>{t("customer360.account.status")}</span>
          <span className={styles.kpiValue}>{account.lifecycle_status}</span>
        </div>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>{t("customer360.account.currency")}</span>
          <span className={styles.kpiValue}>{account.primary_currency_code ?? "—"}</span>
        </div>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>{t("customer360.account.updated")}</span>
          <span className={styles.kpiValue}>{new Date(account.updated_at).toLocaleDateString()}</span>
        </div>
      </section>

      {/* ── Contacts ───────────────────────────────────────────────────── */}
      <Section
        title={t("customer360.section.contacts")}
        emptyMessage={t("customer360.empty.contacts")}
        emptySubtitleKey="customer360.empty.contacts"
        items={contacts}
        renderItem={(contact) => (
          <ContactRow key={contact.id} contact={contact} t={t} />
        )}
      />

      {/* ── Opportunities ──────────────────────────────────────────────── */}
      <Section
        title={t("customer360.section.opportunities")}
        emptyMessage={t("customer360.empty.opportunities")}
        emptySubtitleKey="customer360.empty.opportunities"
        items={opportunities}
        renderItem={(opp) => (
          <OpportunityRow key={opp.id} opportunity={opp} t={t} />
        )}
      />

      {/* ── Activities ─────────────────────────────────────────────────── */}
      <Section
        title={t("customer360.section.activities")}
        emptyMessage={t("customer360.empty.activities")}
        emptySubtitleKey="customer360.empty.activities"
        items={activities}
        renderItem={(activity) => (
          <ActivityRow key={activity.id} activity={activity} t={t} />
        )}
      />

      {/* ── Timeline ───────────────────────────────────────────────────── */}
      <Section
        title={t("customer360.section.timeline")}
        emptyMessage={t("customer360.empty.timeline")}
        emptySubtitleKey="customer360.empty.timeline"
        items={sortedTimeline}
        renderItem={(event) => (
          <TimelineRow key={event.id} event={event} t={t} />
        )}
      />
    </div>
  );
}

/* ============================================================================
 *  Section — reusable section wrapper with empty state
 * ============================================================================ */

function Section<T>({
  title,
  emptyMessage,
  emptySubtitleKey,
  items,
  renderItem,
}: {
  title: string;
  emptyMessage: string;
  emptySubtitleKey: string;
  items: T[];
  renderItem: (item: T) => React.ReactNode;
}) {
  if (items.length === 0) {
    return (
      <div className={styles.emptyLeads}>
        <p>{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div>
      <h3 className={styles.tabTitle} style={{ fontSize: "1rem", marginBottom: 8 }}>
        {title} ({items.length})
      </h3>
      <div className={styles.tableWrapper}>
        <table className={styles.dataTable}>
          <tbody>{items.map(renderItem)}</tbody>
        </table>
      </div>
    </div>
  );
}

/* ============================================================================
 *  ContactRow
 * ============================================================================ */

function ContactRow({
  contact,
  t,
}: {
  contact: CrmContact;
  t: (key: string) => string;
}) {
  return (
    <tr>
      <td className={styles.cellPrimary}>{contact.display_name}</td>
      <td>{contact.primary_email ?? "—"}</td>
      <td>{contact.primary_phone ?? "—"}</td>
      <td>
        <span
          className={styles.statusBadge}
          style={{
            backgroundColor:
              contact.lifecycle_status === "ACTIVE"
                ? "var(--snad-success, #10b981)"
                : contact.lifecycle_status === "INACTIVE"
                  ? "var(--snad-warning, #f59e0b)"
                  : "var(--snad-muted, #6b7280)",
          }}
        >
          {contact.lifecycle_status}
        </span>
      </td>
    </tr>
  );
}

/* ============================================================================
 *  OpportunityRow
 * ============================================================================ */

function OpportunityRow({
  opportunity,
  t,
}: {
  opportunity: CrmOpportunity & { pipeline_name?: string; stage_name?: string };
  t: (key: string) => string;
}) {
  return (
    <tr>
      <td className={styles.cellPrimary}>{opportunity.name}</td>
      <td>{opportunity.pipeline_name ?? opportunity.pipeline_id}</td>
      <td>{opportunity.stage_name ?? opportunity.stage_id}</td>
      <td>
        {opportunity.amount != null
          ? `${opportunity.currency_code} ${opportunity.amount.toLocaleString()}`
          : "—"}
      </td>
      <td>{Math.round(opportunity.probability * 100)}%</td>
      <td>
        <span
          className={styles.statusBadge}
          style={{
            backgroundColor:
              opportunity.status === "OPEN"
                ? "var(--snad-info, #3b82f6)"
                : opportunity.status === "WON"
                  ? "var(--snad-success, #10b981)"
                  : "var(--snad-muted, #6b7280)",
          }}
        >
          {opportunity.status}
        </span>
      </td>
    </tr>
  );
}

/* ============================================================================
 *  ActivityRow
 * ============================================================================ */

function ActivityRow({
  activity,
  t,
}: {
  activity: CrmActivity;
  t: (key: string) => string;
}) {
  return (
    <tr>
      <td className={styles.cellPrimary}>{activity.subject}</td>
      <td>{activity.activity_type}</td>
      <td>
        <span
          className={styles.statusBadge}
          style={{
            backgroundColor:
              activity.status === "COMPLETED"
                ? "var(--snad-success, #10b981)"
                : activity.status === "PENDING"
                  ? "var(--snad-warning, #f59e0b)"
                  : "var(--snad-muted, #6b7280)",
          }}
        >
          {activity.status}
        </span>
      </td>
      <td>{activity.due_at ? new Date(activity.due_at).toLocaleDateString() : "—"}</td>
    </tr>
  );
}

/* ============================================================================
 *  TimelineRow
 * ============================================================================ */

function TimelineRow({
  event,
  t,
}: {
  event: CrmTimelineEvent;
  t: (key: string) => string;
}) {
  return (
    <tr>
      <td className={styles.cellPrimary}>{event.summary}</td>
      <td>{event.event_type}</td>
      <td>{new Date(event.occurred_at).toLocaleDateString()}</td>
    </tr>
  );
}
