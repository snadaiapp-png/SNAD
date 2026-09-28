"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { Button } from "@/components/sds";
import {
  scpApi,
  type PlatformRole,
  type PlatformSessionSummary,
  type PlatformUser,
  type PlatformUserLifecycle,
  type PlatformUserRoleGrant,
} from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpAccess } from "../../_components/ScpAccess";
import { ScpEmpty, ScpError, ScpPage, ScpSkeleton, ScpStatusPill } from "../../_components/ScpStates";
import { scpErrorMessage } from "../../_components/scp-errors";
import styles from "../../scp.module.css";

export default function PlatformUserDetailPage() {
  const params = useParams<{ userId: string }>();
  const userId = params.userId;
  const { t } = useI18n();
  const { has } = useScpAccess();
  const canRead = has("PLATFORM.USER.READ");
  const canReadRoles = has("PLATFORM.ROLE.READ");
  const canReadPermissions = has("PLATFORM.PERMISSION.READ");
  const canReadSessions = has("PLATFORM.SESSION.READ");
  const canRevokeSessions = has("PLATFORM.SESSION.REVOKE");
  const canAssignRoles = has("PLATFORM.ROLE.ASSIGN");

  const [user, setUser] = useState<PlatformUser | null>(null);
  const [roles, setRoles] = useState<PlatformUserRoleGrant[]>([]);
  const [permissions, setPermissions] = useState<string[]>([]);
  const [sessions, setSessions] = useState<PlatformSessionSummary | null>(null);
  const [availableRoles, setAvailableRoles] = useState<PlatformRole[]>([]);
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!canRead) return;
    setError("");
    try {
      const [nextUser, nextRoles, nextPermissions, nextSessions, catalog] = await Promise.all([
        scpApi.platformUser(userId),
        canReadRoles ? scpApi.platformUserRoles(userId) : Promise.resolve([]),
        canReadPermissions ? scpApi.platformUserPermissions(userId) : Promise.resolve([]),
        canReadSessions ? scpApi.platformUserSessions(userId) : Promise.resolve(null),
        canAssignRoles ? scpApi.platformRoles() : Promise.resolve([]),
      ]);
      setUser(nextUser);
      setRoles(nextRoles);
      setPermissions(nextPermissions);
      setSessions(nextSessions);
      setAvailableRoles(catalog);
      setSelectedRoles(nextRoles.filter((role) => role.status === "ACTIVE").map((role) => role.roleId));
    } catch (reason) {
      setError(scpErrorMessage(reason));
    }
  }, [canAssignRoles, canRead, canReadPermissions, canReadRoles, canReadSessions, userId]);

  useEffect(() => { void load(); }, [load]);

  const roleCodes = useMemo(() => roles.filter((role) => role.status === "ACTIVE").map((role) => role.roleCode), [roles]);

  if (!canRead) return <ScpPage title={t("scp.userDetail.title")}><ScpError message={t("scp.userDetail.forbidden")} /></ScpPage>;
  if (!user && !error) return <ScpPage title={t("scp.userDetail.title")}><ScpSkeleton lines={6} /></ScpPage>;

  async function lifecycle(transition: PlatformUserLifecycle) {
    setBusy(true);
    setError("");
    try {
      await scpApi.transitionPlatformUser(userId, transition, transition === "activate" ? undefined : "Executive lifecycle action");
      await load();
    } catch (reason) { setError(scpErrorMessage(reason)); } finally { setBusy(false); }
  }

  async function revokeSessions() {
    setBusy(true);
    try {
      setSessions(await scpApi.revokePlatformUserSessions(userId, "Executive session security action"));
    } catch (reason) { setError(scpErrorMessage(reason)); } finally { setBusy(false); }
  }

  async function replaceRoles() {
    setBusy(true);
    try {
      await scpApi.replacePlatformUserRoles(userId, selectedRoles, "Executive role assignment update");
      await load();
    } catch (reason) { setError(scpErrorMessage(reason)); } finally { setBusy(false); }
  }

  return (
    <ScpPage title={t("scp.userDetail.title")} subtitle={user?.email}>
      {error ? <ScpError message={error} onRetry={load} /> : null}
      {user ? (
        <>
          <section className={styles.panel}>
            <h2>{t("scp.userDetail.membership")}</h2>
            <p>{user.displayName || user.email}</p>
            <div className={styles.filters}><ScpStatusPill value={user.accountStatus} /><ScpStatusPill value={user.membershipStatus} /></div>
            <div className={styles.filters}>
              {has("PLATFORM.USER.UPDATE") && ["INVITED", "SUSPENDED", "LOCKED"].includes(user.membershipStatus) ? <Button size="sm" variant="primary" disabled={busy} onClick={() => void lifecycle("activate")}>{t("scp.userDetail.activate")}</Button> : null}
              {has("PLATFORM.USER.SUSPEND") && user.membershipStatus === "ACTIVE" ? <Button size="sm" variant="secondary" disabled={busy} onClick={() => void lifecycle("suspend")}>{t("scp.userDetail.suspend")}</Button> : null}
              {has("PLATFORM.SECURITY.MANAGE") && user.membershipStatus === "ACTIVE" ? <Button size="sm" variant="secondary" disabled={busy} onClick={() => void lifecycle("lock")}>{t("scp.userDetail.lock")}</Button> : null}
              {has("PLATFORM.USER.DISABLE") && user.membershipStatus !== "DISABLED" ? <Button size="sm" variant="danger" disabled={busy} onClick={() => void lifecycle("disable")}>{t("scp.userDetail.disable")}</Button> : null}
            </div>
          </section>
          <section className={styles.panel}>
            <h2>{t("scp.userDetail.roles")}</h2>
            {roleCodes.length ? roleCodes.map((code) => <p key={code}>{code}</p>) : <ScpEmpty message={t("scp.userDetail.noRoles")} />}
            {canAssignRoles && availableRoles.length ? (
              <>
                {availableRoles.map((role) => (
                  <label key={role.id} className={styles.appCardMeta}>
                    <input type="checkbox" checked={selectedRoles.includes(role.id)} onChange={(event) => setSelectedRoles((current) => event.target.checked ? [...current, role.id] : current.filter((id) => id !== role.id))} /> {role.code}
                  </label>
                ))}
                <Button size="sm" variant="primary" disabled={busy} onClick={() => void replaceRoles()}>{t("scp.userDetail.replaceRoles")}</Button>
              </>
            ) : null}
          </section>
          <section className={styles.panel}>
            <h2>{t("scp.userDetail.permissions")}</h2>
            {permissions.length ? permissions.map((permission) => <p key={permission}>{permission}</p>) : <ScpEmpty message={t("scp.userDetail.noPermissions")} />}
          </section>
          {canReadSessions && sessions ? (
            <section className={styles.panel}>
              <h2>{t("scp.userDetail.sessions")}</h2>
              <p>{t("scp.userDetail.sessionVersion")}: {sessions.sessionVersion}</p>
              <p>{t("scp.userDetail.lastLogin")}: {sessions.lastLoginAt ?? "—"}</p>
              {canRevokeSessions ? <Button size="sm" variant="danger" disabled={busy} onClick={() => void revokeSessions()}>{t("scp.userDetail.revokeSessions")}</Button> : null}
            </section>
          ) : null}
        </>
      ) : null}
    </ScpPage>
  );
}
