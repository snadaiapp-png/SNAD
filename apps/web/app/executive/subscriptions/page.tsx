"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { scpApi, type PageResponse, type SubscriptionRow } from "@/lib/api/scp-api";
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
 * Subscription grid — server-side filters (tenant, status, country, search,
 * trials), sorting and pagination. Rows link to the full detail page.
 */
export default function SubscriptionsPage() {
  return (
    <Suspense fallback={<ScpSkeleton lines={8} />}>
      <SubscriptionsContent />
    </Suspense>
  );
}

function SubscriptionsContent() {
  const { t } = useI18n();
  const { money } = useScpFormat();
  const router = useRouter();
  const searchParams = useSearchParams();
  const tenantIdParam = searchParams.get("tenantId") ?? "";

  const [page, setPage] = useState<PageResponse<SubscriptionRow> | null>(null);
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
        await scpApi.subscriptions({
          tenantId: tenantIdParam || undefined,
          status: status || undefined,
          search: search || undefined,
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
  }, [tenantIdParam, status, search, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && !page) {
    return (
      <ScpPage title={t("scp.subscriptions.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.subscriptions.title")} subtitle={t("scp.subscriptions.subtitle")}>
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
          placeholder={t("scp.subscriptions.searchPlaceholder")}
          onChange={(event) => setSearch(event.target.value)}
          aria-label={t("scp.subscriptions.searchPlaceholder")}
        />
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPageIndex(0);
          }}
          aria-label={t("scp.subscriptions.statusFilter")}
        >
          <option value="">{t("scp.filters.allStatuses")}</option>
          {[
            "TRIAL", "TRIALING", "ACTIVE", "PAST_DUE", "GRACE_PERIOD",
            "PAUSED", "SUSPENDED", "CANCELLED", "EXPIRED", "TERMINATED",
          ].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
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
              <caption>{t("scp.subscriptions.count", { count: page.totalElements })}</caption>
              <thead>
                <tr>
                  <th scope="col">{t("scp.subscriptions.tenant")}</th>
                  <th scope="col">{t("scp.subscriptions.plan")}</th>
                  <th scope="col">{t("scp.subscriptions.items")}</th>
                  <th scope="col">{t("scp.subscriptions.cycle")}</th>
                  <th scope="col">{t("scp.subscriptions.amount")}</th>
                  <th scope="col">{t("scp.subscriptions.status")}</th>
                  <th scope="col">{t("scp.subscriptions.nextBilling")}</th>
                  <th scope="col">{t("scp.common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((subscription) => (
                  <tr key={subscription.id}>
                    <td data-label={t("scp.subscriptions.tenant")}>{subscription.tenantName}</td>
                    <td data-label={t("scp.subscriptions.plan")}>
                      {subscription.planName || "—"}
                      {subscription.planVersion ? (
                        <span className={styles.appCardMeta}> · {subscription.planVersion}</span>
                      ) : null}
                    </td>
                    <td data-label={t("scp.subscriptions.items")}>{subscription.itemCount}</td>
                    <td data-label={t("scp.subscriptions.cycle")}>{subscription.billingCycle}</td>
                    <td data-label={t("scp.subscriptions.amount")}>
                      {money(subscription.monthlyPriceMinor, subscription.currencyCode)}
                    </td>
                    <td data-label={t("scp.subscriptions.status")}>
                      <ScpStatusPill value={subscription.status} />
                      {subscription.trial ? (
                        <span className={styles.appCardMeta}> · {t("scp.subscriptions.trial")}</span>
                      ) : null}
                    </td>
                    <td data-label={t("scp.subscriptions.nextBilling")}>
                      <NextBilling value={subscription} />
                    </td>
                    <td data-label={t("scp.common.actions")}>
                      <Link
                        href={`/executive/subscriptions/${subscription.id}`}
                        onClick={() => router.prefetch(`/executive/subscriptions/${subscription.id}`)}
                      >
                        {t("scp.subscriptions.details")}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <GridPagination page={page} onPage={setPageIndex} />
        </div>
      ) : null}
    </ScpPage>
  );
}

function NextBilling({ value }: { value: SubscriptionRow }) {
  // period end lives on the detail view; the grid keeps the renewal contract light
  return value.cancelAtPeriodEnd ? (
    <span className={styles.appCardMeta}>{value.status}</span>
  ) : (
    <span>—</span>
  );
}

function GridPagination({
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
