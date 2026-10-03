"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useParams } from "next/navigation";
import { Button } from "@/components/sds/Button";
import { Input } from "@/components/sds/Input";
import {
  createOverride,
  createRelationship,
  effectivePermissions,
  listOverrides,
  listRelationships,
  resync,
  revokeOverride,
  revokeRelationship,
  type EffectivePermission,
  type OverrideEffect,
  type PermissionOverride,
  type SubjectRelationship,
} from "@/lib/api/access-api";
import { scpApi, type PlatformUser } from "@/lib/api/scp-platform-iam-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { authorizationText } from "@/lib/i18n/authorization-l10n";
import { useScpAccess } from "../../../_components/ScpAccess";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../../../_components/ScpStates";
import { scpErrorMessage } from "../../../_components/scp-errors";
import styles from "../../../scp.module.css";

function toIsoOrNull(value: string): string | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

export default function AuthorizationUserPage() {
  const params = useParams<{ id: string }>();
  const userId = params.id;
  const access = useScpAccess();
  const { locale } = useI18n();

  const canReadEffective = access.has("ROLE.READ");
  const canManageOverrides = access.has("AUTHORIZATION.OVERRIDE.MANAGE");
  const canManageRelationships = access.has("AUTHORIZATION.RELATIONSHIP.MANAGE");
  const canResync = access.has("AUTHORIZATION.RESYNC");
  const canReadUsers = access.has("PLATFORM.USER.READ");
  const canReadPlatformAccess =
    access.has("PLATFORM.ROLE.READ") || access.has("PLATFORM.PERMISSION.READ");
  const canRead = canReadEffective || canManageOverrides || canManageRelationships;

  const [identity, setIdentity] = useState<PlatformUser | null>(null);
  const [overrides, setOverrides] = useState<PermissionOverride[]>([]);
  const [relationships, setRelationships] = useState<SubjectRelationship[]>([]);
  const [effective, setEffective] = useState<EffectivePermission[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const [overrideCapability, setOverrideCapability] = useState("");
  const [overrideEffect, setOverrideEffect] = useState<OverrideEffect>("ALLOW");
  const [overrideScopeType, setOverrideScopeType] = useState("");
  const [overrideScopeReference, setOverrideScopeReference] = useState("");
  const [overrideReason, setOverrideReason] = useState("");
  const [overrideValidUntil, setOverrideValidUntil] = useState("");

  const [relationshipType, setRelationshipType] = useState("");
  const [objectType, setObjectType] = useState("");
  const [objectId, setObjectId] = useState("");
  const [relationshipReason, setRelationshipReason] = useState("");
  const [relationshipValidUntil, setRelationshipValidUntil] = useState("");

  const load = useCallback(async () => {
    if (access.phase !== "authorized" || !canRead || !userId) return;
    setError("");
    setLoaded(false);
    try {
      const [nextIdentity, nextOverrides, nextRelationships, nextEffective] = await Promise.all([
        canReadUsers ? scpApi.platformUser(userId) : Promise.resolve(null),
        canManageOverrides ? listOverrides(userId) : Promise.resolve([]),
        canManageRelationships ? listRelationships(userId) : Promise.resolve([]),
        canReadEffective ? effectivePermissions(userId) : Promise.resolve([]),
      ]);
      setIdentity(nextIdentity);
      setOverrides(nextOverrides);
      setRelationships(nextRelationships);
      setEffective(nextEffective);
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setLoaded(true);
    }
  }, [
    access.phase,
    canManageOverrides,
    canManageRelationships,
    canRead,
    canReadEffective,
    canReadUsers,
    userId,
  ]);

  useEffect(() => {
    void load();
  }, [load]);

  async function submitOverride(event: FormEvent) {
    event.preventDefault();
    if (!overrideCapability.trim() || !overrideReason.trim()) return;
    setBusy(true);
    setError("");
    try {
      await createOverride({
        targetUserId: userId,
        capabilityCode: overrideCapability.trim(),
        effect: overrideEffect,
        scopeType: overrideScopeType.trim() || null,
        scopeReference: overrideScopeReference.trim() || null,
        reason: overrideReason.trim(),
        validFrom: null,
        validUntil: toIsoOrNull(overrideValidUntil),
      });
      setOverrideCapability("");
      setOverrideScopeType("");
      setOverrideScopeReference("");
      setOverrideReason("");
      setOverrideValidUntil("");
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function removeOverride(overrideId: string) {
    setBusy(true);
    setError("");
    try {
      await revokeOverride(overrideId);
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function submitRelationship(event: FormEvent) {
    event.preventDefault();
    if (!relationshipType.trim() || !objectType.trim() || !objectId.trim()) return;
    setBusy(true);
    setError("");
    try {
      await createRelationship({
        subjectUserId: userId,
        relationshipType: relationshipType.trim(),
        objectType: objectType.trim(),
        objectId: objectId.trim(),
        validUntil: toIsoOrNull(relationshipValidUntil),
        reason: relationshipReason.trim() || null,
      });
      setRelationshipType("");
      setObjectType("");
      setObjectId("");
      setRelationshipReason("");
      setRelationshipValidUntil("");
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function removeRelationship(relationshipId: string) {
    setBusy(true);
    setError("");
    try {
      await revokeRelationship(relationshipId, authorizationText(locale, "revocationReason"));
      await load();
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function resyncEffective() {
    setBusy(true);
    setError("");
    try {
      setEffective(await resync(userId));
    } catch (reason) {
      setError(scpErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  if (access.phase === "checking") {
    return <ScpPage title={authorizationText(locale, "title")}><ScpSkeleton lines={6} /></ScpPage>;
  }

  if (access.phase === "degraded") {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpError message={authorizationText(locale, "accessCheckFailed")} onRetry={access.refresh} />
      </ScpPage>
    );
  }

  if (access.phase === "unauthorized" || !canRead) {
    return (
      <ScpPage title={authorizationText(locale, "title")}>
        <ScpError message={authorizationText(locale, "noAccess")} />
      </ScpPage>
    );
  }

  if (!loaded) {
    return <ScpPage title={authorizationText(locale, "title")}><ScpSkeleton lines={7} /></ScpPage>;
  }

  return (
    <ScpPage
      title={identity?.displayName || identity?.email || authorizationText(locale, "userAccess")}
      subtitle={authorizationText(locale, "userAccessSubtitle")}
    >
      <div data-testid="executive-authorization-user-ready">
        {error ? <ScpError message={error} onRetry={load} /> : null}

        <section className={styles.panel}>
          <h2>{authorizationText(locale, "identity")}</h2>
          {identity ? (
            <>
              <strong>{identity.displayName || identity.email}</strong>
              <span className={styles.appCardMeta}>{identity.email}</span>
              <div className={styles.filters}>
                <ScpStatusPill value={identity.accountStatus} />
                <ScpStatusPill value={identity.membershipStatus} />
              </div>
            </>
          ) : (
            <p className={styles.pageSubtitle}>{authorizationText(locale, "identityUnavailable")}</p>
          )}
          <div className={styles.filters}>
            <Link className={styles.navLink} href="/executive/authorization">
              {authorizationText(locale, "backToAuthorization")}
            </Link>
            {canReadUsers ? (
              <Link className={styles.navLink} href={"/executive/users/" + encodeURIComponent(userId)}>
                {authorizationText(locale, "openCanonicalUser")}
              </Link>
            ) : null}
            {canReadPlatformAccess ? (
              <Link className={styles.navLink} href="/executive/access">
                {authorizationText(locale, "manageRoles")}
              </Link>
            ) : null}
          </div>
        </section>

        {canManageOverrides ? (
          <section className={styles.panel}>
            <h2>{authorizationText(locale, "directPermissions")}</h2>
            {overrides.length === 0 ? (
              <ScpEmpty message={authorizationText(locale, "empty")} />
            ) : (
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>{authorizationText(locale, "capability")}</th>
                      <th>{authorizationText(locale, "effect")}</th>
                      <th>{authorizationText(locale, "scope")}</th>
                      <th>{authorizationText(locale, "expires")}</th>
                      <th>{authorizationText(locale, "actions")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {overrides.map((item) => (
                      <tr key={item.id}>
                        <td data-label={authorizationText(locale, "capability")}>{item.capabilityCode}</td>
                        <td data-label={authorizationText(locale, "effect")}>{item.effect}</td>
                        <td data-label={authorizationText(locale, "scope")}>
                          {item.scopeType || authorizationText(locale, "globalScope")}
                          {item.scopeReference ? " · " + item.scopeReference : ""}
                        </td>
                        <td data-label={authorizationText(locale, "expires")}>
                          {item.validUntil || authorizationText(locale, "noExpiry")}
                        </td>
                        <td data-label={authorizationText(locale, "actions")}>
                          <Button
                            size="sm"
                            variant="secondary"
                            disabled={busy}
                            onClick={() => void removeOverride(item.id)}
                          >
                            {authorizationText(locale, "revoke")}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <form className={styles.panel} onSubmit={(event) => void submitOverride(event)}>
              <h3>{authorizationText(locale, "addOverride")}</h3>
              <label>
                <span>{authorizationText(locale, "capability")}</span>
                <Input required value={overrideCapability} onChange={(event) => setOverrideCapability(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "effect")}</span>
                <select value={overrideEffect} onChange={(event) => setOverrideEffect(event.target.value as OverrideEffect)}>
                  <option value="ALLOW">{authorizationText(locale, "allow")}</option>
                  <option value="DENY">{authorizationText(locale, "deny")}</option>
                </select>
              </label>
              <label>
                <span>{authorizationText(locale, "scopeType")}</span>
                <Input value={overrideScopeType} onChange={(event) => setOverrideScopeType(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "scopeReference")}</span>
                <Input value={overrideScopeReference} onChange={(event) => setOverrideScopeReference(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "reason")}</span>
                <Input required value={overrideReason} onChange={(event) => setOverrideReason(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "validUntil")}</span>
                <Input type="datetime-local" value={overrideValidUntil} onChange={(event) => setOverrideValidUntil(event.target.value)} />
              </label>
              <Button type="submit" size="sm" variant="primary" loading={busy}>
                {authorizationText(locale, "create")}
              </Button>
            </form>
          </section>
        ) : null}

        {canManageRelationships ? (
          <section className={styles.panel}>
            <h2>{authorizationText(locale, "dataScopes")}</h2>
            {relationships.length === 0 ? (
              <ScpEmpty message={authorizationText(locale, "empty")} />
            ) : (
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>{authorizationText(locale, "relationship")}</th>
                      <th>{authorizationText(locale, "objectType")}</th>
                      <th>{authorizationText(locale, "objectId")}</th>
                      <th>{authorizationText(locale, "expires")}</th>
                      <th>{authorizationText(locale, "actions")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {relationships.map((item) => (
                      <tr key={item.id}>
                        <td data-label={authorizationText(locale, "relationship")}>{item.relationshipType}</td>
                        <td data-label={authorizationText(locale, "objectType")}>{item.objectType}</td>
                        <td data-label={authorizationText(locale, "objectId")}>{item.objectId}</td>
                        <td data-label={authorizationText(locale, "expires")}>
                          {item.validUntil || authorizationText(locale, "noExpiry")}
                        </td>
                        <td data-label={authorizationText(locale, "actions")}>
                          <Button
                            size="sm"
                            variant="secondary"
                            disabled={busy}
                            onClick={() => void removeRelationship(item.id)}
                          >
                            {authorizationText(locale, "revoke")}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <form className={styles.panel} onSubmit={(event) => void submitRelationship(event)}>
              <h3>{authorizationText(locale, "addRelationship")}</h3>
              <label>
                <span>{authorizationText(locale, "relationshipType")}</span>
                <Input required value={relationshipType} onChange={(event) => setRelationshipType(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "objectType")}</span>
                <Input required value={objectType} onChange={(event) => setObjectType(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "objectId")}</span>
                <Input required value={objectId} onChange={(event) => setObjectId(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "reason")}</span>
                <Input value={relationshipReason} onChange={(event) => setRelationshipReason(event.target.value)} />
              </label>
              <label>
                <span>{authorizationText(locale, "validUntil")}</span>
                <Input type="datetime-local" value={relationshipValidUntil} onChange={(event) => setRelationshipValidUntil(event.target.value)} />
              </label>
              <Button type="submit" size="sm" variant="primary" loading={busy}>
                {authorizationText(locale, "create")}
              </Button>
            </form>
          </section>
        ) : null}

        {canReadEffective ? (
          <section className={styles.panel}>
            <div className={styles.filters}>
              <h2>{authorizationText(locale, "effectivePermissions")}</h2>
              <Button size="sm" variant="secondary" disabled={!canResync || busy} onClick={() => void resyncEffective()}>
                {authorizationText(locale, "resync")}
              </Button>
            </div>
            {effective.length === 0 ? (
              <ScpEmpty message={authorizationText(locale, "empty")} />
            ) : (
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>{authorizationText(locale, "capability")}</th>
                      <th>{authorizationText(locale, "source")}</th>
                      <th>{authorizationText(locale, "scope")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {effective.map((item) => (
                      <tr key={item.capabilityId + ":" + item.scopeType + ":" + (item.scopeReference ?? "all")}>
                        <td data-label={authorizationText(locale, "capability")}>{item.capabilityId}</td>
                        <td data-label={authorizationText(locale, "source")}>{item.source}</td>
                        <td data-label={authorizationText(locale, "scope")}>
                          {item.scopeType}
                          {item.scopeReference ? " · " + item.scopeReference : ""}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ) : null}
      </div>
    </ScpPage>
  );
}
