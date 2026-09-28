"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/sds";
import {
  scpApi,
  type PlatformCapability,
  type PlatformRole,
} from "@/lib/api/scp-platform-iam-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpAccess } from "../_components/ScpAccess";
import { ScpError, ScpNotice, ScpPage, ScpSkeleton, ScpStatusPill } from "../_components/ScpStates";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

export default function PlatformAccessPage() {
  const { t } = useI18n();
  const { has } = useScpAccess();
  const canReadRoles = has("PLATFORM.ROLE.READ");
  const canReadCapabilities = has("PLATFORM.PERMISSION.READ");
  const canManageCapabilities = has("PLATFORM.PERMISSION.MANAGE");
  const [roles, setRoles] = useState<PlatformRole[] | null>(null);
  const [capabilities, setCapabilities] = useState<PlatformCapability[] | null>(null);
  const [selectedRole, setSelectedRole] = useState<string | null>(null);
  const [selectedCapabilities, setSelectedCapabilities] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const allowed = canReadRoles || canReadCapabilities;
  const load = useCallback(async () => {
    if (!allowed) return;
    setError("");
    try {
      const [nextRoles, nextCapabilities] = await Promise.all([
        canReadRoles ? scpApi.platformRoles() : Promise.resolve([]),
        canReadCapabilities ? scpApi.platformCapabilities() : Promise.resolve([]),
      ]);
      setRoles(nextRoles);
      setCapabilities(nextCapabilities);
    } catch (reason) {
      setError(scpErrorMessage(reason));
    }
  }, [allowed, canReadCapabilities, canReadRoles]);

  useEffect(() => { void load(); }, [load]);

  if (!allowed) {
    return <ScpPage title={t("scp.access.title")}><ScpError message={t("scp.access.forbidden")} /></ScpPage>;
  }
  if (roles === null || capabilities === null) {
    return <ScpPage title={t("scp.access.title")}><ScpSkeleton lines={6} /></ScpPage>;
  }

  async function selectRole(roleId: string) {
    setSelectedRole(roleId);
    if (!canReadCapabilities) return;
    try {
      const links = await scpApi.platformRoleCapabilities(roleId);
      setSelectedCapabilities(links.map((link) => link.capabilityId));
    } catch (reason) { setError(scpErrorMessage(reason)); }
  }

  async function saveCapabilities() {
    if (!selectedRole) return;
    setBusy(true);
    try {
      await scpApi.replacePlatformRoleCapabilities(selectedRole, selectedCapabilities);
      await selectRole(selectedRole);
    } catch (reason) { setError(scpErrorMessage(reason)); } finally { setBusy(false); }
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
                {canReadCapabilities ? <Button size="sm" variant="secondary" onClick={() => void selectRole(role.id)}>{t("scp.access.capabilities")}</Button> : null}
              </article>
            ))}
          </div>
        </section>
      ) : null}
      {canReadCapabilities ? (
        <section className={styles.panel}>
          <h2>{t("scp.access.capabilities")}</h2>
          {capabilities.map((capability) => (
            <label key={capability.id} className={styles.appCardMeta}>
              {canManageCapabilities && selectedRole ? (
                <input type="checkbox" checked={selectedCapabilities.includes(capability.id)} onChange={(event) => setSelectedCapabilities((current) => event.target.checked ? [...current, capability.id] : current.filter((id) => id !== capability.id))} />
              ) : null} {capability.code}
            </label>
          ))}
          {canManageCapabilities && selectedRole ? <Button size="sm" variant="primary" loading={busy} onClick={() => void saveCapabilities()}>{t("scp.access.saveCapabilities")}</Button> : null}
        </section>
      ) : null}
      <ScpNotice>{t("scp.access.temporaryUnavailable")}</ScpNotice>
    </ScpPage>
  );
}
