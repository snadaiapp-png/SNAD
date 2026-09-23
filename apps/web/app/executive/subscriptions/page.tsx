"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { scpApi, type PageResponse, type SubscriptionRow } from "@/lib/api/scp-api";
import { executiveApi, type ManagedTenant, type SaasPlan } from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpAccess } from "../_components/ScpAccess";
import { useScpFormat } from "../_components/format";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

const ACTIVATABLE_TENANT_STATUSES = new Set([
  "PENDING",
  "TRIAL",
  "PAST_DUE",
  "SUSPENDED",
  "CANCELLED",
]);

/**
 * Subscription grid — server-side filters (tenant, status, country, search,
 * trials), sorting and pagination. Rows link to the full detail page.
 *
 * When a tenant-management `intent=upgrade` deep-link targets a tenant with no
 * subscription yet, this page owns the missing first-subscription orchestration:
 * choose an ACTIVE catalog plan, create the subscription through the canonical
 * Executive command, provision it, then activate the tenant directory state.
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
  const requestGeneration = useRef(0);

  const [upgradeTenant, setUpgradeTenant] = useState<ManagedTenant | null>(null);
  const [upgradePlans, setUpgradePlans] = useState<SaasPlan[]>([]);
  const [upgradePlanId, setUpgradePlanId] = useState("");
  const [upgradeBillingCycle, setUpgradeBillingCycle] = useState<"MONTHLY" | "ANNUAL">("MONTHLY");
  const [upgradeSeatQuantity, setUpgradeSeatQuantity] = useState(1);
  const [upgradeLoading, setUpgradeLoading] = useState(false);
  const [upgradeBusy, setUpgradeBusy] = useState(false);
  const upgradeRequestGeneration = useRef(0);

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

  const emptyUpgrade =
    intentParam === "upgrade" &&
    tenantIdParam.length > 0 &&
    page !== null &&
    page.content.length === 0;

  const loadUpgradeMetadata = useCallback(async () => {
    if (!emptyUpgrade || !canManage) return;
    const generation = ++upgradeRequestGeneration.current;
    setUpgradeLoading(true);
    setError("");
    try {
      const [tenant, plans] = await Promise.all([
        executiveApi.tenant(tenantIdParam),
        executiveApi.plans(),
      ]);
      if (generation !== upgradeRequestGeneration.current) return;
      const activePlans = plans.filter((plan) => plan.status === "ACTIVE");
      setUpgradeTenant(tenant);
      setUpgradePlans(activePlans);
      setUpgradePlanId((current) =>
        current && activePlans.some((plan) => plan.id === current)
          ? current
          : activePlans[0]?.id ?? "",
      );
    } catch (reason) {
      if (generation === upgradeRequestGeneration.current) {
        setError(scpErrorMessage(reason));
      }
    } finally {
      if (generation === upgradeRequestGeneration.current) {
        setUpgradeLoading(false);
      }
    }
  }, [canManage, emptyUpgrade, tenantIdParam]);

  useEffect(() => {
    void loadUpgradeMetadata();
  }, [loadUpgradeMetadata]);

  const selectedUpgradePlan = upgradePlans.find((plan) => plan.id === upgradePlanId) ?? null;
  const validUpgradeSeats =
    Number.isInteger(upgradeSeatQuantity) &&
    upgradeSeatQuantity >= 1 &&
    (selectedUpgradePlan?.maxUsers == null ||
      selectedUpgradePlan.maxUsers <= 0 ||
      upgradeSeatQuantity <= selectedUpgradePlan.maxUsers);

  async function startUpgrade() {
    if (
      !canManage ||
      !upgradeTenant ||
      !selectedUpgradePlan ||
      !validUpgradeSeats ||
      upgradeBusy
    ) {
      return;
    }

    if (
      upgradeTenant.status !== "ACTIVE" &&
      !ACTIVATABLE_TENANT_STATUSES.has(upgradeTenant.status)
    ) {
      setError(scpErrorMessage(new Error("Tenant status cannot be activated by subscription upgrade")));
      return;
    }

    setUpgradeBusy(true);
    setError("");
    try {
      const created = await executiveApi.createSubscription({
        tenantId: upgradeTenant.id,
        planId: selectedUpgradePlan.id,
        billingCycle: upgradeBillingCycle,
        seatQuantity: upgradeSeatQuantity,
        // Explicit operator upgrade is commercial activation, not an implicit trial.
        trialDays: 0,
      });

      const provisioned = await scpApi.provision(created.id);
      if (provisioned.status !== "SUCCEEDED") {
        throw new Error("Subscription provisioning did not succeed");
      }

      if (upgradeTenant.status !== "ACTIVE") {
        await executiveApi.changeTenantStatus(
          upgradeTenant.id,
          "ACTIVE",
          "Subscription upgrade activated",
        );
      }

      router.push(
        `/executive/subscriptions/${created.id}?tenantId=${encodeURIComponent(upgradeTenant.id)}&intent=upgrade`,
      );
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setUpgradeBusy(false);
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

      {error ? <ScpError message={error} onRetry={emptyUpgrade ? loadUpgradeMetadata : load} /> : null}

      {emptyUpgrade && canManage ? (
        upgradeLoading || !upgradeTenant ? (
          <ScpSkeleton lines={5} />
        ) : upgradePlans.length === 0 ? (
          <ScpEmpty message={t("scp.state.empty")} />
        ) : (
          <section className={styles.panel} aria-labelledby="scp-subscription-upgrade-heading">
            <h2 id="scp-subscription-upgrade-heading" className={styles.pageSubtitle}>
              {t("scp.tenants.upgrade")}
            </h2>
            <p className={styles.appCardMeta}>
              {upgradeTenant.name} · <ScpStatusPill value={upgradeTenant.status} />
            </p>
            <div className={styles.filters}>
              <label className={styles.appCardMeta}>
                <span>{t("scp.detail.targetPlan")}</span>
                <select
                  value={upgradePlanId}
                  onChange={(event) => setUpgradePlanId(event.target.value)}
                  aria-label={t("scp.detail.targetPlan")}
                  disabled={upgradeBusy}
                >
                  {upgradePlans.map((plan) => (
                    <option key={plan.id} value={plan.id}>
                      {plan.name} ({plan.code})
                    </option>
                  ))}
                </select>
              </label>
              <label className={styles.appCardMeta}>
                <span>{t("scp.subscriptions.cycle")}</span>
                <select
                  value={upgradeBillingCycle}
                  onChange={(event) =>
                    setUpgradeBillingCycle(event.target.value as "MONTHLY" | "ANNUAL")
                  }
                  aria-label={t("scp.subscriptions.cycle")}
                  disabled={upgradeBusy}
                >
                  <option value="MONTHLY">MONTHLY</option>
                  <option value="ANNUAL">ANNUAL</option>
                </select>
              </label>
              <Input
                type="number"
                min={1}
                max={selectedUpgradePlan?.maxUsers && selectedUpgradePlan.maxUsers > 0
                  ? selectedUpgradePlan.maxUsers
                  : undefined}
                value={String(upgradeSeatQuantity)}
                label={t("scp.detail.seats")}
                aria-label={t("scp.detail.seats")}
                onChange={(event) => setUpgradeSeatQuantity(Number(event.target.value))}
                disabled={upgradeBusy}
              />
              <Button
                type="button"
                variant="primary"
                size="sm"
                loading={upgradeBusy}
                disabled={!upgradePlanId || !validUpgradeSeats}
                onClick={() => void startUpgrade()}
              >
                {t("scp.tenants.upgrade")}
              </Button>
            </div>
          </section>
        )
      ) : page && page.content.length === 0 ? (
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
