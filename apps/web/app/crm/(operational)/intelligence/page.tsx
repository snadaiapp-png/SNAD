"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount } from "@/lib/api/crm";
import { crmIntelligenceApi, type IntelligenceSegment } from "@/lib/api/crm-intelligence";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useCrmI18n } from "../../crm-i18n";
import styles from "../../crm-shared-styles.module.css";
import { IntelligenceTab } from "../../components/intelligence-tab";

/** CRM Intelligence Dashboard — customer intelligence and segmentation. */
export default function IntelligencePage() {
  const { t: globalT } = useI18n();
  const { t, lang } = useCrmI18n();

  // Account selector
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>("");
  const [accountsLoading, setAccountsLoading] = useState(true);

  // Segments
  const [segments, setSegments] = useState<IntelligenceSegment[]>([]);
  const [segmentsLoading, setSegmentsLoading] = useState(true);
  const [segmentsError, setSegmentsError] = useState("");

  // Create segment form
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState({
    segmentCode: "",
    segmentName: "",
    segmentType: "STATIC",
    description: "",
  });
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [createError, setCreateError] = useState("");

  const loadAccounts = useCallback(async () => {
    setAccountsLoading(true);
    try {
      const data = await crmApi.accounts();
      setAccounts(data);
    } catch {
      // silently fail — accounts are optional for the page
    } finally {
      setAccountsLoading(false);
    }
  }, []);

  const loadSegments = useCallback(async () => {
    setSegmentsLoading(true);
    setSegmentsError("");
    try {
      const data = await crmIntelligenceApi.getAllSegments();
      setSegments(data as unknown as IntelligenceSegment[]);
    } catch (reason) {
      setSegmentsError(toUserFacingError(reason).message);
    } finally {
      setSegmentsLoading(false);
    }
  }, []);

  useEffect(() => {
    const t1 = window.setTimeout(() => void loadAccounts(), 0);
    const t2 = window.setTimeout(() => void loadSegments(), 0);
    return () => {
      window.clearTimeout(t1);
      window.clearTimeout(t2);
    };
  }, [loadAccounts, loadSegments]);

  async function handleCreateSegment(e: React.FormEvent) {
    e.preventDefault();
    setCreateSubmitting(true);
    setCreateError("");
    try {
      await crmIntelligenceApi.createSegment({
        segmentCode: createForm.segmentCode,
        segmentName: createForm.segmentName,
        segmentType: createForm.segmentType,
        description: createForm.description || undefined,
      });
      setCreateForm({ segmentCode: "", segmentName: "", segmentType: "STATIC", description: "" });
      setShowCreateForm(false);
      await loadSegments();
    } catch (reason) {
      setCreateError(toUserFacingError(reason).message);
    } finally {
      setCreateSubmitting(false);
    }
  }

  return (
    <div className={styles.contentInner}>
      {/* ── Page Header ──────────────────────────────────────────────── */}
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{t("crm.intelligence.title")}</h1>
          <p className={styles.pageDescription}>{t("crm.intelligence.description")}</p>
        </div>
      </div>

      {/* ── Account Selector ─────────────────────────────────────────── */}
      <section className={styles.overviewSection} aria-label={t("crm.intelligence.selectAccount")}>
        <label
          htmlFor="intelligence-account-select"
          style={{ fontSize: "0.88rem", fontWeight: 700, color: "var(--snad-text-secondary)" }}
        >
          {t("crm.intelligence.selectAccount")}
        </label>
        <select
          id="intelligence-account-select"
          className={styles.formInput}
          value={selectedAccountId}
          onChange={(e) => setSelectedAccountId(e.target.value)}
          disabled={accountsLoading}
          style={{ maxWidth: 400 }}
        >
          <option value="">
            {accountsLoading ? t("crm.intelligence.loadingAccounts") : t("crm.intelligence.chooseAccount")}
          </option>
          {accounts.map((acc) => (
            <option key={acc.id} value={acc.id}>
              {acc.display_name}
            </option>
          ))}
        </select>
      </section>

      {/* ── Intelligence Tab (per account) ───────────────────────────── */}
      {selectedAccountId ? (
        <IntelligenceTab accountId={selectedAccountId} />
      ) : (
        <div className={styles.emptyState}>
          <div className={styles.emptyTitle}>{t("crm.intelligence.selectAccountPrompt")}</div>
          <div className={styles.emptySubtitle}>{t("crm.intelligence.selectAccountHint")}</div>
        </div>
      )}

      {/* ── All Segments ─────────────────────────────────────────────── */}
      <section className={styles.overviewSection} aria-label={t("crm.intelligence.allSegments")}>
        <div className={styles.rowHeader}>
          <h2 className={styles.overviewSectionTitle}>{t("crm.intelligence.allSegments")}</h2>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() => setShowCreateForm(!showCreateForm)}
          >
            {t("crm.intelligence.createSegment")}
          </button>
        </div>

        {segmentsError ? (
          <div className={styles.errorBanner} role="alert">
            <span>{segmentsError}</span>
          </div>
        ) : null}

        {showCreateForm ? (
          <form onSubmit={void handleCreateSegment} className={styles.form} style={{ maxWidth: 500 }}>
            {createError ? <div className={styles.formError}>{createError}</div> : null}
            <div className={styles.formGroup}>
              <label htmlFor="seg-code">{t("crm.intelligence.segmentCode")}</label>
              <input
                id="seg-code"
                className={styles.formInput}
                value={createForm.segmentCode}
                onChange={(e) => { setCreateForm((f) => ({ ...f, segmentCode: e.target.value })); }}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="seg-name">{t("crm.intelligence.segmentName")}</label>
              <input
                id="seg-name"
                className={styles.formInput}
                value={createForm.segmentName}
                onChange={(e) => { setCreateForm((f) => ({ ...f, segmentName: e.target.value })); }}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="seg-type">{t("crm.intelligence.segmentType")}</label>
              <select
                id="seg-type"
                className={styles.formInput}
                value={createForm.segmentType}
                onChange={(e) => { setCreateForm((f) => ({ ...f, segmentType: e.target.value })); }}
              >
                <option value="STATIC">STATIC</option>
                <option value="DYNAMIC">DYNAMIC</option>
              </select>
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="seg-desc">{t("crm.intelligence.segmentDescription")}</label>
              <input
                id="seg-desc"
                className={styles.formInput}
                value={createForm.description}
                onChange={(e) => { setCreateForm((f) => ({ ...f, description: e.target.value })); }}
              />
            </div>
            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={() => setShowCreateForm(false)}
              >
                {t("crm.intelligence.cancel")}
              </button>
              <button
                type="submit"
                className={styles.primaryButton}
                disabled={createSubmitting || !createForm.segmentCode || !createForm.segmentName}
              >
                {createSubmitting ? t("crm.intelligence.creating") : t("crm.intelligence.create")}
              </button>
            </div>
          </form>
        ) : null}

        {segmentsLoading ? (
          <div className={styles.loading}>{t("crm.intelligence.loadingSegments")}</div>
        ) : segments.length === 0 ? (
          <p className={styles.kpiHint}>{t("crm.intelligence.noSegmentsYet")}</p>
        ) : (
          <div className={styles.tableWrapper}>
            <table className={styles.dataTable}>
              <thead>
                <tr>
                  <th>{t("crm.intelligence.segmentCode")}</th>
                  <th>{t("crm.intelligence.segmentName")}</th>
                  <th>{t("crm.intelligence.segmentType")}</th>
                  <th>{t("crm.intelligence.segmentDescription")}</th>
                  <th>{t("crm.intelligence.segmentStatus")}</th>
                </tr>
              </thead>
              <tbody>
                {segments.map((seg) => (
                  <tr key={seg.id}>
                    <td className={styles.cellPrimary}>{seg.segmentCode}</td>
                    <td>{seg.segmentName}</td>
                    <td>{seg.segmentType}</td>
                    <td>{seg.description || "—"}</td>
                    <td>
                      <span
                        className={styles.statusBadge}
                        style={{
                          background: seg.active ? "var(--snad-success-soft)" : "var(--snad-surface-secondary)",
                          color: seg.active ? "var(--snad-success)" : "var(--snad-text-muted)",
                          border: `1px solid ${seg.active ? "var(--snad-success)" : "var(--snad-border-default)"}`,
                        }}
                      >
                        {seg.active ? t("crm.intelligence.active") : t("crm.intelligence.inactive")}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
