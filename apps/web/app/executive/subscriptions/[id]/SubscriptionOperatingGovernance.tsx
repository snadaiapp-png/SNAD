"use client";

import { useCallback, useEffect, useState } from "react";
import {
  executiveApi,
  type ManagedOrganization,
  type SubscriptionAvailableResource,
  type SubscriptionBillingProfile,
  type SubscriptionOperatingUnit,
  type SubscriptionResourceBinding,
  type SubscriptionUnitApplication,
} from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import { ScpEmpty, ScpStatusPill } from "../../_components/ScpStates";
import { scpErrorMessage } from "../../_components/scp-errors";
import styles from "../../scp.module.css";

/**
 * Operator-entered governance form values. They live in the parent page so a
 * full detail reload (which remounts this lazily-loaded section) does not
 * discard in-progress operator input — the exact pre-split behavior.
 */
export interface SubscriptionGovernanceFormState {
  selectedOrganizationId: string;
  setSelectedOrganizationId: (value: string | ((current: string) => string)) => void;
  branchName: string;
  setBranchName: (value: string) => void;
  existingOrganizationId: string;
  setExistingOrganizationId: (value: string) => void;
  billingProfileOrganizationId: string;
  setBillingProfileOrganizationId: (value: string) => void;
  billingProfileName: string;
  setBillingProfileName: (value: string) => void;
  billingEmail: string;
  setBillingEmail: (value: string) => void;
  selectedResource: string;
  setSelectedResource: (value: string) => void;
}

interface SubscriptionOperatingGovernanceProps {
  subscriptionId: string;
  tenantId: string;
  currencyCode: string;
  canManage: boolean;
  busy: boolean;
  /** Bumped by the page after every full detail load; governance data refetches in step. */
  dataVersion: number;
  onError: (message: string) => void;
  onNotice: (message: string) => void;
  setBusy: (busy: boolean) => void;
  forms: SubscriptionGovernanceFormState;
}

/**
 * Subscription operating-governance section: operating units, unit
 * applications, resource bindings and billing profiles. Loaded lazily so the
 * subscription-detail route's initial JS stays within the fail-closed
 * performance budget; this below-the-fold admin tooling is fetched on demand
 * after hydration. Status and bindings are never mutated directly — every
 * change goes through the canonical backend commands.
 */
