"use client";

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ExecutiveShell } from "@/components/shell";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { usersApi, type UserLifecycleAction, type UserResponse } from "@/lib/api/users";
import { createTenantAuthApi } from "@/lib/api/auth";
import {
  tenantAccessApi,
  type RoleResponse,
  type UserRoleLinkResponse,
} from "@/lib/api/tenant-access";
import type { OrganizationMembershipResponse } from "@/lib/api/memberships";
import { effectivePermissions, type EffectivePermission } from "@/lib/api/access-api";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { usersMessages } from "@/lib/i18n/users-l10n";
import { UserLifecycleActions } from "../_components/UserLifecycleActions";
import styles from "./user-detail.module.css";

const TRANSIENT_AUTH_STATES = new Set([
  "INITIALIZING",
  "CHECKING_SESSION",
  "REFRESHING",
  "REFRESHING_SESSION",
  "LOGGING_OUT",
]);

type ScopeKind = "TENANT" | "ORGANIZATION";

interface PendingRevoke {
  grantId: string;
  roleCode: string;
  scopeLabel: string;
}

export default function TenantUserDetailPage() {
  const params = useParams<{ userId: string }>();
  const router = useRouter();
  const { state, user: actor, me } = useAuth();
  const { t, locale = "ar" } = useI18n();
  const messages = useMemo(() => usersMessages(t), [t]);
  const tenantId = actor?.tenantId ?? null;
  const userId = params.userId;
  const capabilities = me?.capabilities ?? [];
  const canRead = capabilities.includes("USER.READ");
  const canWrite = capabilities.includes("USER.WRITE");
  const canArchive = capabilities.includes("USER.DELETE");
  const canReadMemberships = capabilities.includes("MEMBERSHIP.READ");
  const canReadRoles = capabilities.includes("ROLE.READ");
  const canGrantRole = capabilities.includes("USER.GRANT_ROLE");
  const canRevokeRole = capabilities.includes("USER.REVOKE_ROLE");
  const dataScopeKey = `${tenantId ?? "none"}:${userId}:${canRead ? "read" : "deny"}:${canReadMemberships ? "memberships" : "no-memberships"}:${canReadRoles ? "roles" : "no-roles"}`;
  const activeDataScopeRef = useRef(dataScopeKey);
  const loadSequenceRef = useRef(0);
  activeDataScopeRef.current = dataScopeKey;

  const [target, setTarget] = useState<UserResponse | null>(null);
  const [memberships, setMemberships] = useState<OrganizationMembershipResponse[]>([]);
  const [roleLinks, setRoleLinks] = useState<UserRoleLinkResponse[]>([]);
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [effectiveAccess, setEffectiveAccess] = useState<EffectivePermission[]>([]);
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [mobileRegion, setMobileRegion] = useState("");
  const [initialCredential, setInitialCredential] = useState("");
  const [confirmCredential, setConfirmCredential] = useState("");
  const [selectedRoleId, setSelectedRoleId] = useState("");
  const [selectedScope, setSelectedScope] = useState<ScopeKind>("TENANT");
  const [selectedOrganizationId, setSelectedOrganizationId] = useState("");
  const [pendingRevoke, setPendingRevoke] = useState<PendingRevoke | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [credentialNotice, setCredentialNotice] = useState<string | null>(null);

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace(`/?returnUrl=${encodeURIComponent(`/management/users/${userId}`)}`);
    }
  }, [router, state, userId]);

  // Reset transient mutation state when the actor's tenant changes (G7).
  // This prevents a previously selected role / scope / organization from
  // leaking across tenant boundaries. The backend remains the sole
  // authority — this is purely a UX safeguard so the operator does not
  // accidentally submit a stale selection against the new tenant.
  useEffect(() => {
    setSelectedRoleId("");
    setSelectedScope("TENANT");
    setSelectedOrganizationId("");
    setPendingRevoke(null);
  }, [tenantId]);

  const load = useCallback(async () => {
    const requestedScope = dataScopeKey;
    if (activeDataScopeRef.current !== requestedScope) return;
    const sequence = ++loadSequenceRef.current;

    // Fail closed across actor/tenant/user/capability transitions: never keep
    // prior scoped data mounted while a new request is pending or denied.
    setTarget(null);
    setMemberships([]);
    setRoleLinks([]);
    setRoles([]);
    setEffectiveAccess([]);
    setLoading(true);
    setError(null);
    if (!tenantId || !canRead) {
      setLoading(false);
      return;
    }
    try {
      const [userResult, membershipResult, linkResult, roleResult, effectiveResult] = await Promise.all([
        usersApi.get(tenantId, userId),
        canReadMemberships ? tenantAccessApi.listUserMemberships(userId) : Promise.resolve([]),
        canReadRoles ? tenantAccessApi.listUserRoleLinks(tenantId, userId) : Promise.resolve([]),
        canReadRoles ? tenantAccessApi.listRoles(tenantId) : Promise.resolve([]),
        canReadRoles ? effectivePermissions(userId) : Promise.resolve([]),
      ]);
      if (sequence !== loadSequenceRef.current || activeDataScopeRef.current !== requestedScope) return;
      setTarget(userResult);
      setEmail(userResult.email);
      setUsername(userResult.username ?? "");
      setDisplayName(userResult.displayName ?? "");
      setMobileNumber(userResult.mobileNumber ?? "");
      setMobileRegion(userResult.mobileRegion ?? "");
      setMemberships(membershipResult);
      setRoleLinks(linkResult);
      setRoles(roleResult);
      setEffectiveAccess(effectiveResult);
      setSelectedRoleId((currentRoleId) =>
        currentRoleId && roleResult.some((role) => role.id === currentRoleId)
          ? currentRoleId
          : (roleResult[0]?.id ?? "")
      );
    } catch (caught) {
      if (sequence === loadSequenceRef.current && activeDataScopeRef.current === requestedScope) {
        setError(toUserFacingError(caught));
      }
    } finally {
      if (sequence === loadSequenceRef.current && activeDataScopeRef.current === requestedScope) {
        setLoading(false);
      }
    }
  }, [canRead, canReadMemberships, canReadRoles, dataScopeKey, tenantId, userId]);

  useEffect(() => {
    if (state === "AUTHENTICATED") void load();
  }, [load, state]);

  const transitionUser = async (action: UserLifecycleAction) => {
    const permitted = action === "archive" ? canArchive : canWrite;
    if (!target || !permitted) return;
    setBusy(true);
    setError(null);
    try {
      await usersApi.transition(tenantId, userId, action);
      await load();
    } catch (caught) {
      setError(toUserFacingError(caught));
    } finally {
      setBusy(false);
    }
  };

  const organizationOptions = useMemo(() => {
    // Organization scope choices are derived exclusively from the user's
    // existing memberships — these are tenant-bound and backend-authoritative.
    // No synthetic organization id is ever fabricated on the frontend.
    return memberships.filter((m) => m.organizationId).map((m) => ({
      id: m.organizationId,
      label: m.displayName || m.email,
    }));
  }, [memberships]);

  const scopeOptions = useMemo<ReadonlyArray<{ value: ScopeKind; label: string }>>(() => [
    { value: "TENANT", label: t("management.users.detail.scopeTenant") },
    { value: "ORGANIZATION", label: t("management.users.detail.scopeOrganization") },
  ], [t]);

  if (TRANSIENT_AUTH_STATES.has(state)) return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED" || !tenantId) return <AuthLoadingState phase="workspace" />;

  const saveIdentity = async (event: FormEvent) => {
    event.preventDefault();
    if (!target || !canWrite) return;
    setBusy(true);
    setError(null);
    try {
      await usersApi.update(tenantId, userId, {
        email,
        username: username.trim() || null,
        displayName: displayName.trim() || null,
        mobileNumber: mobileNumber.trim() || null,
        mobileRegion: mobileRegion.trim() || null,
      });
      await load();
    } catch (caught) {
      setError(toUserFacingError(caught));
    } finally {
      setBusy(false);
    }
  };

  const initializeCredential = async () => {
    if (!canWrite || !target || target.status !== "ACTIVE" || target.credentialInitialized) return;
    if (initialCredential !== confirmCredential) {
      setCredentialNotice(null);
      setError({ title: t("management.users.credentials.mismatch"), message: t("management.users.credentials.mismatch"), kind: "validation" });
      return;
    }
    setBusy(true);
    setError(null);
    setCredentialNotice(null);
    try {
      const credentialApi = createTenantAuthApi(tenantId);
      await credentialApi.adminInitializeCredential(userId, { initialCredential });
      setInitialCredential("");
      setConfirmCredential("");
      setCredentialNotice(t("management.users.credentials.initializeSuccess"));
      await load();
    } catch (caught) {
      setError(toUserFacingError(caught));
    } finally {
      setBusy(false);
    }
  };

  const sendSetPasswordLink = async () => {
    if (!canWrite) return;
    setBusy(true);
    setError(null);
    setCredentialNotice(null);
    try {
      const credentialApi = createTenantAuthApi(tenantId);
      await credentialApi.adminResetPassword(userId, { locale });
      setCredentialNotice(t("management.users.credentials.resetSuccess"));
    } catch (caught) {
      setError(toUserFacingError(caught));
    } finally {
      setBusy(false);
    }
  };

  const grantRole = async () => {
    if (!canGrantRole || !selectedRoleId) return;
    // Fail-closed scope contract: only TENANT and ORGANIZATION scopes are
    // offered. Organization scope requires a canonical organizationId that
    // the backend validates against the authenticated tenant. No other
    // scope type is exposable from the Users workspace.
    const organizationId = selectedScope === "ORGANIZATION" ? selectedOrganizationId : undefined;
    if (selectedScope === "ORGANIZATION" && !organizationId) return;
    setBusy(true);
    setError(null);
    try {
      await tenantAccessApi.grantUserRole(tenantId, userId, selectedRoleId, organizationId);
      await load();
    } catch (caught) {
      setError(toUserFacingError(caught));
    } finally {
      setBusy(false);
    }
  };

  const requestRevoke = (link: UserRoleLinkResponse) => {
    if (!canRevokeRole) return;
    const scopeLabel = link.organizationId
      ? t("management.users.detail.scopeOrganizationLabel")
      : t("management.users.detail.scopeTenantLabel");
    setPendingRevoke({ grantId: link.id, roleCode: link.roleCode, scopeLabel });
  };

  const cancelRevoke = () => setPendingRevoke(null);

  const confirmRevoke = async () => {
    if (!pendingRevoke || !canRevokeRole) return;
    setBusy(true);
    setError(null);
    try {
      await tenantAccessApi.revokeUserRole(tenantId, pendingRevoke.grantId);
      setPendingRevoke(null);
      await load();
    } catch (caught) {
      setError(toUserFacingError(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ExecutiveShell>
      <section className={styles.root} data-testid="management-user-detail-ready">
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>{t("management.users.detail.title")}</h1>
        </header>
        {!canRead ? (
          <div role="alert">{messages.forbidden}</div>
        ) : error ? (
          <div role="alert">
            <h2>{error.title}</h2>
            <p>{error.message}</p>
          </div>
        ) : null}
        {loading ? <div role="status">{t("management.users.detail.loading")}</div> : null}
        {!loading && target ? (
          <>
            <div className={styles.userHeadingRow}>
              <div>
                <h2 className={styles.userHeading}>{target.displayName || target.email}</h2>
                <span className={styles.statusBadge}>{messages[`status_${target.status}` as keyof typeof messages]}</span>
              </div>
              <div className={styles.lifecycleActions} data-testid="management-user-lifecycle">
                <UserLifecycleActions
                  status={target.status}
                  canWrite={canWrite}
                  canArchive={canArchive}
                  busy={busy}
                  messages={messages}
                  onAction={(action) => void transitionUser(action)}
                />
              </div>
            </div>
            <form className={styles.identityForm} data-testid="management-user-identity" onSubmit={saveIdentity}>
              <label>
                {t("users.email")}
                <input aria-label={t("users.email")} type="email" value={email} disabled={!canWrite || busy} onChange={(event) => setEmail(event.target.value)} />
              </label>
              <label>
                {t("users.username")}
                <input
                  aria-label={t("users.username")}
                  autoComplete="username"
                  value={username}
                  disabled={!canWrite || busy}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </label>
              <label>
                {t("users.displayName")}
                <input aria-label={t("users.displayName")} value={displayName} disabled={!canWrite || busy} onChange={(event) => setDisplayName(event.target.value)} />
              </label>
              <label>
                {t("users.mobileNumber")}
                <input aria-label={t("users.mobileNumber")} value={mobileNumber} disabled={!canWrite || busy} onChange={(event) => setMobileNumber(event.target.value)} />
              </label>
              <label>
                {t("users.mobileRegion")}
                <input aria-label={t("users.mobileRegion")} maxLength={2} value={mobileRegion} disabled={!canWrite || busy} onChange={(event) => setMobileRegion(event.target.value)} />
              </label>
              {canWrite ? <button type="submit" disabled={busy}>{t("management.users.detail.save")}</button> : null}
            </form>

            <section className={styles.card} aria-labelledby="user-credentials-heading" data-testid="management-user-credentials">
              <h2 id="user-credentials-heading">{t("management.users.credentials.title")}</h2>
              <p>{t("management.users.credentials.help")}</p>
              <dl className={styles.statsGrid}>
                <div>
                  <dt>{t("users.username")}</dt>
                  <dd>{target.username || t("management.users.credentials.notSet")}</dd>
                </div>
                <div>
                  <dt>{t("users.email")}</dt>
                  <dd>{target.email}</dd>
                </div>
                <div>
                  <dt>{t("management.users.credentials.initialized")}</dt>
                  <dd>{target.credentialInitialized ? t("management.users.credentials.yes") : t("management.users.credentials.no")}</dd>
                </div>
                <div>
                  <dt>{t("management.users.credentials.rotation")}</dt>
                  <dd>{target.credentialRotationRequired ? t("management.users.credentials.yes") : t("management.users.credentials.no")}</dd>
                </div>
                <div>
                  <dt>{t("management.users.credentials.lastLogin")}</dt>
                  <dd>{target.lastLoginAt || t("management.users.credentials.never")}</dd>
                </div>
              </dl>
              {credentialNotice ? <p role="status">{credentialNotice}</p> : null}
              {canWrite && target.status === "ACTIVE" && !target.credentialInitialized ? (
                <div className={styles.credentialActions}>
                  <label>
                    {t("management.users.credentials.initial")}
                    <input
                      aria-label={t("management.users.credentials.initial")}
                      type="password"
                      autoComplete="new-password"
                      value={initialCredential}
                      disabled={busy}
                      onChange={(event) => setInitialCredential(event.target.value)}
                    />
                  </label>
                  <label>
                    {t("management.users.credentials.confirm")}
                    <input
                      aria-label={t("management.users.credentials.confirm")}
                      type="password"
                      autoComplete="new-password"
                      value={confirmCredential}
                      disabled={busy}
                      onChange={(event) => setConfirmCredential(event.target.value)}
                    />
                  </label>
                  <button type="button" disabled={busy || initialCredential.length < 8 || confirmCredential.length < 8} onClick={() => void initializeCredential()}>
                    {t("management.users.credentials.initialize")}
                  </button>
                </div>
              ) : null}
              {canWrite && target.credentialInitialized ? (
                <button type="button" disabled={busy} onClick={() => void sendSetPasswordLink()}>
                  {t("management.users.credentials.reset")}
                </button>
              ) : null}
            </section>

            {canReadMemberships ? <section className={styles.card} aria-labelledby="user-memberships-heading" data-testid="management-user-memberships">
              <h2 id="user-memberships-heading">{t("management.users.detail.memberships")}</h2>
              {memberships.length === 0 ? <p className={styles.emptyState}>{t("management.users.detail.noMemberships")}</p> : <ul className={styles.list}>{memberships.map((membership) => <li className={styles.listItem} key={membership.id}>{membership.displayName || membership.email} — {membership.status}</li>)}</ul>}
            </section> : null}

            {canReadRoles ? <section className={styles.card} aria-labelledby="user-roles-heading" data-testid="management-user-roles">
              <h2 id="user-roles-heading">{t("management.users.detail.roles")}</h2>
              {roleLinks.length === 0 ? <p className={styles.emptyState}>{t("management.users.detail.noRoles")}</p> : (
                <ul className={styles.list}>{roleLinks.map((link) => (
                  <li className={styles.roleItem} key={link.id}>
                    <span>{link.roleCode}</span>
                    <span>{link.organizationId ? t("management.users.detail.scopeOrganizationLabel") : t("management.users.detail.scopeTenantLabel")}</span>
                    {canRevokeRole ? <button type="button" disabled={busy} onClick={() => requestRevoke(link)}>{t("management.users.detail.revoke")}</button> : null}
                  </li>
                ))}</ul>
              )}
              {canGrantRole ? (
                <div className={styles.grantGrid}>
                  <label>
                    {t("management.users.detail.role")}
                    <select aria-label={t("management.users.detail.role")} value={selectedRoleId} onChange={(event) => setSelectedRoleId(event.target.value)}>
                      {roles.map((role) => <option key={role.id} value={role.id}>{role.code}</option>)}
                    </select>
                  </label>
                  <label>
                    {t("management.users.detail.scope")}
                    <select
                      aria-label={t("management.users.detail.scope")}
                      value={selectedScope}
                      onChange={(event) => setSelectedScope(event.target.value as ScopeKind)}
                    >
                      {scopeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </select>
                  </label>
                  {selectedScope === "ORGANIZATION" ? (
                    <label>
                      {t("management.users.detail.scopeOrganizationPlaceholder")}
                      <select
                        aria-label={t("management.users.detail.scopeOrganizationPlaceholder")}
                        value={selectedOrganizationId}
                        onChange={(event) => setSelectedOrganizationId(event.target.value)}
                      >
                        <option value="">{t("management.users.detail.scopeOrganizationPlaceholder")}</option>
                        {organizationOptions.map((org) => <option key={org.id} value={org.id}>{org.label}</option>)}
                      </select>
                    </label>
                  ) : null}
                  <button
                    type="button"
                    disabled={busy || !selectedRoleId || (selectedScope === "ORGANIZATION" && !selectedOrganizationId)}
                    onClick={() => void grantRole()}
                  >
                    {t("management.users.detail.grant")}
                  </button>
                </div>
              ) : null}
            </section> : null}

            {canReadRoles ? <section className={styles.card} aria-labelledby="user-effective-access-heading" data-testid="management-user-effective-access">
              <h2 id="user-effective-access-heading">{t("management.users.detail.effectiveAccess")}</h2>
              <p>{t("management.users.detail.effectiveAccessCount")}: {effectiveAccess.length}</p>
              {effectiveAccess.length === 0 ? <p className={styles.emptyState}>{t("management.users.detail.effectiveAccessEmpty")}</p> : (
                <ul className={styles.accessList}>{effectiveAccess.map((perm) => (
                  <li className={styles.accessItem} key={`${perm.capabilityId}-${perm.effect}-${perm.scopeType}-${perm.scopeReference ?? ""}-${perm.source}`}>
                    {perm.capabilityId} — {perm.scopeType}{perm.scopeReference ? ` (${perm.scopeReference})` : ""} — {perm.source}
                    {" "}
                    {/* Backend-authoritative effect: the frontend never computes
                        authorization outcomes or deny precedence itself. */}
                    <strong data-testid="effective-access-effect">{perm.effect}</strong>
                    {perm.matchedRoleId ? (
                      <>
                        {" "}— {t("management.users.detail.effectiveAccessRoleOrigin")}: {perm.matchedRoleId}
                      </>
                    ) : null}
                    {perm.reason ? (
                      <>
                        {" "}— {t("management.users.detail.effectiveAccessReason")}: {perm.reason}
                      </>
                    ) : null}
                  </li>
                ))}</ul>
              )}
            </section> : null}
          </>
        ) : null}

        {pendingRevoke ? (
          <div className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="revoke-confirm-title">
            <h2 id="revoke-confirm-title">{t("management.users.detail.revokeConfirmTitle")}</h2>
            <p>{t("management.users.detail.revokeConfirmMessage")}</p>
            <dl>
              <div>
                <dt>{t("management.users.detail.role")}</dt>
                <dd>{pendingRevoke.roleCode}</dd>
              </div>
              <div>
                <dt>{t("management.users.detail.scope")}</dt>
                <dd>{pendingRevoke.scopeLabel}</dd>
              </div>
            </dl>
            <button type="button" disabled={busy} onClick={() => void confirmRevoke()}>
              {t("management.users.detail.revokeConfirmApply")}
            </button>
            <button type="button" disabled={busy} onClick={cancelRevoke}>
              {t("management.users.detail.revokeConfirmCancel")}
            </button>
          </div>
        ) : null}
      </section>
    </ExecutiveShell>
  );
}
