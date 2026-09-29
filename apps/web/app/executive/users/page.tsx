"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Button, Input } from "@/components/sds";
import { scpApi, type PlatformUser } from "@/lib/api/scp-platform-iam-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpAccess } from "../_components/ScpAccess";
import { ScpEmpty, ScpError, ScpPage, ScpSkeleton, ScpStatusPill } from "../_components/ScpStates";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

export default function PlatformUsersPage() {
  const { t } = useI18n();
  const { has, phase } = useScpAccess();
  const canRead = has("PLATFORM.USER.READ");
  const canCreate = has("PLATFORM.USER.CREATE");
  const [users, setUsers] = useState<PlatformUser[] | null>(null);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");

  const load = useCallback(async () => {
    if (phase !== "authorized" || !canRead) return;
    setError("");
    try {
      setUsers(await scpApi.platformUsers());
    } catch (reason) {
      setError(scpErrorMessage(reason));
    }
  }, [phase, canRead]);

  useEffect(() => { void load(); }, [load]);

  if (phase === "checking") {
    return <ScpPage title={t("scp.users.title")}><ScpSkeleton lines={5} /></ScpPage>;
  }
  if (phase === "degraded") {
    return (
      <ScpPage title={t("scp.users.title")}>
        <div data-testid="access-check-failed"><ScpError message={t("scp.state.errorGeneric")} /></div>
      </ScpPage>
    );
  }
  if (phase === "unauthorized" || !canRead) {
    return (
      <ScpPage title={t("scp.users.title")}>
        <div data-testid="access-denied"><ScpError message={t("scp.users.forbidden")} /></div>
      </ScpPage>
    );
  }
  if (error) {
    return (
      <ScpPage title={t("scp.users.title")}>
        <div data-testid="users-load-failed"><ScpError message={error} onRetry={load} /></div>
      </ScpPage>
    );
  }
  if (users === null) {
    return <ScpPage title={t("scp.users.title")}><ScpSkeleton lines={5} /></ScpPage>;
  }

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await scpApi.createPlatformUser({ email: email.trim(), displayName: displayName.trim() || undefined });
      setEmail("");
      setDisplayName("");
      setCreating(false);
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScpPage title={t("scp.users.title")} subtitle={t("scp.users.subtitle")}>
      <div data-testid="executive-users-ready">
        {canCreate ? (
          <div className={styles.filters}>
            <Button variant="primary" size="sm" onClick={() => setCreating((value) => !value)}>
              {t("scp.users.create")}
            </Button>
          </div>
        ) : null}
        {creating ? (
          <form className={styles.panel} onSubmit={(event) => void create(event)}>
            <label><span>{t("scp.users.email")}</span><Input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label><span>{t("scp.users.displayName")}</span><Input value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
            <Button type="submit" variant="primary" size="sm" loading={busy}>{t("scp.users.submit")}</Button>
          </form>
        ) : null}
        {users.length === 0 ? <ScpEmpty message={t("scp.users.empty")} /> : null}
        {users.length > 0 ? (
          <div className={styles.cards}>
            {users.map((user) => (
              <article key={user.userId} className={styles.appCard} data-testid={`executive-user-${user.userId}`}>
                <h2 className={styles.appCardTitle}>{user.displayName || user.email}</h2>
                <span className={styles.appCardMeta}>{user.email}</span>
                <div className={styles.filters}>
                  <ScpStatusPill value={user.accountStatus} />
                  <ScpStatusPill value={user.membershipStatus} />
                </div>
                <Link href={`/executive/users/${user.userId}`} className={styles.navLink}>{t("scp.users.open")}</Link>
              </article>
            ))}
          </div>
        ) : null}
      </div>
    </ScpPage>
  );
}