export default function SubscriptionOperatingGovernance({
  subscriptionId,
  tenantId,
  currencyCode,
  canManage,
  busy,
  dataVersion,
  onError,
  onNotice,
  setBusy,
  forms,
}: SubscriptionOperatingGovernanceProps) {
  const { t } = useI18n();
  const [operatingUnits, setOperatingUnits] = useState<SubscriptionOperatingUnit[]>([]);
  const [unitApplications, setUnitApplications] = useState<SubscriptionUnitApplication[]>([]);
  const [billingProfiles, setBillingProfiles] = useState<SubscriptionBillingProfile[]>([]);
  const [resourceBindings, setResourceBindings] = useState<SubscriptionResourceBinding[]>([]);
  const [availableResources, setAvailableResources] = useState<SubscriptionAvailableResource[]>([]);
  const [tenantOrganizations, setTenantOrganizations] = useState<ManagedOrganization[]>([]);

  const { selectedOrganizationId, setSelectedOrganizationId } = forms;

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
      setSelectedOrganizationId((current: string) => {
        if (current && units.some((unit) => unit.organizationId === current && unit.status === "ACTIVE")) {
          return current;
        }
        return units.find((unit) => unit.status === "ACTIVE")?.organizationId ?? "";
      });
    } catch (reason) {
      onError(scpErrorMessage(reason));
    }
  }, [onError, setSelectedOrganizationId, subscriptionId]);

  useEffect(() => {
    void loadOperatingGovernance();
  }, [dataVersion, loadOperatingGovernance]);

  useEffect(() => {
    if (!tenantId) {
      setTenantOrganizations([]);
      return;
    }
    let active = true;
    executiveApi.organizations(tenantId).then((result) => {
      if (active) setTenantOrganizations(result);
    }).catch((reason) => {
      if (active) {
        setTenantOrganizations([]);
        onError(scpErrorMessage(reason));
      }
    });
    return () => {
      active = false;
    };
  }, [dataVersion, onError, tenantId]);

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
        if (active) onError(scpErrorMessage(reason));
      });
    return () => {
      active = false;
    };
  }, [onError, selectedOrganizationId, subscriptionId]);

  async function createAndBindBranch() {
    if (!tenantId || !forms.branchName.trim()) return;
    setBusy(true);
    onNotice("");
    onError("");
    let createdOrganizationId = "";
    try {
      const organization = await executiveApi.createOrganization(tenantId, {
        name: forms.branchName.trim(),
        unitType: "BRANCH",
      });
      createdOrganizationId = organization.id;
      await executiveApi.bindOperatingUnit(subscriptionId, organization.id, "CONSOLIDATED");
      forms.setBranchName("");
      forms.setSelectedOrganizationId(organization.id);
      onNotice(t("scp.detail.branchCreated"));
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
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function bindExistingOperatingUnit() {
    if (!forms.existingOrganizationId) return;
    setBusy(true);
    onError("");
    try {
      await executiveApi.bindOperatingUnit(
        subscriptionId,
        forms.existingOrganizationId,
        "CONSOLIDATED",
      );
      forms.setSelectedOrganizationId(forms.existingOrganizationId);
      forms.setExistingOrganizationId("");
      onNotice(t("scp.detail.branchBound"));
      await loadOperatingGovernance();
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function updateOperatingUnit(
    organizationId: string,
    billingMode: "CONSOLIDATED" | "SEPARATE",
  ) {
    setBusy(true);
    onError("");
    try {
      await executiveApi.bindOperatingUnit(subscriptionId, organizationId, billingMode);
      onNotice(t("scp.detail.branchBound"));
      await loadOperatingGovernance();
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function unbindOperatingUnit(organizationId: string) {
    setBusy(true);
    onError("");
    try {
      await executiveApi.deactivateOperatingUnit(subscriptionId, organizationId);
      onNotice(t("scp.detail.branchUnbound"));
      if (selectedOrganizationId === organizationId) forms.setSelectedOrganizationId("");
      await loadOperatingGovernance();
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function toggleUnitApplication(application: SubscriptionUnitApplication) {
    if (!selectedOrganizationId) return;
    setBusy(true);
    onError("");
    try {
      await executiveApi.setOperatingUnitApplication(
        subscriptionId,
        selectedOrganizationId,
        application.applicationId,
        !application.enabled,
      );
      onNotice(t("scp.detail.applicationUpdated"));
      setUnitApplications(await executiveApi.operatingUnitApplications(subscriptionId, selectedOrganizationId));
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function saveBillingProfile() {
    const profileCurrency = currencyCode.trim().toUpperCase();
    if (!forms.billingProfileName.trim() || !profileCurrency) return;
    setBusy(true);
    onError("");
    try {
      await executiveApi.upsertSubscriptionBillingProfile(subscriptionId, {
        organizationId: forms.billingProfileOrganizationId || null,
        profileName: forms.billingProfileName.trim(),
        billingEmail: forms.billingEmail.trim() || null,
        currencyCode: profileCurrency,
        billingMode: forms.billingProfileOrganizationId ? "SEPARATE" : "CONSOLIDATED",
      });
      forms.setBillingProfileName("");
      forms.setBillingEmail("");
      onNotice(t("scp.detail.billingProfileSaved"));
      await loadOperatingGovernance();
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function bindResource() {
    if (!selectedOrganizationId || !forms.selectedResource) return;
    const [resourceType, resourceId] = forms.selectedResource.split(":");
    if ((resourceType !== "WEBSITE" && resourceType !== "STORE") || !resourceId) return;
    setBusy(true);
    onError("");
    try {
      await executiveApi.bindSubscriptionResource(
        subscriptionId,
        selectedOrganizationId,
        resourceType,
        resourceId,
      );
      forms.setSelectedResource("");
      onNotice(t("scp.detail.resourceBound"));
      await loadOperatingGovernance();
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function unbindResource(binding: SubscriptionResourceBinding) {
    setBusy(true);
    onError("");
    try {
      await executiveApi.unbindSubscriptionResource(
        subscriptionId,
        binding.resourceType,
        binding.resourceId,
      );
      onNotice(t("scp.detail.resourceUnbound"));
      await loadOperatingGovernance();
    } catch (reason) {
      onError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
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
            value={forms.branchName}
            maxLength={200}
            onChange={(event) => forms.setBranchName(event.target.value)}
          />
          <Button
            variant="primary"
            size="sm"
            disabled={busy || !forms.branchName.trim()}
            onClick={() => void createAndBindBranch()}
          >
            {t("scp.detail.createBranch")}
          </Button>
          <select
            aria-label={t("scp.detail.existingBranch")}
            value={forms.existingOrganizationId}
            onChange={(event) => forms.setExistingOrganizationId(event.target.value)}
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
            disabled={busy || !forms.existingOrganizationId}
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
                          onClick={() => forms.setSelectedOrganizationId(unit.organizationId)}
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
                value={forms.selectedResource}
                onChange={(event) => forms.setSelectedResource(event.target.value)}
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
                disabled={busy || !forms.selectedResource}
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
              value={forms.billingProfileOrganizationId}
              onChange={(event) => forms.setBillingProfileOrganizationId(event.target.value)}
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
            value={forms.billingProfileName}
            maxLength={160}
            onChange={(event) => forms.setBillingProfileName(event.target.value)}
          />
          <Input
            type="email"
            label={t("scp.detail.billingEmail")}
            aria-label={t("scp.detail.billingEmail")}
            value={forms.billingEmail}
            maxLength={255}
            onChange={(event) => forms.setBillingEmail(event.target.value)}
          />
          <Button
            variant="secondary"
            size="sm"
            disabled={busy || !forms.billingProfileName.trim() || !currencyCode}
            onClick={() => void saveBillingProfile()}
          >
            {t("scp.detail.saveBillingProfile")}
          </Button>
        </div>
      ) : null}
    </section>
  );
}
