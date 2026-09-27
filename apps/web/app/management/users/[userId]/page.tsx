"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ExecutiveShell } from "@/components/shell";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { usersApi, type UserResponse } from "@/lib/api/users";
import {
  tenantAccessApi,
  type RoleResponse,
  type UserRoleLinkResponse,
} from "@/lib/api/tenant-access";
import type { OrganizationMembershipResponse } from "@/lib/api/memberships";
import { toUserFacingMessage } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";

const COPY = {
  ar: {
    title: "تفاصيل المستخدم",
    email: "البريد الإلكتروني",
    displayName: "الاسم المعروض",
    save: "حفظ التعديلات",
    memberships: "عضويات المؤسسات",
    roles: "الأدوار المسندة",
    role: "الدور",
    grant: "إسناد الدور",
    revoke: "سحب الدور",
    noMemberships: "لا توجد عضويات",
    noRoles: "لا توجد أدوار مسندة",
    loading: "جارٍ تحميل بيانات المستخدم",
    error: "تعذر تحميل بيانات المستخدم",
  },
  en: {
    title: "User details",
    email: "Email",
    displayName: "Display name",
    save: "Save changes",
    memberships: "Organization memberships",
    roles: "Assigned roles",
    role: "Role",
    grant: "Assign role",
    revoke: "Revoke role",
    noMemberships: "No memberships",
    noRoles: "No assigned roles",
    loading: "Loading user details",
    error: "Unable to load user details",
  },
} as const;

const TRANSIENT_AUTH_STATES = new Set([
  "INITIALIZING",
  "CHECKING_SESSION",
  "REFRESHING",
  "REFRESHING_SESSION",
  "LOGGING_OUT",
]);

export default function TenantUserDetailPage() {
  const params = useParams<{ userId: string }>();
  const router = useRouter();
  const { state, user: actor, me } = useAuth();
  const { locale } = useI18n();
  const copy = COPY[locale === "en" ? "en" : "ar"];
  const tenantId = actor?.tenantId ?? null;
  const userId = params.userId;
  const capabilities = me?.capabilities ?? [];
  const canRead = capabilities.includes("USER.READ");
  const canWrite = capabilities.includes("USER.WRITE");
  const canReadMemberships = capabilities.includes("MEMBERSHIP.READ");
  const canReadRoles = capabilities.includes("ROLE.READ");
  const canGrantRole = capabilities.includes("USER.GRANT_ROLE");
  const canRevokeRole = capabilities.includes("USER.REVOKE_ROLE");

  const [target, setTarget] = useState<UserResponse | null>(null);
  const [memberships, setMemberships] = useState<OrganizationMembershipResponse[]>([]);
  const [roleLinks, setRoleLinks] = useState<UserRoleLinkResponse[]>([]);
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [selectedRoleId, setSelectedRoleId] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace(`/?returnUrl=${encodeURIComponent(`/management/users/${userId}`)}`);
    }
  }, [router, state, userId]);

  const load = useCallback(async () => {
    if (!tenantId || !canRead) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [userResult, membershipResult, linkResult, roleResult] = await Promise.all([
        usersApi.get(tenantId, userId),
        canReadMemberships ? tenantAccessApi.listUserMemberships(userId) : Promise.resolve([]),
        canReadRoles ? tenantAccessApi.listUserRoleLinks(tenantId, userId) : Promise.resolve([]),
        canReadRoles ? tenantAccessApi.listRoles(tenantId) : Promise.resolve([]),
      ]);
      setTarget(userResult);
      setEmail(userResult.email);
      setDisplayName(userResult.displayName ?? "");
      setMemberships(membershipResult);
      setRoleLinks(linkResult);
      setRoles(roleResult);
      if (roleResult.length > 0) setSelectedRoleId(roleResult[0].id);
    } catch (caught) {
      setError(toUserFacingMessage(caught) || copy.error);
    } finally {
      setLoading(false);
    }
  }, [canRead, canReadMemberships, canReadRoles, copy.error, tenantId, userId]);

  useEffect(() => {
    if (state === "AUTHENTICATED") void load();
  }, [load, state]);

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
        displayName: displayName.trim() || null,
      });
      await load();
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const grantRole = async () => {
    if (!canGrantRole || !selectedRoleId) return;
    setBusy(true);
    try {
      await tenantAccessApi.grantUserRole(tenantId, userId, selectedRoleId, undefined);
      await load();
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const revokeRole = async (grantId: string) => {
    if (!canRevokeRole) return;
    setBusy(true);
    try {
      await tenantAccessApi.revokeUserRole(tenantId, grantId);
      await load();
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ExecutiveShell>
      <section data-testid="management-user-detail-ready">
        <h1>{copy.title}</h1>
        {error ? <div role="alert">{error}</div> : null}
        {loading ? <div role="status">{copy.loading}</div> : null}
        {!loading && target ? (
          <>
            <h2>{target.displayName || target.email}</h2>
            <form onSubmit={saveIdentity}>
              <label>
                {copy.email}
                <input
                  aria-label={copy.email}
                  type="email"
                  value={email}
                  disabled={!canWrite || busy}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>
              <label>
                {copy.displayName}
                <input
                  aria-label={copy.displayName}
                  value={displayName}
                  disabled={!canWrite || busy}
                  onChange={(event) => setDisplayName(event.target.value)}
                />
              </label>
              {canWrite ? <button type="submit" disabled={busy}>{copy.save}</button> : null}
            </form>

            <section aria-labelledby="user-memberships-heading">
              <h2 id="user-memberships-heading">{copy.memberships}</h2>
              {memberships.length === 0 ? <p>{copy.noMemberships}</p> : (
                <ul>{memberships.map((membership) => (
                  <li key={membership.id}>{membership.displayName || membership.email} — {membership.status}</li>
                ))}</ul>
              )}
            </section>

            <section aria-labelledby="user-roles-heading">
              <h2 id="user-roles-heading">{copy.roles}</h2>
              {roleLinks.length === 0 ? <p>{copy.noRoles}</p> : (
                <ul>{roleLinks.map((link) => (
                  <li key={link.id}>
                    <span>{link.roleCode}</span>
                    {canRevokeRole ? (
                      <button type="button" disabled={busy} onClick={() => void revokeRole(link.id)}>{copy.revoke}</button>
                    ) : null}
                  </li>
                ))}</ul>
              )}
              {canGrantRole ? (
                <div>
                  <label>
                    {copy.role}
                    <select aria-label={copy.role} value={selectedRoleId} onChange={(event) => setSelectedRoleId(event.target.value)}>
                      {roles.map((role) => <option key={role.id} value={role.id}>{role.code}</option>)}
                    </select>
                  </label>
                  <button type="button" disabled={busy || !selectedRoleId} onClick={() => void grantRole()}>{copy.grant}</button>
                </div>
              ) : null}
            </section>
          </>
        ) : null}
      </section>
    </ExecutiveShell>
  );
}
