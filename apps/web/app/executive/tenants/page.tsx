"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { scpApi, type PageResponse, type TenantRow } from "@/lib/api/scp-api";
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
 * Tenant directory — server-side search, filters, sorting and pagination.
 * The code column renders the tenant's subdomain (its real code), fixing the
 * legacy bug that rendered the tenant name in both code and name columns.
 */
export default function TenantsPage() {
  const { t } = useI18n();
  const { day } = useScpFormat();
  const [page, setPage] = useState<PageResponse<TenantRow> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [pageIndex, setPageIndex] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setPage(
        await scpApi.tenants({
          search: search || undefined,
          status: status || undefined,
          page: pageIndex,
          size: 20,
          sort: "name",
          direction: "ASC",
        }),
      );
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, [search, status, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && !page) {
    return (
      <ScpPage title={t("scp.tenants.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.tenants.title")} subtitle={t("scp.tenants.subtitle")}>
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
          value={search}
          placeholder={t("scp.tenants.searchPlaceholder")}
          onChange={(event) => setSearch(event.target.value)}
          aria-label={t("scp.tenants.searchPlaceholder")}
        />
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPageIndex(0);
          }}
          aria-label={t("scp.tenants.statusFilter")}
        >
          <option value="">{t("scp.filters.allStatuses")}</option>
          {["PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED"].map(
            (value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ),
          )}
        </select>
        <Button type="submit" variant="primary" size="sm">
          {t("scp.filters.apply")}
        </Button>
      </form>

      {error ? <ScpError message={error} onRetry={load} /> : null}

      {page && page.content.length === 0 ? (
        <ScpEmpty message={t("scp.state.empty")} />
      ) : page ? (
        <div className={styles.panel}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>{t("scp.tenants.count", { count: page.totalElements })}</caption>
              <thead>
                <tr>
                  <th scope="col">{t("scp.tenants.code")}</th>
                  <th scope="col">{t("scp.tenants.name")}</th>
                  <th scope="col">{t("scp.tenants.status")}</th>
                  <th scope="col">{t("scp.tenants.country")}</th>
                  <th scope="col">{t("scp.tenants.subscription")}</th>
                  <th scope="col">{t("scp.tenants.createdAt")}</th>
                  <th scope="col">{t("scp.common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((tenant) => (
                  <tr key={tenant.id}>
                    <td data-label={t("scp.tenants.code")}>{tenant.code || tenant.id}</td>
                    <td data-label={t("scp.tenants.name")}>{tenant.name}</td>
                    <td data-label={t("scp.tenants.status")}>
                      <ScpStatusPill value={tenant.status} />
                    </td>
                    <td data-label={t("scp.tenants.country")}>{tenant.countryCode || "—"}</td>
                    <td data-label={t("scp.tenants.subscription")}>
                      {tenant.subscriptionStatus ? (
                        <ScpStatusPill value={tenant.subscriptionStatus} />
                      ) : (
                        "—"
                      )}
                    </td>
                    <td data-label={t("scp.tenants.createdAt")}>{day(tenant.createdAt)}</td>
                    <td data-label={t("scp.common.actions")}>
                      <Link href={`/executive/subscriptions?tenantId=${tenant.id}`}>
                        {t("scp.tenants.viewSubscriptions")}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} onPage={setPageIndex} />
        </div>
      ) : null}
    </ScpPage>
  );
}

export function Pagination({
  page,
  onPage,
}: {
  page: PageResponse<unknown>;
  onPage: (index: number) => void;
}) {
  const { t } = useI18n();
  return (
    <nav className={styles.filters} aria-label={t("scp.common.pagination")}>
      <Button
        variant="secondary"
        size="sm"
        disabled={page.page === 0}
        onClick={() => onPage(page.page - 1)}
      >
        {t("scp.common.previous")}
      </Button>
      <span className={styles.appCardMeta}>
        {t("scp.common.pageOf", { page: page.page + 1, total: Math.max(page.totalPages, 1) })}
      </span>
      <Button
        variant="secondary"
        size="sm"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onPage(page.page + 1)}
      >
        {t("scp.common.next")}
      </Button>
    </nav>
  );
}
