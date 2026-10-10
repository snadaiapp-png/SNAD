"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/sds";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import { useAuth } from "@/lib/auth/auth-provider";
import { usersApi, type ModuleProvisioningContext, type ModuleUserAccessProjection, type UserLifecycleAction, type UserResponse, type UserStatus } from "@/lib/api/users";
import { toUserFacingMessage } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { usersMessages } from "@/lib/i18n/users-l10n";
import { GlobalUserProvisioningLauncher } from "@/components/users/GlobalUserProvisioningLauncher";
import { UserCreateDialog } from "./_components/UserCreateDialog";
import { UserDirectory } from "./_components/UserDirectory";
import styles from "./users.module.css";

const TRANSIENT_AUTH_STATES = new Set([
  "INITIALIZING",
  "CHECKING_SESSION",
  "REFRESHING",
  "REFRESHING_SESSION",
  "LOGGING_OUT",
]);

export default function TenantUsersPage() {
  const { state, user, me } = useAuth();
  const router = useRouter();
  const { t } = useI18n();
  const messages = useMemo(() => usersMessages(t), [t]);
  const capabilities = me?.capabilities ?? [];
  const canRead = capabilities.includes("USER.READ");
  const canCreate = capabilities.includes("USER.CREATE");
  const canWrite = capabilities.includes("USER.WRITE");
  const canArchive = capabilities.includes("USER.DELETE");
  const tenantId = user?.tenantId ?? null;

  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<UserStatus | "ALL">("ALL");
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const moduleParams =
    typeof window === "undefined" ? null : new URLSearchParams(window.location.search);
  const moduleRouteRoot = moduleParams?.get("module")?.trim().toLowerCase() || null;
  const moduleReturnTo = moduleParams?.get("returnTo") || null;
  const [moduleContext, setModuleContext] = useState<ModuleProvisioningContext | null>(null);
  const [moduleAccess, setModuleAccess] = useState<Record<string, { assignedRoles: string[]; effectiveCapabilities: string[] }>>({});

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/?returnUrl=%2Fmanagement%2Fusers");
    }
  }, [router, state]);

  const loadUsers = useCallback(async () => {
    if (!tenantId || !canRead) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (moduleRouteRoot) {
        const [allUsers, projectedUsers, context] = await Promise.all([
          usersApi.list(tenantId),
          usersApi.listModuleAccessUsers(moduleRouteRoot),
          usersApi.moduleContext(moduleRouteRoot),
        ]);
        const accessible = new Set(
          projectedUsers
            .filter((entry) => entry.effectiveAccess || entry.assignedRoles.length > 0)
            .map((entry) => entry.userId),
        );
        setUsers(allUsers.filter((entry) => accessible.has(entry.id)));
        setModuleContext(context);
        setModuleAccess(Object.fromEntries(
          projectedUsers.map((entry: ModuleUserAccessProjection) => [
            entry.userId,
            {
              assignedRoles: entry.assignedRoles,
              effectiveCapabilities: entry.effectiveCapabilities,
            },
          ]),
        ));
      } else {
        setUsers(await usersApi.list(tenantId));
        setModuleContext(null);
        setModuleAccess({});
      }
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setLoading(false);
    }
  }, [canRead, moduleRouteRoot, tenantId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    void loadUsers();
  }, [loadUsers, state]);

  if (TRANSIENT_AUTH_STATES.has(state)) return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED" || !tenantId) return <AuthLoadingState phase="workspace" />;

  const createUser = async (input: { email: string; username: string | null; displayName?: string | null; mobileNumber?: string | null; mobileRegion?: string | null; initialCredential?: string }) => {
    setCreating(true);
    setError(null);
    try {
      await usersApi.create(tenantId, input);
      setCreateOpen(false);
      await loadUsers();
      return true;
    } catch (caught) {
      setError(toUserFacingMessage(caught));
      return false;
    } finally {
      setCreating(false);
    }
  };

  const transitionUser = async (userId: string, action: UserLifecycleAction) => {
    setBusyUserId(userId);
    setError(null);
    try {
      await usersApi.transition(tenantId, userId, action);
      await loadUsers();
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <ExecutiveShell>
      <section className={styles.root} data-testid="management-users-ready">
        <header className={styles.header}>
          <div className={styles.headingGroup}>
            <h1 className={styles.title}>
              {moduleContext ? `${messages.title} — ${moduleContext.localizedName || moduleContext.name}` : messages.title}
            </h1>
            <p className={styles.subtitle}>
              {moduleContext
                ? "إدارة المستخدمين الذين لديهم وصول فعّال لهذا الموديول فقط."
                : messages.subtitle}
            </p>
            {moduleContext && moduleReturnTo ? (
              <a className={styles.link} href={moduleReturnTo}>العودة إلى الموديول</a>
            ) : null}
          </div>
          {moduleRouteRoot
            ? <GlobalUserProvisioningLauncher presentation="header" />
            : canCreate
              ? <Button onClick={() => setCreateOpen(true)}>{messages.create}</Button>
              : null}
        </header>

        {!canRead ? (
          <div className={styles.alert} role="alert">{messages.forbidden}</div>
        ) : error ? (
          <div className={styles.alert} role="alert">{error || messages.error}</div>
        ) : (
          <div className={styles.panel}>
            {loading ? (
              <div className={styles.state} role="status">{messages.loading}</div>
            ) : users.length === 0 ? (
              <div className={styles.state}>{messages.empty}</div>
            ) : (
              <UserDirectory
                users={users}
                query={query}
                status={status}
                canWrite={canWrite}
                canArchive={canArchive}
                busyUserId={busyUserId}
                messages={messages}
                onQueryChange={setQuery}
                onStatusChange={setStatus}
                onLifecycle={transitionUser}
                moduleAccess={moduleRouteRoot ? moduleAccess : undefined}
                detailQuery={moduleRouteRoot
                  ? new URLSearchParams({
                      module: moduleRouteRoot,
                      ...(moduleReturnTo ? { returnTo: moduleReturnTo } : {}),
                    }).toString()
                  : undefined}
              />
            )}
          </div>
        )}

        {canCreate && !moduleRouteRoot ? (
          <UserCreateDialog
            open={createOpen}
            busy={creating}
            messages={messages}
            error={error}
            onClose={() => setCreateOpen(false)}
            onSubmit={createUser}
          />
        ) : null}
      </section>
    </ExecutiveShell>
  );
}
