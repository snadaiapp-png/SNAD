"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import {
  scpApi,
  type ChangePreview,
  type CommandResult,
  type PlanVersion,
  type SubscriptionDetail,
  type SubscriptionItem,
  type UsageSnapshot,
} from "@/lib/api/scp-api";
import {
  executiveApi,
  type SaasPlan,
  type SubscriptionOperatingUnit,
  type SubscriptionBillingProfile,
  type SubscriptionUnitApplication,
  type SubscriptionResourceBinding,
  type SubscriptionAvailableResource,
  type ManagedOrganization,
} from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpNotice,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../../_components/ScpStates";
import { useScpFormat } from "../../_components/format";
import { useScpAccess } from "../../_components/ScpAccess";
import { scpErrorMessage } from "../../_components/scp-errors";
import styles from "../../scp.module.css";
import {
  SubscriptionEntitlementsSection,
  type ScpEntitlementRow,
} from "./SubscriptionEntitlementsSection";

/**
 * Subscription detail — overview, items, usage, invoices, changes,
 * provisioning and audit sections, plus lifecycle commands and the
 * preview→confirm plan change flow. Status is never set directly: every
 * mutation goes through backend commands.
 */
export default function SubscriptionDetailPage() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const subscriptionId = params.id;
  const tenantIdParam = searchParams.get("tenantId") ?? "";
  const intentParam = searchParams.get("intent") ?? "";
  const backParams = new URLSearchParams();
  if (tenantIdParam) backParams.set("tenantId", tenantIdParam);
  if (intentParam === "upgrade") backParams.set("intent", "upgrade");
  const backQuery = backParams.toString();
  const subscriptionsHref = `/executive/subscriptions${backQuery ? `?${backQuery}` : ""}`;
  const { t } = useI18n();
  const { money, day, number } = useScpFormat();
  const { has } = useScpAccess();
  // Mutation controls must mirror the backend authority exactly. The command,
  // change-plan and provisioning endpoints are EXECUTIVE_MANAGE-gated; granular
  // mirrors are informative only and must never expose a control that the
  // backend will reject (or hide a control from the canonical owner).
  const canManage = has("EXECUTIVE_MANAGE");
  const canManageLifecycle = canManage;
  const canChangePlan = canManage;

  const [detail, setDetail] = useState<SubscriptionDetail | null>(null);
  const [items, setItems] = useState<SubscriptionItem[] | null>(null);
  const [usage, setUsage] = useState<UsageSnapshot[] | null>(null);
  const [plans, setPlans] = useState<SaasPlan[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [commandReason, setCommandReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [changePlanId, setChangePlanId] = useState("");
  const [changePreview, setChangePreview] = useState<ChangePreview | null>(null);
  const [operatingUnits, setOperatingUnits] = useState<SubscriptionOperatingUnit[]>([]);
  const [unitApplications, setUnitApplications] = useState<SubscriptionUnitApplication[]>([]);
  const [billingProfiles, setBillingProfiles] = useState<SubscriptionBillingProfile[]>([]);
  const [resourceBindings, setResourceBindings] = useState<SubscriptionResourceBinding[]>([]);
  const [availableResources, setAvailableResources] = useState<SubscriptionAvailableResource[]>([]);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState("");
  const [branchName, setBranchName] = useState("");
  const [billingProfileOrganizationId, setBillingProfileOrganizationId] = useState("");
  const [billingProfileName, setBillingProfileName] = useState("");
  const [billingEmail, setBillingEmail] = useState("");
  const [selectedResource, setSelectedResource] = useState("");
  const [tenantOrganizations, setTenantOrganizations] = useState<ManagedOrganization[]>([]);
  const [existingOrganizationId, setExistingOrganizationId] = useState("");

  const loadOperatingGovernance = useCallback(async () => {
    try {
      const [units, profiles, bindings, resources] = await Promise.all([
        executiveApi.operatingUnits(subscriptionId),
        executiveApi.subscriptionBillingProfiles(subscriptionId),
        executiveApi.subscriptionResourceBindings(subscriptionId),
        executiveApi.subscriptionAvailableResources(subscriptionId),
      ]);
      setOperatingUnits(units);
      setBillingProfiles(profiles);
      setResourceBindings(bindings);
      setAvailableResources(resources);
      setSelectedOrganizationId((current) => {
        if (current && units.some((unit) => unit.organizationId === current && unit.status === "ACTIVE")) {
          return current;
        }
        return units.find((unit) => unit.status === "ACTIVE")?.organizationId ?? "";
      });
    } catch (reason) {
      setError(scpErrorMessage(reason));
    }
  }, [subscriptionId]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [detailResult, itemsResult] = await Promise.all([
        scpApi.subscriptionDetail(subscriptionId),
        scpApi.subscriptionItems(subscriptionId, true),
      ]);
      setDetail(detailResult);
      setItems(itemsResult);
      void loadOperatingGovernance();
      const tenantId = String(detailResult.overview.tenantId ?? "");
      if (tenantId) {
        scpApi.usage(tenantId).then(setUsage).catch((reason) => {
          setUsage(null);
          setError(scpErrorMessage(reason));
        });
        executiveApi.organizations(tenantId).then(setTenantOrganizations).catch((reason) => {
          setTenantOrganizations([]);
          setError(scpErrorMessage(reason));
        });
      }
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [loadOperatingGovernance, subscriptionId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!selectedOrganizationId) {
      setUnitApplications([]);
      return;
    }
    let active = true;
    executiveApi
      .operatingUnitApplications(subscriptionId, selectedOrganizationId)
      .then((applications) => {
        if (active) setUnitApplications(applications);
      })
      .catch((reason) => {
        if (active) setError(scpErrorMessage(reason));
      });
    return () => {
      active = false;
    };
  }, [selectedOrganizationId, subscriptionId]);

  useEffect(() => {
    if (!canChangePlan) {
      setPlans(null);
      setChangePlanId("");
      setChangePreview(null);
      return;
    }
    let active = true;
    executiveApi
      .plans()
      .then((result) => {
        if (active) setPlans(result);
      })
      .catch((reason) => {
        if (active) setError(scpErrorMessage(reason));
      });
    return () => {
      active = false;
    };
  }, [canChangePlan]);

  async function runCommand(command: string) {
    setBusy(true);
    setNotice("");
    setError("");
    try {
      if (command === "CANCEL") {
        const cancelled = await executiveApi.cancelSubscription(subscriptionId, {
          immediate: true,
          reason: commandReason || command,
        });
        setNotice(t("scp.detail.commandApplied", {
          command,
          from: String(detail?.overview.status ?? ""),
          to: cancelled.status,
        }));
        await load();
        return;
      }
      if (command === "RENEW") {
        const renewed = await executiveApi.renewSubscription(subscriptionId);
        setNotice(t("scp.detail.commandApplied", {
          command,
          from: String(detail?.overview.status ?? ""),
          to: renewed.status,
        }));
        await load();
        return;
      }
      if (command === "RESUME") {
        const resumed = await executiveApi.resumeSubscription(subscriptionId);
        setNotice(t("scp.detail.commandApplied", {
          command,
          from: String(detail?.overview.status ?? ""),
          to: resumed.status,
        }));
        await load();
        return;
      }
      const result: CommandResult = await scpApi.lifecycleCommand(
        subscriptionId,
        command as Parameters<typeof scpApi.lifecycleCommand>[1],
        commandReason || command,
      );
      setNotice(t("scp.detail.commandApplied", { command: result.command, from: result.fromStatus, to: result.toStatus }));
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function createAndBindBranch() {
    const tenantId = String(detail?.overview.tenantId ?? "");
    if (!tenantId || !branchName.trim()) return;
    setBusy(true);
    setError("");
    setNotice("");
    let createdOrganizationId = "";
    try {
      const organization = await executiveApi.createOrganization(tenantId, {
        name: branchName.trim(),
        unitType: "BRANCH",
      });
      createdOrganizationId = organization.id;
      await executiveApi.bindOperatingUnit(subscriptionId, organization.id, "CONSOLIDATED");
      setBranchName("");
      setSelectedOrganizationId(organization.id);
      setNotice(t("scp.detail.branchCreated"));
      await loadOperatingGovernance();
    } catch (reason) {
      // The UI spans two governed commands. If subscription binding fails
      // after organization creation, archive the just-created branch so the
      // operator is not left with an orphan operating unit.
      if (createdOrganizationId) {
        try {
          await executiveApi.changeOrganizationStatus(
            tenantId,
            createdOrganizationId,
            "ARCHIVED",
            "Rollback failed subscription operating-unit binding",
          );
        } catch {
          // Preserve the authoritative original failure. A rollback failure is
          // still visible in audit/organization state and must not mask it.
        }
      }
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function bindExistingOperatingUnit() {
    if (!existingOrganizationId) return;
    setBusy(true);
    setError("");
    try {
      await executiveApi.bindOperatingUnit(
        subscriptionId,
        existingOrganizationId,
        "CONSOLIDATED",
      );
      setSelectedOrganizationId(existingOrganizationId);
      setExistingOrganizationId("");
      setNotice(t("scp.detail.branchBound"));
      await loadOperatingGovernance();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function updateOperatingUnit(
    organizationId: string,
    billingMode: "CONSOLIDATED" | "SEPARATE",
  ) {
    setBusy(true);
    setError("");
    try {
      await executiveApi.bindOperatingUnit(subscriptionId, organizationId, billingMode);
      setNotice(t("scp.detail.branchBound"));
      await loadOperatingGovernance();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function unbindOperatingUnit(organizationId: string) {
    setBusy(true);
    setError("");
    try {
      await executiveApi.deactivateOperatingUnit(subscriptionId, organizationId);
      setNotice(t("scp.detail.branchUnbound"));
      if (selectedOrganizationId === organizationId) setSelectedOrganizationId("");
      await loadOperatingGovernance();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function toggleUnitApplication(application: SubscriptionUnitApplication) {
    if (!selectedOrganizationId) return;
    setBusy(true);
    setError("");
    try {
      await executiveApi.setOperatingUnitApplication(
        subscriptionId,
        selectedOrganizationId,
        application.applicationId,
        !application.enabled,
      );
      setNotice(t("scp.detail.applicationUpdated"));
      setUnitApplications(await executiveApi.operatingUnitApplications(subscriptionId, selectedOrganizationId));
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function saveBillingProfile() {
    const currencyCode = String(detail?.overview.currencyCode ?? "").trim().toUpperCase();
    if (!billingProfileName.trim() || !currencyCode) return;
    setBusy(true);
    setError("");
    try {
      await executiveApi.upsertSubscriptionBillingProfile(subscriptionId, {
        organizationId: billingProfileOrganizationId || null,
        profileName: billingProfileName.trim(),
        billingEmail: billingEmail.trim() || null,
        currencyCode,
        billingMode: billingProfileOrganizationId ? "SEPARATE" : "CONSOLIDATED",
      });
      setBillingProfileName("");
      setBillingEmail("");
      setNotice(t("scp.detail.billingProfileSaved"));
      await loadOperatingGovernance();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function bindResource() {
    if (!selectedOrganizationId || !selectedResource) return;
    const [resourceType, resourceId] = selectedResource.split(":");
    if ((resourceType !== "WEBSITE" && resourceType !== "STORE") || !resourceId) return;
    setBusy(true);
    setError("");
    try {
      await executiveApi.bindSubscriptionResource(
        subscriptionId,
        selectedOrganizationId,
        resourceType,
        resourceId,
      );
      setSelectedResource("");
      setNotice(t("scp.detail.resourceBound"));
      await loadOperatingGovernance();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function unbindResource(binding: SubscriptionResourceBinding) {
    setBusy(true);
    setError("");
    try {
      await executiveApi.unbindSubscriptionResource(
        subscriptionId,
        binding.resourceType,
        binding.resourceId,
      );
      setNotice(t("scp.detail.resourceUnbound"));
      await loadOperatingGovernance();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function previewPlanChange() {
    if (!changePlanId) return;
    setBusy(true);
    setNotice("");
    try {
      const versions: PlanVersion[] = await scpApi.planVersions(changePlanId);
      const active = versions.find((version) => version.status === "ACTIVE");
      if (!active) {
        setNotice(t("scp.detail.noActiveVersionForPlan"));
        return;
      }
      setChangePreview(await scpApi.previewChange(subscriptionId, active.id));
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function confirmPlanChange() {
    if (!changePreview) return;
    setBusy(true);
    setError("");
    try {
      await scpApi.executeChange(
        subscriptionId,
        changePreview.targetPlanVersionId,
        "Executive plan change",
      );
      setChangePreview(null);
      setNotice(t("scp.detail.changeExecuted"));
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <ScpPage title={t("scp.detail.title")}>
        <ScpSkeleton lines={10} />
      </ScpPage>
    );
  }

  const overview = detail?.overview ?? {};

  return (
    <ScpPage
      title={`${t("scp.detail.title")} — ${String(overview.tenantName ?? overview.tenantCode ?? subscriptionId)}`}
      subtitle={String(overview.planName ?? "")}
    >
      <Link href={subscriptionsHref} className={styles.appCardMeta}>
        ← {t("scp.subscriptions.title")}
      </Link>

      {notice ? <ScpNotice>{notice}</ScpNotice> : null}
      {error ? <ScpError message={error} onRetry={load} /> : null}

      <div className={styles.metrics}>
        <div className={styles.metricCard}>
          <ScpStatusPill value={String(overview.status ?? "—")} />
          <span className={styles.metricLabel}>{t("scp.subscriptions.status")}</span>
        </div>
        <div className={styles.metricCard}>
          <span className={styles.metricValue}>{String(overview.billingCycle ?? "—")}</span>
          <span className={styles.metricLabel}>{t("scp.subscriptions.cycle")}</span>
        </div>
        <div className={styles.metricCard}>
          <span className={styles.metricValue}>{number(Number(overview.seatQuantity ?? 0))}</span>
          <span className={styles.metricLabel}>{t("scp.detail.seats")}</span>
        </div>
        <div className={styles.metricCard}>
          <span className={styles.metricValue}>{day(String(overview.currentPeriodEnd ?? ""))}</span>
          <span className={styles.metricLabel}>{t("scp.detail.periodEnd")}</span>
        </div>
      </div>

      <section className={styles.panel} aria-labelledby="scp-items-heading">
        <h2 id="scp-items-heading" className={styles.pageSubtitle}>{t("scp.detail.items")}</h2>
        {items && items.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.detail.itemType")}</th>
                  <th scope="col">{t("scp.detail.itemName")}</th>
                  <th scope="col">{t("scp.detail.quantity")}</th>
                  <th scope="col">{t("scp.detail.amount")}</th>
                  <th scope="col">{t("scp.subscriptions.status")}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.itemType}</td>
                    <td>{item.nameSnapshot ?? item.id}</td>
                    <td>{item.quantity}</td>
                    <td>{money(item.unitAmountMinor, item.currencyCode)}</td>
                    <td><ScpStatusPill value={item.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      {detail ? (
        <SubscriptionEntitlementsSection
          entitlements={(detail.entitlements ?? []) as unknown as ScpEntitlementRow[]}
        />
      ) : null}

      {usage && usage.length > 0 ? (
        <section className={styles.panel} aria-labelledby="scp-usage-heading">
          <h2 id="scp-usage-heading" className={styles.pageSubtitle}>{t("scp.detail.usage")}</h2>
          <div className={styles.metrics}>
            {usage.map((snapshot) => (
              <div key={snapshot.metricCode} className={styles.metricCard}>
                <span className={styles.metricValue}>{number(snapshot.current)}</span>
                <span className={styles.metricLabel}>
                  {snapshot.metricCode}
                  {snapshot.limit !== null ? ` / ${number(snapshot.limit)}` : ""}
                </span>
                {snapshot.percent !== null ? (
                  <div
                    className={styles.usageBar}
                    role="progressbar"
                    aria-valuenow={snapshot.percent}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={snapshot.metricCode}
                  >
                    <div
                      className={styles.usageBarFill}
                      data-warning={snapshot.warning}
                      style={{ inlineSize: `${Math.min(snapshot.percent, 100)}%` }}
                    />
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <section className={styles.panel} aria-labelledby="scp-invoices-heading">
        <h2 id="scp-invoices-heading" className={styles.pageSubtitle}>{t("scp.detail.invoices")}</h2>
        {detail && detail.invoices.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.billing.number")}</th>
                  <th scope="col">{t("scp.billing.status")}</th>
                  <th scope="col">{t("scp.billing.total")}</th>
                  <th scope="col">{t("scp.billing.dueAt")}</th>
                </tr>
              </thead>
              <tbody>
                {detail.invoices.map((invoice) => (
                  <tr key={String(invoice.id)}>
                    <td>{String(invoice.invoiceNumber)}</td>
                    <td><ScpStatusPill value={String(invoice.status)} /></td>
                    <td>{money(Number(invoice.totalMinor), String(invoice.currencyCode))}</td>
                    <td>{day(String(invoice.dueAt))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      <section className={styles.panel} aria-labelledby="scp-changes-heading">
        <h2 id="scp-changes-heading" className={styles.pageSubtitle}>{t("scp.detail.changes")}</h2>
        {detail && detail.changes.length > 0 ? (
          <ul>
            {detail.changes.slice(0, 10).map((change, index) => (
              <li key={index} className={styles.appCardMeta}>
                {day(String(change.createdAt ?? ""))} — {String(change.action)}{" "}
                {change.fromStatus ? `${String(change.fromStatus)} → ${String(change.toStatus)}` : ""}
              </li>
            ))}
          </ul>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      <section className={styles.panel} aria-labelledby="scp-provisioning-heading">
        <h2 id="scp-provisioning-heading" className={styles.pageSubtitle}>{t("scp.detail.provisioning")}</h2>
        {detail && detail.provisioningJobs.length > 0 ? (
          <ul>
            {detail.provisioningJobs.map((job) => (
              <li key={String(job.id)} className={styles.appCardMeta}>
                <ScpStatusPill value={String(job.status)} /> {String(job.action)} ·{" "}
                {t("scp.provisioning.attempts")}: {number(Number(job.attempts))}
              </li>
            ))}
          </ul>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>

      <section className={styles.panel} aria-labelledby="scp-operating-units-heading">
        <h2 id="scp-operating-units-heading" className={styles.pageSubtitle}>
          {t("scp.detail.operatingUnits")}
        </h2>
        <p className={styles.appCardMeta}>{t("scp.detail.operatingUnitsHelp")}</p>

        {canManage ? (
          <div className={styles.filters}>
            <Input
              label={t("scp.detail.branchName")}
              aria-label={t("scp.detail.branchName")}
              value={branchName}
              maxLength={200}
              onChange={(event) => setBranchName(event.target.value)}
            />
            <Button
              variant="primary"
              size="sm"
              disabled={busy || !branchName.trim()}
              onClick={() => void createAndBindBranch()}
            >
              {t("scp.detail.createBranch")}
            </Button>
            <select
              aria-label={t("scp.detail.existingBranch")}
              value={existingOrganizationId}
              onChange={(event) => setExistingOrganizationId(event.target.value)}
            >
              <option value="">{t("scp.detail.existingBranch")}</option>
              {tenantOrganizations
                .filter((organization) =>
                  organization.status === "ACTIVE"
                  && organization.unitType === "BRANCH"
                  && !operatingUnits.some((unit) =>
                    unit.organizationId === organization.id && unit.status === "ACTIVE"))
                .map((organization) => (
                  <option key={organization.id} value={organization.id}>
                    {organization.name}
                  </option>
                ))}
            </select>
            <Button
              variant="secondary"
              size="sm"
              disabled={busy || !existingOrganizationId}
              onClick={() => void bindExistingOperatingUnit()}
            >
              {t("scp.detail.bindExistingBranch")}
            </Button>
          </div>
        ) : null}

        {operatingUnits.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.detail.branchName")}</th>
                  <th scope="col">{t("scp.subscriptions.status")}</th>
                  <th scope="col">{t("scp.detail.billingMode")}</th>
                  <th scope="col">{t("scp.common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {operatingUnits.map((unit) => (
                  <tr key={unit.organizationId}>
                    <td>{unit.organizationName}</td>
                    <td><ScpStatusPill value={unit.status} /></td>
                    <td>
                      {canManage && unit.status === "ACTIVE" ? (
                        <select
                          aria-label={`${t("scp.detail.billingMode")} — ${unit.organizationName}`}
                          value={unit.billingMode}
                          disabled={busy}
                          onChange={(event) => void updateOperatingUnit(
                            unit.organizationId,
                            event.target.value as "CONSOLIDATED" | "SEPARATE",
                          )}
                        >
                          <option value="CONSOLIDATED">{t("scp.detail.consolidated")}</option>
                          <option
                            value="SEPARATE"
                            disabled={!billingProfiles.some((profile) =>
                              profile.organizationId === unit.organizationId
                              && profile.status === "ACTIVE")}
                          >
                            {t("scp.detail.separate")}
                          </option>
                        </select>
                      ) : unit.billingMode}
                    </td>
                    <td>
                      <div className={styles.filters}>
                        {unit.status === "ACTIVE" ? (
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={busy}
                            onClick={() => setSelectedOrganizationId(unit.organizationId)}
                          >
                            {t("scp.detail.selectBranch")}
                          </Button>
                        ) : null}
                        {canManage && unit.status === "ACTIVE" ? (
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={busy}
                            onClick={() => void unbindOperatingUnit(unit.organizationId)}
                          >
                            {t("scp.detail.unbindUnit")}
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}

        {selectedOrganizationId ? (
          <>
            <h3 className={styles.pageSubtitle}>{t("scp.detail.applications")}</h3>
            {unitApplications.length > 0 ? (
              <div className={styles.filters}>
                {unitApplications.map((application) => (
                  <Button
                    key={application.applicationId}
                    variant={application.enabled ? "primary" : "secondary"}
                    size="sm"
                    disabled={busy || !canManage}
                    aria-pressed={application.enabled}
                    aria-label={application.enabled
                      ? `${t("scp.detail.disableApplication")} — ${application.applicationName}`
                      : `${t("scp.detail.enableApplication")} — ${application.applicationName}`}
                    onClick={() => void toggleUnitApplication(application)}
                  >
                    {application.applicationName} ({application.applicationCode})
                  </Button>
                ))}
              </div>
            ) : (
              <ScpEmpty message={t("scp.state.empty")} />
            )}

            <h3 className={styles.pageSubtitle}>{t("scp.detail.resources")}</h3>
            {canManage ? (
              <div className={styles.filters}>
                <select
                  aria-label={t("scp.detail.selectResource")}
                  value={selectedResource}
                  onChange={(event) => setSelectedResource(event.target.value)}
                >
                  <option value="">{t("scp.detail.selectResource")}</option>
                  {availableResources
                    .filter((resource) => !resource.boundOrganizationId
                      || resource.boundOrganizationId === selectedOrganizationId)
                    .map((resource) => (
                      <option
                        key={`${resource.resourceType}:${resource.resourceId}`}
                        value={`${resource.resourceType}:${resource.resourceId}`}
                      >
                        {resource.resourceName} ({resource.resourceType})
                      </option>
                    ))}
                </select>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy || !selectedResource}
                  onClick={() => void bindResource()}
                >
                  {t("scp.detail.assignResource")}
                </Button>
              </div>
            ) : null}
          </>
        ) : null}

        {resourceBindings.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.detail.resource")}</th>
                  <th scope="col">{t("scp.detail.branchName")}</th>
                  <th scope="col">{t("scp.subscriptions.status")}</th>
                  <th scope="col">{t("scp.common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {resourceBindings.map((binding) => {
                  const unit = operatingUnits.find(
                    (candidate) => candidate.organizationId === binding.organizationId,
                  );
                  return (
                    <tr key={`${binding.resourceType}:${binding.resourceId}`}>
                      <td>{binding.resourceName ?? binding.resourceId} ({binding.resourceType})</td>
                      <td>{unit?.organizationName ?? binding.organizationId}</td>
                      <td><ScpStatusPill value={binding.status} /></td>
                      <td>
                        {canManage && binding.status === "ACTIVE" ? (
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={busy}
                            onClick={() => void unbindResource(binding)}
                          >
                            {t("scp.detail.unbindResource")}
                          </Button>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}

        <h3 className={styles.pageSubtitle}>{t("scp.detail.billingProfiles")}</h3>
        {billingProfiles.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">{t("scp.detail.profileName")}</th>
                  <th scope="col">{t("scp.detail.branchName")}</th>
                  <th scope="col">{t("scp.detail.billingMode")}</th>
                  <th scope="col">{t("scp.subscriptions.status")}</th>
                </tr>
              </thead>
              <tbody>
                {billingProfiles.map((profile) => (
                  <tr key={profile.id}>
                    <td>{profile.profileName}</td>
                    <td>{profile.organizationId
                      ? operatingUnits.find((unit) => unit.organizationId === profile.organizationId)?.organizationName
                        ?? profile.organizationId
                      : t("scp.detail.consolidated")}</td>
                    <td>{profile.billingMode}</td>
                    <td><ScpStatusPill value={profile.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {canManage ? (
          <div className={styles.filters}>
            <label>
              <span>{t("scp.detail.billingMode")}</span>
              <select
                aria-label={t("scp.detail.billingMode")}
                value={billingProfileOrganizationId}
                onChange={(event) => setBillingProfileOrganizationId(event.target.value)}
              >
                <option value="">{t("scp.detail.consolidated")}</option>
                {operatingUnits
                  .filter((unit) => unit.status === "ACTIVE")
                  .map((unit) => (
                    <option key={unit.organizationId} value={unit.organizationId}>
                      {unit.organizationName} — {t("scp.detail.separate")}
                    </option>
                  ))}
              </select>
            </label>
            <Input
              label={t("scp.detail.profileName")}
              aria-label={t("scp.detail.profileName")}
              value={billingProfileName}
              maxLength={160}
              onChange={(event) => setBillingProfileName(event.target.value)}
            />
            <Input
              type="email"
              label={t("scp.detail.billingEmail")}
              aria-label={t("scp.detail.billingEmail")}
              value={billingEmail}
              maxLength={255}
              onChange={(event) => setBillingEmail(event.target.value)}
            />
            <Button
              variant="secondary"
              size="sm"
              disabled={busy || !billingProfileName.trim() || !String(overview.currencyCode ?? "")}
              onClick={() => void saveBillingProfile()}
            >
              {t("scp.detail.saveBillingProfile")}
            </Button>
          </div>
        ) : null}
      </section>

      <section className={styles.panel} aria-labelledby="scp-lifecycle-heading">
        <h2 id="scp-lifecycle-heading" className={styles.pageSubtitle}>{t("scp.detail.lifecycleCommands")}</h2>
        <label className={styles.appCardMeta}>
          <span>{t("scp.detail.reason")}</span>
          <Input value={commandReason} onChange={(event) => setCommandReason(event.target.value)} maxLength={200} />
        </label>
        {canManageLifecycle ? (
          <div className={styles.filters}>
            {(detail?.availableActions ?? []).map((command) => (
              <Button
                key={command}
                variant="secondary"
                size="sm"
                disabled={busy}
                onClick={() => void runCommand(command)}
              >
                {t(`scp.detail.lifecycle.${command}`)}
              </Button>
            ))}
          </div>
        ) : null}
      </section>

      {canChangePlan ? (
        <section className={styles.panel} aria-labelledby="scp-change-heading">
          <h2 id="scp-change-heading" className={styles.pageSubtitle}>{t("scp.detail.changePlan")}</h2>
          <div className={styles.filters}>
            <select
              value={changePlanId}
              onChange={(event) => setChangePlanId(event.target.value)}
              aria-label={t("scp.detail.targetPlan")}
            >
              <option value="">{t("scp.detail.targetPlan")}</option>
              {(plans ?? []).map((plan) => (
                <option key={plan.id} value={plan.id}>
                  {plan.name} ({plan.code})
                </option>
              ))}
            </select>
            <Button variant="secondary" size="sm" disabled={busy || !changePlanId} onClick={() => void previewPlanChange()}>
              {t("scp.detail.preview")}
            </Button>
          </div>
          {changePreview ? (
            <div>
              <p className={styles.pageSubtitle}>
                {t("scp.detail.currentTotal")}: {money(changePreview.currentMonthlyMinor, changePreview.currentCurrencyCode ?? changePreview.currencyCode)}
                {" · "}
                {t("scp.detail.targetTotal")}: {money(changePreview.targetMonthlyMinor, changePreview.targetCurrencyCode ?? changePreview.currencyCode)}
                {" · "}
                {t("scp.detail.delta")}: {changePreview.deltaMonthlyMinor === null
                  ? "—"
                  : money(changePreview.deltaMonthlyMinor, changePreview.currencyCode)}
              </p>
              {changePreview.warnings.length > 0 ? (
                <ul>
                  {changePreview.warnings.map((warning, index) => (
                    <li key={index} className={styles.appCardMeta}>⚠ {warning}</li>
                  ))}
                </ul>
              ) : (
                <Button variant="primary" size="sm" disabled={busy} onClick={() => void confirmPlanChange()}>
                  {t("scp.detail.confirmChange")}
                </Button>
              )}
            </div>
          ) : null}
        </section>
      ) : null}

      <section className={styles.panel} aria-labelledby="scp-audit-heading">
        <h2 id="scp-audit-heading" className={styles.pageSubtitle}>{t("scp.detail.audit")}</h2>
        {detail && detail.audit.length > 0 ? (
          <ul>
            {detail.audit.map((entry, index) => (
              <li key={index} className={styles.appCardMeta}>
                {day(String(entry.createdAt ?? ""))} — {String(entry.action)} ({String(entry.resourceType)})
              </li>
            ))}
          </ul>
        ) : (
          <ScpEmpty message={t("scp.state.empty")} />
        )}
      </section>
    </ScpPage>
  );
}
