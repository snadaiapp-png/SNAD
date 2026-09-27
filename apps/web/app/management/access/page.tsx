"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  tenantAccessApi,
  type CapabilityResponse,
  type RoleAccessResponse,
  type RoleResponse,
} from "@/lib/api/tenant-access";
import { toUserFacingMessage } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./access.module.css";

const TRANSIENT_AUTH_STATES = new Set([
  "INITIALIZING",
  "CHECKING_SESSION",
  "REFRESHING",
  "REFRESHING_SESSION",
  "LOGGING_OUT",
]);

export default function TenantAccessPage() {
  const { state, user, me } = useAuth();
  const router = useRouter();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const tenantId = user?.tenantId ?? null;
  const canReadRoles = capabilities.includes("ROLE.READ");
  const canReadCapabilities = capabilities.includes("CAPABILITY.READ");
  const canRead = canReadRoles && canReadCapabilities;
  const canManageRoles = capabilities.includes("ROLE.MANAGE");
  const canManageCapabilities = capabilities.includes("CAPABILITY.MANAGE");

  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [registry, setRegistry] = useState<CapabilityResponse[]>([]);
  const [roleAccess, setRoleAccess] = useState<RoleAccessResponse[]>([]);
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [roleAccessLoading, setRoleAccessLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showRoleForm, setShowRoleForm] = useState(false);
  const [showCapabilityForm, setShowCapabilityForm] = useState(false);
  const [roleCode, setRoleCode] = useState("");
  const [roleName, setRoleName] = useState("");
  const [capabilityCode, setCapabilityCode] = useState("");
  const [capabilityName, setCapabilityName] = useState("");

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/?returnUrl=%2Fmanagement%2Faccess");
    }
  }, [router, state]);

  const loadAccess = useCallback(async () => {
    if (!tenantId || !canRead) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [nextRoles, nextCapabilities] = await Promise.all([
        tenantAccessApi.listRoles(tenantId),
        tenantAccessApi.listCapabilities(),
      ]);
      setRoles(nextRoles);
      setRegistry(nextCapabilities);
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setLoading(false);
    }
  }, [canRead, tenantId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    void loadAccess();
  }, [loadAccess, state]);

  const loadRoleAccess = useCallback(async (roleId: string) => {
    if (!tenantId) return;
    setRoleAccessLoading(true);
    setError(null);
    try {
      setRoleAccess(await tenantAccessApi.listRoleCapabilities(tenantId, roleId));
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setRoleAccessLoading(false);
    }
  }, [tenantId]);

  const selectedRole = useMemo(
    () => roles.find((role) => role.id === selectedRoleId) ?? null,
    [roles, selectedRoleId],
  );
  const attachedCapabilityIds = useMemo(
    () => new Set(roleAccess.map((link) => link.capabilityId)),
    [roleAccess],
  );

  if (TRANSIENT_AUTH_STATES.has(state)) return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED" || !tenantId) return <AuthLoadingState phase="workspace" />;

  const runMutation = async (operation: () => Promise<unknown>, after?: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await operation();
      if (after) await after();
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const createRole = async (event: React.FormEvent) => {
    event.preventDefault();
    const code = roleCode.trim();
    const name = roleName.trim();
    if (!code || !name || !canManageRoles) return;
    await runMutation(
      () => tenantAccessApi.createRole(tenantId, { code, name, description: null }),
      async () => {
        setRoleCode("");
        setRoleName("");
        setShowRoleForm(false);
        await loadAccess();
      },
    );
  };

  const transitionRole = async (role: RoleResponse, action: "activate" | "deactivate" | "archive") => {
    if (!canManageRoles) return;
    if (action === "archive" && !window.confirm(t("management.access.confirmArchiveRole"))) return;
    await runMutation(() => tenantAccessApi.transitionRole(tenantId, role.id, action), loadAccess);
  };

  const createCapability = async (event: React.FormEvent) => {
    event.preventDefault();
    const code = capabilityCode.trim();
    const name = capabilityName.trim();
    if (!code || !name || !canManageCapabilities) return;
    await runMutation(
      () => tenantAccessApi.createCapability({ code, name, description: null }),
      async () => {
        setCapabilityCode("");
        setCapabilityName("");
        setShowCapabilityForm(false);
        await loadAccess();
      },
    );
  };

  const transitionCapability = async (capability: CapabilityResponse, action: "activate" | "deactivate") => {
    if (!canManageCapabilities) return;
    await runMutation(() => tenantAccessApi.transitionCapability(capability.id, action), loadAccess);
  };

  const selectRole = async (role: RoleResponse) => {
    setSelectedRoleId(role.id);
    await loadRoleAccess(role.id);
  };

  const attachCapability = async (capabilityId: string) => {
    if (!selectedRoleId || !canManageRoles) return;
    await runMutation(
      () => tenantAccessApi.attachRoleCapability(tenantId, selectedRoleId, capabilityId),
      () => loadRoleAccess(selectedRoleId),
    );
  };

  const detachCapability = async (capabilityId: string) => {
    if (!selectedRoleId || !canManageRoles) return;
    if (!window.confirm(t("management.access.confirmDetachCapability"))) return;
    await runMutation(
      () => tenantAccessApi.detachRoleCapability(tenantId, selectedRoleId, capabilityId),
      () => loadRoleAccess(selectedRoleId),
    );
  };

  return (
    <ExecutiveShell>
      <section className={styles.root} data-testid="management-access-ready">
        <header className={styles.header}>
          <div>
            <h1 className={styles.title}>{t("management.access.title")}</h1>
            <p className={styles.subtitle}>{t("management.access.subtitle")}</p>
          </div>
        </header>

        {!canRead ? (
          <div className={styles.alert} role="alert">{t("management.access.forbidden")}</div>
        ) : error ? (
          <div className={styles.alert} role="alert">{error}</div>
        ) : loading ? (
          <div className={styles.state} role="status">{t("management.access.loading")}</div>
        ) : (
          <div className={styles.grid}>
            <section className={styles.panel} aria-labelledby="tenant-roles-heading">
              <div className={styles.panelHeader}>
                <h2 id="tenant-roles-heading">{t("management.access.roles")}</h2>
                {canManageRoles ? (
                  <button className={styles.primaryButton} type="button" onClick={() => setShowRoleForm((value) => !value)}>
                    {t("management.access.createRole")}
                  </button>
                ) : null}
              </div>

              {showRoleForm && canManageRoles ? (
                <form className={styles.form} onSubmit={createRole}>
                  <label>
                    <span>{t("management.access.roleCode")}</span>
                    <input value={roleCode} onChange={(event) => setRoleCode(event.target.value)} aria-label={t("management.access.roleCode")} />
                  </label>
                  <label>
                    <span>{t("management.access.roleName")}</span>
                    <input value={roleName} onChange={(event) => setRoleName(event.target.value)} aria-label={t("management.access.roleName")} />
                  </label>
                  <button className={styles.primaryButton} type="submit" disabled={busy}>{t("management.access.create")}</button>
                </form>
              ) : null}

              {roles.length === 0 ? <p className={styles.state}>{t("management.access.emptyRoles")}</p> : (
                <div className={styles.tableScroll}>
                  <table className={styles.table}>
                    <thead><tr><th>{t("management.access.roleName")}</th><th>{t("management.access.status")}</th><th>{t("management.access.actions")}</th></tr></thead>
                    <tbody>
                      {roles.map((role) => (
                        <tr key={role.id}>
                          <td><strong>{role.name}</strong><div className={styles.code}>{role.code}</div></td>
                          <td>{role.status}</td>
                          <td className={styles.actions}>
                            <button type="button" onClick={() => void selectRole(role)} aria-label={`${role.name} — ${t("management.access.manageRoleCapabilities")}`}>
                              {t("management.access.manageRoleCapabilities")}
                            </button>
                            {canManageRoles && role.status !== "ACTIVE" ? <button type="button" disabled={busy} onClick={() => void transitionRole(role, "activate")}>{t("management.access.activateRole")}</button> : null}
                            {canManageRoles && role.status === "ACTIVE" ? <button type="button" disabled={busy} onClick={() => void transitionRole(role, "deactivate")}>{t("management.access.deactivateRole")}</button> : null}
                            {canManageRoles && role.status !== "ARCHIVED" ? <button type="button" disabled={busy} onClick={() => void transitionRole(role, "archive")}>{t("management.access.archiveRole")}</button> : null}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className={styles.panel} aria-labelledby="capability-registry-heading">
              <div className={styles.panelHeader}>
                <h2 id="capability-registry-heading">{t("management.access.capabilities")}</h2>
                {canManageCapabilities ? (
                  <button className={styles.primaryButton} type="button" onClick={() => setShowCapabilityForm((value) => !value)}>
                    {t("management.access.createCapability")}
                  </button>
                ) : null}
              </div>

              {showCapabilityForm && canManageCapabilities ? (
                <form className={styles.form} onSubmit={createCapability}>
                  <label>
                    <span>{t("management.access.capabilityCode")}</span>
                    <input value={capabilityCode} onChange={(event) => setCapabilityCode(event.target.value)} aria-label={t("management.access.capabilityCode")} />
                  </label>
                  <label>
                    <span>{t("management.access.capabilityName")}</span>
                    <input value={capabilityName} onChange={(event) => setCapabilityName(event.target.value)} aria-label={t("management.access.capabilityName")} />
                  </label>
                  <button className={styles.primaryButton} type="submit" disabled={busy}>{t("management.access.create")}</button>
                </form>
              ) : null}

              {registry.length === 0 ? <p className={styles.state}>{t("management.access.emptyCapabilities")}</p> : (
                <ul className={styles.registry}>
                  {registry.map((capability) => (
                    <li key={capability.id}>
                      <div><strong>{capability.code}</strong><div>{capability.name}</div></div>
                      {canManageCapabilities ? (
                        capability.status === "ACTIVE"
                          ? <button type="button" disabled={busy} onClick={() => void transitionCapability(capability, "deactivate")}>{t("management.access.deactivateCapability")}</button>
                          : <button type="button" disabled={busy} onClick={() => void transitionCapability(capability, "activate")}>{t("management.access.activateCapability")}</button>
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {selectedRole ? (
              <section className={`${styles.panel} ${styles.fullWidth}`} aria-labelledby="role-capabilities-heading">
                <div className={styles.panelHeader}>
                  <h2 id="role-capabilities-heading">{t("management.access.roleCapabilities")}: {selectedRole.name}</h2>
                </div>
                {roleAccessLoading ? <p className={styles.state}>{t("management.access.loading")}</p> : (
                  <ul className={styles.registry}>
                    {registry.map((capability) => {
                      const attached = attachedCapabilityIds.has(capability.id);
                      return (
                        <li key={capability.id}>
                          <div><strong>{capability.code}</strong><div>{capability.name}</div></div>
                          {canManageRoles ? (
                            attached ? (
                              <button type="button" disabled={busy} onClick={() => void detachCapability(capability.id)}>{t("management.access.detachCapability")}</button>
                            ) : (
                              <button type="button" disabled={busy} onClick={() => void attachCapability(capability.id)}>{t("management.access.attachCapability")}</button>
                            )
                          ) : null}
                        </li>
                      );
                    })}
                  </ul>
                )}
              </section>
            ) : null}
          </div>
        )}
      </section>
    </ExecutiveShell>
  );
}
