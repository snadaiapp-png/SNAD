"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmSearchResult } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/**
 * CRM Search route — /crm/search
 *
 * Cross-entity search across accounts, contacts, and leads. Returns
 * unified results with entity type, display name, and secondary info.
 * Minimum 2 characters required to trigger search.
 *
 * Branch: feature/crm-search-export
 */
export default function CrmSearchPage() {
  const { t } = useI18n();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CrmSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [searched, setSearched] = useState(false);

  const performSearch = useCallback(async (q: string) => {
    if (q.trim().length < 2) {
      setResults([]);
      setSearched(false);
      return;
    }
    setLoading(true);
    setError("");
    setSearched(true);
    try {
      const data = await crmApi.search(q.trim());
      setResults(data);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Debounce search
  useEffect(() => {
    const timer = window.setTimeout(() => void performSearch(query), 300);
    return () => window.clearTimeout(timer);
  }, [query, performSearch]);

  async function handleDownload(entityType: "accounts" | "contacts" | "leads") {
    setLoading(true);
    setError("");
    try {
      let blob: Blob;
      if (entityType === "accounts") {
        blob = await crmApi.downloadAccountsCsv(query || undefined);
      } else if (entityType === "contacts") {
        blob = await crmApi.downloadContactsCsv(query || undefined);
      } else {
        blob = await crmApi.downloadLeadsCsv(query || undefined);
      }
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `crm-${entityType}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }

  const hasResults = results.length > 0;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.search.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.search.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}

      <section className={styles.workspace}>
        <div className={styles.formCard}>
          <h2 className={styles.sectionHeading}>{t("crm.search.input")}</h2>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("crm.search.placeholder")}
            disabled={loading}
            autoFocus
            style={{ width: "100%", padding: "0.5rem", fontSize: "1rem" }}
          />
          <p style={{ fontSize: "0.75rem", opacity: 0.7, marginTop: "0.5rem" }}>
            {t("crm.search.hint")}
          </p>

          <div style={{ marginTop: "1rem", display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
            <button type="button" onClick={() => void handleDownload("accounts")} disabled={loading}>
              {t("crm.search.exportAccounts")}
            </button>
            <button type="button" onClick={() => void handleDownload("contacts")} disabled={loading}>
              {t("crm.search.exportContacts")}
            </button>
            <button type="button" onClick={() => void handleDownload("leads")} disabled={loading}>
              {t("crm.search.exportLeads")}
            </button>
          </div>
        </div>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.search.results")}</h2>
            {searched && !loading ? (
              <span className={styles.badge}>{results.length} {t("crm.search.found")}</span>
            ) : null}
          </div>

          {!searched ? (
            <CrmEmpty
              title={t("crm.search.startHint")}
              hint={t("crm.search.startHintDetail")}
            />
          ) : loading ? (
            <CrmLoading rows={4} />
          ) : !hasResults ? (
            <CrmEmpty
              title={t("crm.search.noResults")}
              hint={t("crm.search.noResultsHint")}
            />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.search.colType")}</th>
                    <th>{t("crm.search.colName")}</th>
                    <th>{t("crm.search.colInfo")}</th>
                    <th>{t("crm.search.colMatched")}</th>
                  </tr>
                </thead>
                <tbody>
                  {results.map((r, idx) => (
                    <tr key={`${r.entity_type}-${r.entity_id}-${idx}`}>
                      <td>
                        <span className={styles.badge}>{r.entity_type}</span>
                      </td>
                      <td>
                        <a href={`/crm/${r.entity_type.toLowerCase()}s/${r.entity_id}`}>
                          {r.display_name}
                        </a>
                      </td>
                      <td>{r.secondary_info || "—"}</td>
                      <td style={{ fontSize: "0.75rem", opacity: 0.7 }}>{r.matched_field}</td>
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
