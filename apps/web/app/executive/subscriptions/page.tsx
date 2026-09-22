"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { scpApi, type PageResponse, type SubscriptionRow } from "@/lib/api/scp-api";
import { executiveApi, type SaasPlan } from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpNotice,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import { useScpAccess } from "../_components/ScpAccess";
import { scpErrorMessage } from "../_components/scp-errors";
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
  const { has } = useScpAccess();
  const canManage = has("EXECUTIVE_MANAGE");
  const router = useRouter();
  const searchParams = useSearchParams();
  const tenantIdParam = searchParams.get("tenantId") ?? "";
  const intentParam = searchParams.get("intent") ?? "";

  const [page, setPage] = useState<PageResponse<SubscriptionRow> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [plans, setPlans] = useState<SaasPlan[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [billingCycle, setBillingCycle] = useState<"MONTHLY" | "ANNUAL">("MONTHLY");
  const [seatQuantity, setSeatQuantity] = useState(1);
  const [creating, setCreating] = useState(false);
  const [notice, setNotice] = useState("");
  const requestGeneration = useRef(0);

  const load = useCallback(async () => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    setError("");
    try {
      const result = await scpApi.subscriptions({
        tenantId: tenantIdParam || undefined,
        status: status || undefined,
        search: search || undefined,
        page: pageIndex,
        size: 20,
        sort: "created_at",
        direction: "DESC",
      });
      if (generation === requestGeneration.current) {
        setPage(result);
      }
    } catch (reason) {
      if (generation === requestGeneration.current) {
        setError(scpErrorMessage(reason));
      }
    } finally {
      if (generation === requestGeneration.current) {
        setLoading(false);
      }
    }
  }, [tenantIdParam, status, search, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!canManage || !tenantIdParam || intentParam !== "upgrade") {
      setPlans([]);
      setSelectedPlanId("");
      return;
    }
    let active = true;
    executiveApi.plans()
      .then((result) => {
        if (!active) return;
        const activePlans = result.filter((plan) => plan.status === "ACTIVE");
        setPlans(activePlans);
        setSelectedPlanId((current) =>
          current && activePlans.some((plan) => plan.id === current)
            ? current
            : activePlans[0]?.id ?? "");
      })
      .catch((reason) => {
        if (active) setError(scpErrorMessage(reason));
      });
    return () => {
      active = false;
    };
  }, [canManage, intentParam, tenantIdParam]);

  async function createSubscriptionForTenant() {
    if (!canManage || !tenantIdParam || !selectedPlanId || seatQuantity < 1) return;
    const plan = plans.find((candidate) => candidate.id === selectedPlanId);
    if (!plan) return;
    setCreating(true);
    setError("");
    setNotice("");
    try {
      const created = await executiveApi.createSubscription({
        tenantId: tenantIdParam,
        planId: selectedPlanId,
        billingCycle,
        seatQuantity,
        trialDays: plan.trialDays,
      });
      try {
        await scpApi.provision(created.id);
        setNotice(t("scp.subscriptions.createdAndProvisioned"));
      } catch (provisionReason) {
        setNotice(t("scp.subscriptions.createdProvisionPending"));
        setError(scpErrorMessage(provisionReason));
      }
      await load();
      router.push(`/executive/subscriptions/${created.id}?tenantId=${encodeURIComponent(tenantIdParam)}`);
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setCreating(false);
    }
  }

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

      {notice ? <ScpNotice>{notice}</ScpNotice> : null}
      {error ? <ScpError message={error} onRetry={load} /> : null}

      {canManage && tenantIdParam && intentParam === "upgrade" && page?.content.length === 0 ? (
        <section className={styles.panel} aria-labelledby="scp-create-subscription-heading">
          <h2 id="scp-create-subscription-heading" className={styles.pageSubtitle}>
            {t("scp.subscriptions.createForTenant")}
          </h2>
          <p className={styles.appCardMeta}>{t("scp.subscriptions.createForTenantHelp")}</p>
          <div className={styles.filters}>
            <label>
              <span>{t("scp.subscriptions.plan")}</span>
              <select
                aria-label={t("scp.subscriptions.plan")}
                value={selectedPlanId}
                disabled={creating}
                onChange={(event) => setSelectedPlanId(event.target.value)}
              >
                <option value="">{t("scp.subscriptions.selectPlan")}</option>
                {plans.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.name} ({plan.code})
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>{t("scp.subscriptions.cycle")}</span>
              <select
                aria-label={t("scp.subscriptions.cycle")}
                value={billingCycle}
                disabled={creating}
                onChange={(event) => setBillingCycle(event.target.value as "MONTHLY" | "ANNUAL")}
              >
                <option value="MONTHLY">{t("scp.subscriptions.monthly")}</option>
                <option value="ANNUAL">{t("scp.subscriptions.annual")}</option>
              </select>
            </label>
            <Input
              type="number"
              min={1}
              label={t("scp.detail.seats")}
              aria-label={t("scp.detail.seats")}
              value={String(seatQuantity)}
              disabled={creating}
              onChange={(event) => {
                const value = Number.parseInt(event.target.value, 10);
                setSeatQuantity(Number.isFinite(value) && value > 0 ? value : 1);
              }}
            />
            <Button
              type="button"
              variant="primary"
              size="sm"
              loading={creating}
              disabled={creating || !selectedPlanId || plans.length === 0}
              onClick={() => void createSubscriptionForTenant()}
            >
              {t("scp.subscriptions.createAndProvision")}
            </Button>
          </div>
        </section>
      ) : null}

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
                {page.content.map((subscription) => {
                  const detailParams = new URLSearchParams();
                  if (tenantIdParam) detailParams.set("tenantId", tenantIdParam);
                  if (intentParam === "upgrade") detailParams.set("intent", "upgrade");
                  const detailQuery = detailParams.toString();
                  const detailHref = `/executive/subscriptions/${subscription.id}${detailQuery ? `?${detailQuery}` : ""}`;
                  return (
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
                        <Link href={detailHref} onClick={() => router.prefetch(detailHref)}>
                          {intentParam === "upgrade" ? t("scp.subscriptions.continueUpgrade") : t("scp.subscriptions.details")}
                        </Link>
                      </td>
                    </tr>
                  );
                })}
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
  const { t } = useI18n();
  const { day } = useScpFormat();

  if (!value.currentPeriodEnd) {
    return <span>—</span>;
  }

  const periodEnd = day(value.currentPeriodEnd);
  return value.cancelAtPeriodEnd ? (
    <span className={styles.appCardMeta}>
      {t("scp.subscriptions.cancelsAtPeriodEnd", { date: periodEnd })}
    </span>
  ) : (
    <span>{periodEnd}</span>
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
