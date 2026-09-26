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
  ScpNotice,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpAccess } from "../_components/ScpAccess";
import { useScpFormat } from "../_components/format";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

type RecurringSubscriptionRow = SubscriptionRow & {
  recurringAmountMinor?: number | null;
  monthlyEquivalentMinor?: number | null;
};

type ContinuationIntent = "create" | "create-successor" | "resume";

const ACTIVATABLE_TENANT_STATUSES = new Set([
  "PENDING",
  "TRIAL",
  "PAST_DUE",
  "SUSPENDED",
  "CANCELLED",
]);

function continuationIntent(value: string): ContinuationIntent | null {
  return value === "create" || value === "create-successor" || value === "resume" ? value : null;
}

/**
 * Subscription grid plus governed commercial continuation.
 *
 * CREATE / CREATE_SUCCESSOR / RESUME are driven by backend-derived commercial
 * actions. `intent=upgrade` is also supported for the legacy tenant-management
 * deep link: when that tenant has no subscription yet, this page performs the
 * explicit first-subscription orchestration using an existing ACTIVE plan,
 * provisions the created subscription, activates an eligible tenant directory
 * state, then opens subscription detail. Unknown intents expose no mutation.
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
  const governedIntent = continuationIntent(intentParam);

  const [page, setPage] = useState<PageResponse<SubscriptionRow> | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [plans, setPlans] = useState<SaasPlan[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [billingCycle, setBillingCycle] = useState<"MONTHLY" | "ANNUAL">("MONTHLY");
  const [seatQuantity, setSeatQuantity] = useState(1);
  const [commercialBusy, setCommercialBusy] = useState(false);
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
      if (generation === requestGeneration.current) setPage(result);
    } catch (reason) {
      if (generation === requestGeneration.current) setError(scpErrorMessage(reason));
    } finally {
      if (generation === requestGeneration.current) setLoading(false);
    }
  }, [tenantIdParam, status, search, pageIndex]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!tenantIdParam || (governedIntent !== "create" && governedIntent !== "create-successor")) {
      setPlans([]);
      setSelectedPlanId("");
      return;
    }
    let cancelled = false;
    void executiveApi.plans()
      .then((catalog) => {
        if (cancelled) return;
        setPlans(catalog.filter((plan) => plan.status === "ACTIVE"));
      })
      .catch((reason) => {
        if (!cancelled) setError(scpErrorMessage(reason));
      });
    return () => { cancelled = true; };
  }, [governedIntent, tenantIdParam]);

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
      const [tenant, catalog] = await Promise.all([
        executiveApi.tenant(tenantIdParam),
        executiveApi.plans(),
      ]);
      if (generation !== upgradeRequestGeneration.current) return;
      const activePlans = catalog.filter((plan) => plan.status === "ACTIVE");
      // Fail-closed plan-version currency: an operator may only target a plan
      // whose canonical backend version is still ACTIVE. Retired plans and
      // active plans without a live version are never offered for upgrade.
      const currencyEligible = await Promise.all(
        activePlans.map(async (plan) => {
          const planVersions = await scpApi.planVersions(plan.id);
          return planVersions.some((version) => version.status === "ACTIVE") ? plan : null;
        }),
      );
      if (generation !== upgradeRequestGeneration.current) return;
      const selectablePlans = currencyEligible.filter(
        (plan): plan is SaasPlan => plan !== null,
      );
      setUpgradeTenant(tenant);
      setUpgradePlans(selectablePlans);
      setUpgradePlanId((current) =>
        current && selectablePlans.some((plan) => plan.id === current)
          ? current
          : selectablePlans[0]?.id ?? "",
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

  useEffect(() => { void loadUpgradeMetadata(); }, [loadUpgradeMetadata]);

  const selectedUpgradePlan = upgradePlans.find((plan) => plan.id === upgradePlanId) ?? null;
  const validUpgradeSeats =
    Number.isInteger(upgradeSeatQuantity) &&
    upgradeSeatQuantity >= 1 &&
    (selectedUpgradePlan?.maxUsers == null ||
      selectedUpgradePlan.maxUsers <= 0 ||
      upgradeSeatQuantity <= selectedUpgradePlan.maxUsers);

  async function startUpgrade() {
    if (!canManage || !upgradeTenant || !selectedUpgradePlan || !validUpgradeSeats || upgradeBusy) return;

    if (upgradeTenant.status !== "ACTIVE" && !ACTIVATABLE_TENANT_STATUSES.has(upgradeTenant.status)) {
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
        // Operator-requested upgrade is commercial activation, never an implicit trial.
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

  async function createSubscription(successor: boolean) {
    if (!tenantIdParam || !selectedPlanId) return;
    const plan = plans.find((candidate) => candidate.id === selectedPlanId);
    if (!plan) return;
    setCommercialBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.createSubscription({
        tenantId: tenantIdParam,
        planId: plan.id,
        billingCycle,
        seatQuantity,
        trialDays: successor ? 0 : plan.trialDays,
      });
      setNotice(successor
        ? t("scp.subscriptions.createSuccessor.success")
        : t("scp.subscriptions.create.success"));
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setCommercialBusy(false);
    }
  }

  async function resumeSubscription(subscriptionId: string) {
    setCommercialBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.resumeSubscription(subscriptionId);
      setNotice(t("scp.subscriptions.resume.success"));
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setCommercialBusy(false);
    }
  }

  const cancelledRows = page?.content.filter((subscription) => subscription.status === "CANCELLED") ?? [];
  const resumableSubscription = governedIntent === "resume" && cancelledRows.length === 1
    ? cancelledRows[0]
    : null;

  if (loading && !page) {
    return <ScpPage title={t("scp.subscriptions.title")}><ScpSkeleton lines={8} /></ScpPage>;
  }

  return (
    <ScpPage title={t("scp.subscriptions.title")} subtitle={t("scp.subscriptions.subtitle")}>
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
                    <option key={plan.id} value={plan.id}>{plan.name} ({plan.code})</option>
                  ))}
                </select>
              </label>
              <label className={styles.appCardMeta}>
                <span>{t("scp.subscriptions.cycle")}</span>
                <select
                  value={upgradeBillingCycle}
                  onChange={(event) => setUpgradeBillingCycle(event.target.value as "MONTHLY" | "ANNUAL")}
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
      ) : null}

      {(governedIntent === "create" || governedIntent === "create-successor") && tenantIdParam ? (
        <section className={styles.panel} aria-label={t("scp.subscriptions.create.section")}>
          <div className={styles.filters}>
            <select
              aria-label={t("scp.subscriptions.create.plan")}
              value={selectedPlanId}
              onChange={(event) => setSelectedPlanId(event.target.value)}
            >
              <option value="">{t("scp.subscriptions.create.selectPlan")}</option>
              {plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name}</option>)}
            </select>
            <select
              aria-label={t("scp.subscriptions.create.billingCycle")}
              value={billingCycle}
              onChange={(event) => setBillingCycle(event.target.value as "MONTHLY" | "ANNUAL")}
            >
              <option value="MONTHLY">MONTHLY</option>
              <option value="ANNUAL">ANNUAL</option>
            </select>
            <Input
              type="number"
              min={1}
              value={seatQuantity}
              aria-label={t("scp.subscriptions.create.seatQuantity")}
              onChange={(event) => setSeatQuantity(Math.max(1, Number(event.target.value) || 1))}
            />
            <Button
              type="button"
              variant="primary"
              disabled={!selectedPlanId || commercialBusy}
              loading={commercialBusy}
              onClick={() => void createSubscription(governedIntent === "create-successor")}
            >
              {governedIntent === "create-successor"
                ? t("scp.subscriptions.createSuccessor.submit")
                : t("scp.subscriptions.create.submit")}
            </Button>
          </div>
        </section>
      ) : null}

      {governedIntent === "resume" && tenantIdParam ? (
        <section className={styles.panel} aria-label={t("scp.subscriptions.resume.section")}>
          {resumableSubscription ? (
            <Button
              type="button"
              variant="primary"
              disabled={commercialBusy}
              loading={commercialBusy}
              onClick={() => void resumeSubscription(resumableSubscription.id)}
            >
              {t("scp.subscriptions.resume.submit")}
            </Button>
          ) : (
            <p className={styles.appCardMeta}>{t("scp.subscriptions.resume.unavailable")}</p>
          )}
        </section>
      ) : null}

      <form className={styles.filters} onSubmit={(event) => { event.preventDefault(); setPageIndex(0); void load(); }}>
        <Input type="search" value={search} placeholder={t("scp.subscriptions.searchPlaceholder")} onChange={(event) => setSearch(event.target.value)} aria-label={t("scp.subscriptions.searchPlaceholder")} />
        <select value={status} onChange={(event) => { setStatus(event.target.value); setPageIndex(0); }} aria-label={t("scp.subscriptions.statusFilter")}>
          <option value="">{t("scp.filters.allStatuses")}</option>
          {["TRIAL", "TRIALING", "ACTIVE", "PAST_DUE", "GRACE_PERIOD", "PAUSED", "SUSPENDED", "CANCELLED", "EXPIRED", "TERMINATED"].map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
        <Button type="submit" variant="primary" size="sm">{t("scp.filters.apply")}</Button>
      </form>

      {notice ? <ScpNotice>{notice}</ScpNotice> : null}
      {error ? <ScpError message={error} onRetry={emptyUpgrade ? loadUpgradeMetadata : load} /> : null}

      {page && page.content.length === 0 && !emptyUpgrade ? <ScpEmpty message={t("scp.state.empty")} /> : page && page.content.length > 0 ? (
        <div className={styles.panel}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>{t("scp.subscriptions.count", { count: page.totalElements })}</caption>
              <thead><tr>
                <th scope="col">{t("scp.subscriptions.tenant")}</th>
                <th scope="col">{t("scp.subscriptions.plan")}</th>
                <th scope="col">{t("scp.subscriptions.items")}</th>
                <th scope="col">{t("scp.subscriptions.cycle")}</th>
                <th scope="col">{t("scp.subscriptions.recurringAmount")}</th>
                <th scope="col">{t("scp.subscriptions.status")}</th>
                <th scope="col">{t("scp.subscriptions.nextBilling")}</th>
                <th scope="col">{t("scp.common.actions")}</th>
              </tr></thead>
              <tbody>
                {page.content.map((rawSubscription) => {
                  const subscription = rawSubscription as RecurringSubscriptionRow;
                  const detailParams = new URLSearchParams();
                  if (tenantIdParam) detailParams.set("tenantId", tenantIdParam);
                  if (intentParam === "upgrade") detailParams.set("intent", "upgrade");
                  const detailQuery = detailParams.toString();
                  const detailHref = `/executive/subscriptions/${subscription.id}${detailQuery ? `?${detailQuery}` : ""}`;
                  const amountLabel = subscription.billingCycle === "ANNUAL"
                    ? t("scp.subscriptions.annualRecurringAmount")
                    : t("scp.subscriptions.monthlyRecurringAmount");
                  return (
                    <tr key={subscription.id}>
                      <td data-label={t("scp.subscriptions.tenant")}>{subscription.tenantName}</td>
                      <td data-label={t("scp.subscriptions.plan")}>{subscription.planName || "—"}{subscription.planVersion ? <span className={styles.appCardMeta}> · {subscription.planVersion}</span> : null}</td>
                      <td data-label={t("scp.subscriptions.items")}>{subscription.itemCount}</td>
                      <td data-label={t("scp.subscriptions.cycle")}>{subscription.billingCycle}</td>
                      <td data-label={amountLabel}>
                        <span>{money(subscription.recurringAmountMinor ?? null, subscription.currencyCode)}</span>
                        <span className={styles.appCardMeta}> · {amountLabel}</span>
                      </td>
                      <td data-label={t("scp.subscriptions.status")}><ScpStatusPill value={subscription.status} />{subscription.trial ? <span className={styles.appCardMeta}> · {t("scp.subscriptions.trial")}</span> : null}</td>
                      <td data-label={t("scp.subscriptions.nextBilling")}><NextBilling value={subscription} /></td>
                      <td data-label={t("scp.common.actions")}><Link href={detailHref} onClick={() => router.prefetch(detailHref)}>{intentParam === "upgrade" ? t("scp.subscriptions.continueUpgrade") : t("scp.subscriptions.details")}</Link></td>
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
  if (!value.currentPeriodEnd) return <span>—</span>;
  const periodEnd = day(value.currentPeriodEnd);
  return value.cancelAtPeriodEnd
    ? <span className={styles.appCardMeta}>{t("scp.subscriptions.cancelsAtPeriodEnd", { date: periodEnd })}</span>
    : <span>{periodEnd}</span>;
}

function GridPagination({ page, onPage }: { page: PageResponse<unknown>; onPage: (index: number) => void }) {
  const { t } = useI18n();
  return (
    <nav className={styles.filters} aria-label={t("scp.common.pagination")}>
      <Button variant="secondary" size="sm" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>{t("scp.common.previous")}</Button>
      <span className={styles.appCardMeta}>{t("scp.common.pageOf", { page: page.page + 1, total: Math.max(page.totalPages, 1) })}</span>
      <Button variant="secondary" size="sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>{t("scp.common.next")}</Button>
    </nav>
  );
}
