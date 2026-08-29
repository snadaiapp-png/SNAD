"use client";

import { useCallback, useEffect, useState } from "react";
import { scpApi, type AuditEntry, type PageResponse } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import styles from "../scp.module.css";

/**
 * Audit trail — paginated platform audit history with action filter.
 * Records are read-only here; every administrative write upstream already
 * produced them.
 */
export default function AuditPage() {
  const { t } = useI18n();
  const { day } = useScpFormat();
  const [page, setPage] = useState<PageResponse<AuditEntry> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState("");
  const [pageIndex, setPageIndex] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setPage(
        await scpApi.audit({
          action: action || undefined,
          page: pageIndex,
          size: 20,
          sort: "created_at",
          direction: "DESC",
        }),
      );
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, [action, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && !page) {
    return (
      <ScpPage title={t("scp.audit.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.audit.title")} subtitle={t("scp.audit.subtitle")}>
      <form
        className={styles.filters}
        onSubmit={(event) => {
          event.preventDefault();
          setPageIndex(0);
          void load();
        }}
      >
        <Input
          type="search"
          value={action}
          placeholder={t("scp.audit.actionFilter")}
          onChange={(event) => setAction(event.target.value)}
          aria-label={t("scp.audit.actionFilter")}
        />
        <Button type="submit" variant="primary" size="sm">
          {t("scp.filters.apply")}
        </Button>
      </form>

      {error ? <ScpError message={error} onRetry={load} /> : null}

      {!page || page.content.length === 0 ? (
        <ScpEmpty message={t("scp.state.empty")} />
      ) : (
        <div className={styles.panel}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>{t("scp.audit.count", { count: page.totalElements })}</caption>
              <thead>
                <tr>
                  <th scope="col">{t("scp.audit.when")}</th>
                  <th scope="col">{t("scp.audit.action")}</th>
                  <th scope="col">{t("scp.audit.resource")}</th>
                  <th scope="col">{t("scp.audit.reason")}</th>
                  <th scope="col">{t("scp.audit.result")}</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((entry) => (
                  <tr key={entry.id}>
                    <td data-label={t("scp.audit.when")}>{day(entry.createdAt)}</td>
                    <td data-label={t("scp.audit.action")}>{entry.action}</td>
                    <td data-label={t("scp.audit.resource")}>
                      {entry.resourceType}:{entry.resourceId.slice(0, 8)}…
                    </td>
                    <td data-label={t("scp.audit.reason")}>{entry.reason ?? "—"}</td>
                    <td data-label={t("scp.audit.result")}>
                      <ScpStatusPill value={entry.result} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav className={styles.filters} aria-label={t("scp.common.pagination")}>
            <Button
              variant="secondary"
              size="sm"
              disabled={page.page === 0}
              onClick={() => setPageIndex(page.page - 1)}
            >
              {t("scp.common.previous")}
            </Button>
            <span className={styles.appCardMeta}>
              {t("scp.common.pageOf", {
                page: page.page + 1,
                total: Math.max(page.totalPages, 1),
              })}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={page.page + 1 >= page.totalPages}
              onClick={() => setPageIndex(page.page + 1)}
            >
              {t("scp.common.next")}
            </Button>
          </nav>
        </div>
      )}
    </ScpPage>
  );
}
