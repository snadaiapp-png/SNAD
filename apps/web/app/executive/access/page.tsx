"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/sds/Button";
import {
  scpApi,
  type PlatformCapability,
  type PlatformRole,
  type PlatformUser,
} from "@/lib/api/scp-platform-iam-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpAccess } from "../_components/ScpAccess";
import { ScpError, ScpPage, ScpSkeleton, ScpStatusPill } from "../_components/ScpStates";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";
import { TemporaryAccessCard } from "../users/_components/TemporaryAccessCard";

export default function PlatformAccessPage() {
  const { t } = useI18n();
  const { has } = useScpAccess();
  const canReadRoles = has("PLATFORM.ROLE.READ");
  const canReadCapabilities = has("PLATFORM.PERMISSION.READ");
  const canManageCapabilities = has("PLATFORM.PERMISSION.MANAGE");
  const canReadUsers = has("PLATFORM.USER.READ");

  const [users, setUsers] = useState<PlatformUser[]>([]);
  const [temporaryUserId, setTemporaryUserId] = useState("");
  const [roles, setRoles] = useState<PlatformRole[] | null>(null);
  const [capabilities, setCapabilities] = useState<PlatformCapability[] | null>(null);
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null);
  const [selectedCapabilities, setSelectedCapabilities] = useState<string[]>([]);
  const [roleLoading, setRoleLoading] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const [roleCode, setRoleCode] = useState("");
  const [roleName, setRoleName] = useState("");
  const [roleDescription, setRoleDescription] = useState("");
  const [editRoleName, setEditRoleName] = useState("");
  const [editRoleDescription, setEditRoleDescription] = useState("");

  const allowed = canReadRoles || canReadCapabilities;
  const selectedRole = useMemo(
    () => roles?.find((role) => role.id === selectedRoleId) ?? null,
    [roles, selectedRoleId],
  );
  const selectedRoleReadOnly = Boolean(selectedRole?.protectedRole || selectedRole?.ownerRole);

  const load = useCallback(async () => {
    if (!allowed) return;
    setError("");
    try {
      const [nextRoles, nextCapabilities, nextUsers] = await Promise.all([
        canReadRoles ? scpApi.platformRoles() : Promise.resolve([]),
        canReadCapabilities ? scpApi.platformCapabilities() : Promise.resolve([]),
        canReadUsers ? scpApi.platformUsers() : Promise.resolve([]),
      ]);
      setUsers(nextUsers);
      setRoles(nextRoles);
      setCapabilities(nextCapabilities);
    } catch (reason) {
      setError(scpErrorMessage(reason));
    }
  }, [allowed, canReadCapabilities, canReadRoles, canReadUsers]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!allowed) {
    return (
      <ScpPage title={t("scp.access.title")}>
        <ScpError message={t("scp.access.forbidden")} />
      </ScpPage>
    );
  }

  if (error && (roles === null || capabilities === null)) {
    return (
      <ScpPage title={t("scp.access.title")}>
        <ScpError message={error} onRetry={load} />
      </ScpPage>
    );
  }

  if (roles === null || capabilities === null) {
    return (
      <ScpPage title={t("scp.access.title")}>
        <ScpSkeleton lines={6} />
      </ScpPage>
    );
  }

  async function selectRole(roleId: string) {
    const role = roles?.find((candidate) => candidate.id === roleId) ?? null;
    setSelectedRoleId(roleId);
    setEditRoleName(role?.name ?? "");
    setEditRoleDescription(role?.description ?? "");
    if (!canReadCapabilities) return;

    setRoleLoading(true);
    setError("");
    try {
      const links = await scpApi.platformRoleCapabilities(roleId);
      setSelectedCapabilities(links.map((link) => link.capabilityId));
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setRoleLoading(false);
    }
  }

  async function saveCapabilities() {
    if (!selectedRoleId || selectedRoleReadOnly || roleLoading) return;
    setBusy(true);
    setError("");
    try {
      await scpApi.replacePlatformRoleCapabilities(selectedRoleId, selectedCapabilities);
      await selectRole(selectedRoleId);
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function createRole() {
    const code = roleCode.trim();
    const name = roleName.trim();
    if (!code || !name) return;
    setBusy(true);
    setError("");
    try {
      await scpApi.createPlatformRole({ code, name, description: roleDescription.trim() });
      setRoleCode("");
      setRoleName("");
      setRoleDescription("");
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function updateRole() {
    if (!selectedRole || selectedRoleReadOnly || selectedRole.roleType !== "CUSTOM") return;
    const name = editRoleName.trim();
    if (!name) return;
    setBusy(true);
    setError("");
    try {
      await scpApi.updatePlatformRole(selectedRole.id, {
        name,
        description: editRoleDescription.trim(),
      });
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScpPage title={t("scp.access.title")} subtitle={t("scp.access.subtitle")}>
      {error ? <ScpError message={error} onRetry={load} /> : null}

      {canReadRoles ? (
        <section className={styles.panel}>
          <h2>{t("scp.access.roles")}</h2>
          <div className={styles.cards}>
            {roles.map((role) => (
              <article key={role.id} className={styles.appCard}>
                <h3 className={styles.appCardTitle}>{role.code}</h3>
                <p>{role.name}</p>
                <div className={styles.filters}>
                  <ScpStatusPill value={role.status} />
                  {role.protectedRole ? <span className={styles.appCardMeta}>{t("scp.access.protected")}</span> : null}
                  {role.ownerRole ? <span className={styles.appCardMeta}>{t("scp.access.owner")}</span> : null}
                </div>
                {canReadCapabilities ? (
                  <Button size="sm" variant="secondary" onClick={() => void selectRole(role.id)}>
                    {t("scp.access.capabilities")}
                  </Button>
                ) : null}
              </article>
            ))}
          </div>

          {canManageCapabilities ? (
            <div className={styles.panel}>
              <label className={styles.appCardMeta}>
                {t("scp.access.roleCode")}
                <input value={roleCode} onChange={(event) => setRoleCode(event.target.value)} />
              </label>
              <label className={styles.appCardMeta}>
                {t("scp.access.roleName")}
                <input value={roleName} onChange={(event) => setRoleName(event.target.value)} />
              </label>
              <label className={styles.appCardMeta}>
                {t("scp.access.roleDescription")}
                <input value={roleDescription} onChange={(event) => setRoleDescription(event.target.value)} />
              </label>
              <Button size="sm" variant="primary" loading={busy} onClick={() => void createRole()}>
                {t("scp.access.createRole")}
              </Button>
            </div>
          ) : null}
        </section>
      ) : null}

      {canReadCapabilities ? (
        <section className={styles.panel}>
          <h2>{t("scp.access.capabilities")}</h2>
          {capabilities.map((capability) => (
            <label key={capability.id} className={styles.appCardMeta}>
              {canManageCapabilities && selectedRole && !selectedRoleReadOnly ? (
                <input
                  type="checkbox"
                  checked={selectedCapabilities.includes(capability.id)}
                  disabled={roleLoading || busy}
                  onChange={(event) => setSelectedCapabilities((current) => (
                    event.target.checked
                      ? [...current, capability.id]
                      : current.filter((id) => id !== capability.id)
                  ))}
                />
              ) : null}{" "}
              {capability.code}
            </label>
          ))}
          {canManageCapabilities && selectedRole && !selectedRoleReadOnly ? (
            <Button
              size="sm"
              variant="primary"
              loading={busy}
              disabled={roleLoading || busy}
              onClick={() => void saveCapabilities()}
            >
              {t("scp.access.saveCapabilities")}
            </Button>
          ) : null}

          {canManageCapabilities && selectedRole?.roleType === "CUSTOM" && !selectedRoleReadOnly ? (
            <div className={styles.panel}>
              <label className={styles.appCardMeta}>
                {t("scp.access.editRoleName")}
                <input value={editRoleName} onChange={(event) => setEditRoleName(event.target.value)} />
              </label>
              <label className={styles.appCardMeta}>
                {t("scp.access.editRoleDescription")}
                <input value={editRoleDescription} onChange={(event) => setEditRoleDescription(event.target.value)} />
              </label>
              <Button size="sm" variant="primary" loading={busy} onClick={() => void updateRole()}>
                {t("scp.access.updateRole")}
              </Button>
            </div>
          ) : null}
        </section>
      ) : null}

      {canReadUsers && canReadCapabilities ? (
        <section className={styles.panel}>
          <label>
            {t("scp.temporary.user")}
            <select value={temporaryUserId} onChange={(event) => setTemporaryUserId(event.target.value)}>
              <option value="">{t("scp.temporary.choose")}</option>
              {users.map((user) => <option key={user.userId} value={user.userId}>{user.email}</option>)}
            </select>
          </label>
          {temporaryUserId ? <TemporaryAccessCard key={temporaryUserId} userId={temporaryUserId} /> : null}
        </section>
      ) : null}
    </ScpPage>
  );
}
