"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Input } from "@/components/sds";
import { scpApi, type PlatformUser } from "@/lib/api/scp-platform-iam-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { authorizationText } from "@/lib/i18n/authorization-l10n";
import { useScpAccess } from "../_components/ScpAccess";
import { ScpEmpty, ScpError, ScpPage, ScpSkeleton, ScpStatusPill } from "../_components/ScpStates";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

const READ_CAPABILITIES = [
  "ROLE.READ",
  "AUTHORIZATION.OVERRIDE.MANAGE",
  "AUTHORIZATION.RELATIONSHIP.MANAGE",
];

export default function AuthorizationPage() {
  const access = useScpAccess();
  const { locale } = useI18n();
  const canReadAuthorization = access.hasAny(READ_CAPABILITIES);
  const canReadUsers = access.has("PLATFORM.USER.READ");
  const canReadPlatformAccess =
    access.has("PLATFORM.ROLE.READ") || access.has("PLATFORM.PERMISSION.READ");

  const [users, setUsers] = useState<PlatformUser[] | null>(null);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");

  const loadUsers = useCallback(async () => {
    if (access.phase !== "authorized" || !canReadAuthorization || !canReadUsers) return;
    setError("");
    try {
      setUsers(await scpApi.platformUsers());
    } catch (reason) {
      setError(scpErrorMessage(reason));
    }
  }, [access.phase, canReadAuthorization, canReadUsers]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const filteredUsers = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return users ?? [];
    return (users ?? []).filter((user) =>
      [
        user.displayName ?? "",
        user.email,
        user.accountStatus,
        user.membershipStatus,
      ].some((value) => value.toLowerCase().includes(query)),
    );
  }, [search, users]);

  if (access.phase === "checking") {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpSkeleton lines={5} />
      </ScpPage>
    );
  }

  if (access.phase === "degraded") {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpError message={authorizationText(locale, "accessCheckFailed")} onRetry={access.refresh} />
      </ScpPage>
    );
  }

  if (access.phase === "unauthorized" || !canReadAuthorization) {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpError message={authorizationText(locale, "noAccess")} />
      </ScpPage>
    );
  }

  if (!canReadUsers) {
    return (
      <ScpPage
        title={authorizationText(locale, "title")}
        subtitle={authorizationText(locale, "subtitle")}
      >
        <section className={styles.panel}>
          <h2>{authorizationText(locale, "authorizationScope")}</h2>
          <p className={styles.pageSubtitle}>{authorizationText(locale, "authorizationScopeBody")}</p>
          {canReadPlatformAccess ? (
            <Link className={styles.navLink} href="/executive/access">
              {authorizationText(locale, "manageRoles")}
            </Link>
          ) : null}
        </section>
        <ScpError message={authorizationText(locale, "userDirectoryRequired")} />
      </ScpPage>
    );
  }

  if (error) {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpError message={error} onRetry={loadUsers} />
      </ScpPage>
    );
  }

  if (users === null) {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpSkeleton lines={6} />
      </ScpPage>
    );
  }

  return (
    <ScpPage
      title={authorizationText(locale, "title")}
      subtitle={authorizationText(locale, "subtitle")}
    >
      <div data-testid="executive-authorization-ready">
        <section className={styles.panel}>
          <h2>{authorizationText(locale, "authorizationScope")}</h2>
          <p className={styles.pageSubtitle}>{authorizationText(locale, "authorizationScopeBody")}</p>
          <div className={styles.filters}>
            <Link className={styles.navLink} href="/executive/users">
              {authorizationText(locale, "manageUsers")}
            </Link>
            {canReadPlatformAccess ? (
              <Link className={styles.navLink} href="/executive/access">
                {authorizationText(locale, "manageRoles")}
              </Link>
            ) : null}
          </div>
        </section>

        <section className={styles.panel} aria-labelledby="authorization-user-directory">
          <h2 id="authorization-user-directory">{authorizationText(locale, "users")}</h2>
          <label>
            <span>{authorizationText(locale, "searchUsers")}</span>
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={authorizationText(locale, "searchUsersPlaceholder")}
            />
          </label>

          {filteredUsers.length === 0 ? (
            <ScpEmpty message={authorizationText(locale, "emptyUsers")} />
          ) : (
            <div className={styles.cards}>
              {filteredUsers.map((user) => (
                <article key={user.userId} className={styles.appCard}>
                  <h3 className={styles.appCardTitle}>{user.displayName || user.email}</h3>
                  <span className={styles.appCardMeta}>{user.email}</span>
                  <div className={styles.filters}>
                    <ScpStatusPill value={user.accountStatus} />
                    <ScpStatusPill value={user.membershipStatus} />
                  </div>
                  <Link
                    className={styles.navLink}
                    href={"/executive/authorization/users/" + encodeURIComponent(user.userId)}
                  >
                    {authorizationText(locale, "openUser")}
                  </Link>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </ScpPage>
  );
}
