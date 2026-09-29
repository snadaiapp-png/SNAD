"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useScpAccess } from "../../../_components/ScpAccess";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { authorizationText } from "@/lib/i18n/authorization-l10n";
import {
  effectivePermissions,
  listOverrides,
  listRelationships,
  resync,
  type EffectivePermission,
  type PermissionOverride,
  type SubjectRelationship,
} from "@/lib/api/access-api";

export default function AuthorizationUserPage() {
  const params = useParams<{ id: string }>();
  const userId = params.id;
  const access = useScpAccess();
  const { locale } = useI18n();
  const [overrides, setOverrides] = useState<PermissionOverride[]>([]);
  const [relationships, setRelationships] = useState<SubjectRelationship[]>([]);
  const [effective, setEffective] = useState<EffectivePermission[]>([]);
  const [loading, setLoading] = useState(false);

  const canRead = access.has("ROLE.READ") || access.has("AUTHORIZATION.OVERRIDE.MANAGE") || access.has("AUTHORIZATION.RELATIONSHIP.MANAGE");
  const canManageOverrides = access.has("AUTHORIZATION.OVERRIDE.MANAGE");
  const canManageRelationships = access.has("AUTHORIZATION.RELATIONSHIP.MANAGE");
  const canResync = access.has("AUTHORIZATION.RESYNC");

  const load = useCallback(async () => {
    if (!canRead || !userId) return;
    setLoading(true);
    try {
      const [nextOverrides, nextRelationships, nextEffective] = await Promise.all([
        canManageOverrides ? listOverrides(userId) : Promise.resolve([]),
        canManageRelationships ? listRelationships(userId) : Promise.resolve([]),
        effectivePermissions(userId),
      ]);
      setOverrides(nextOverrides);
      setRelationships(nextRelationships);
      setEffective(nextEffective);
    } finally {
      setLoading(false);
    }
  }, [canRead, canManageOverrides, canManageRelationships, userId]);

  useEffect(() => { void load(); }, [load]);

  if (!canRead) return <main><h1>{authorizationText(locale, "title")}</h1><p>{authorizationText(locale, "noAccess")}</p></main>;

  return (
    <main aria-labelledby="authorization-user-title">
      <header>
        <h1 id="authorization-user-title">{authorizationText(locale, "title")} · {userId}</h1>
        <p>{authorizationText(locale, "overview")} · {authorizationText(locale, "roles")} · {authorizationText(locale, "directPermissions")} · {authorizationText(locale, "dataScopes")} · {authorizationText(locale, "effectivePermissions")} · {authorizationText(locale, "audit")}</p>
      </header>

      <div>
        <button type="button" onClick={() => void load()}>{authorizationText(locale, "refresh")}</button>
        {canResync ? <button type="button" onClick={async () => setEffective(await resync(userId))}>{authorizationText(locale, "resync")}</button> : null}
      </div>

      {loading ? <p>{authorizationText(locale, "loading")}</p> : null}

      <section>
        <h2>{authorizationText(locale, "directPermissions")}</h2>
        {overrides.length === 0 ? <p>{authorizationText(locale, "empty")}</p> : (
          <ul>{overrides.map((item) => <li key={item.id}>{item.capabilityCode} · {item.effect} · {item.scopeType ?? "—"}</li>)}</ul>
        )}
      </section>

      <section>
        <h2>{authorizationText(locale, "dataScopes")}</h2>
        {relationships.length === 0 ? <p>{authorizationText(locale, "empty")}</p> : (
          <ul>{relationships.map((item) => <li key={item.id}>{item.relationshipType} · {item.objectType} · {item.objectId}</li>)}</ul>
        )}
      </section>

      <section>
        <h2>{authorizationText(locale, "effectivePermissions")}</h2>
        {effective.length === 0 ? <p>{authorizationText(locale, "empty")}</p> : (
          <ul>{effective.map((item) => <li key={`${item.capabilityId}:${item.scopeType}:${item.scopeReference ?? "all"}`}>{item.capabilityId} · {item.source} · {item.scopeType}</li>)}</ul>
        )}
      </section>
    </main>
  );
}
