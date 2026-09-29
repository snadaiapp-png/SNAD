"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useScpAccess } from "../../../_components/ScpAccess";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { authorizationText } from "@/lib/i18n/authorization-l10n";
import { effectivePermissions, listOverrides, listRelationships, resync, type EffectivePermission, type PermissionOverride, type SubjectRelationship } from "@/lib/api/access-api";

export default function AuthorizationUserPage() {
  const params = useParams<{ id: string }>();
  const userId = params.id;
  const access = useScpAccess();
  const { locale } = useI18n();
  const [overrides, setOverrides] = useState<PermissionOverride[]>([]);
  const [relationships, setRelationships] = useState<SubjectRelationship[]>([]);
  const [effective, setEffective] = useState<EffectivePermission[]>([]);
  const [loading, setLoading] = useState(false);

  const canReadEffective = access.has("ROLE.READ");
  const canManageOverrides = access.has("AUTHORIZATION.OVERRIDE.MANAGE");
  const canManageRelationships = access.has("AUTHORIZATION.RELATIONSHIP.MANAGE");
  const canResync = access.has("AUTHORIZATION.RESYNC");
  const canRead = canReadEffective || canManageOverrides || canManageRelationships;

  const load = useCallback(async () => {
    if (!canRead || !userId) return;
    setLoading(true);
    try {
      const [nextOverrides, nextRelationships, nextEffective] = await Promise.all([
        canManageOverrides ? listOverrides(userId) : Promise.resolve([]),
        canManageRelationships ? listRelationships(userId) : Promise.resolve([]),
        canReadEffective ? effectivePermissions(userId) : Promise.resolve([]),
      ]);
      setOverrides(nextOverrides); setRelationships(nextRelationships); setEffective(nextEffective);
    } finally { setLoading(false); }
  }, [canRead, canReadEffective, canManageOverrides, canManageRelationships, userId]);

  useEffect(() => { void load(); }, [load]);
  if (!canRead) return <main><h1>{authorizationText(locale, "title")}</h1><p>{authorizationText(locale, "noAccess")}</p></main>;

  return <main aria-labelledby="authorization-user-title">
    <header><h1 id="authorization-user-title">{authorizationText(locale, "title")} · {userId}</h1><p>{authorizationText(locale, "overview")} · {authorizationText(locale, "roles")} · {authorizationText(locale, "directPermissions")} · {authorizationText(locale, "dataScopes")} · {authorizationText(locale, "effectivePermissions")} · {authorizationText(locale, "audit")}</p></header>
    <div><button type="button" onClick={() => void load()}>{authorizationText(locale, "refresh")}</button>{canResync ? <button type="button" onClick={async () => setEffective(await resync(userId))}>{authorizationText(locale, "resync")}</button> : null}</div>
    {loading ? <p>{authorizationText(locale, "loading")}</p> : null}
    {canManageOverrides ? <section><h2>{authorizationText(locale, "directPermissions")}</h2>{overrides.length===0?<p>{authorizationText(locale,"empty")}</p>:<ul>{overrides.map((i)=><li key={i.id}>{i.capabilityCode} · {i.effect} · {i.scopeType??"—"}</li>)}</ul>}</section>:null}
    {canManageRelationships ? <section><h2>{authorizationText(locale, "dataScopes")}</h2>{relationships.length===0?<p>{authorizationText(locale,"empty")}</p>:<ul>{relationships.map((i)=><li key={i.id}>{i.relationshipType} · {i.objectType} · {i.objectId}</li>)}</ul>}</section>:null}
    {canReadEffective ? <section><h2>{authorizationText(locale, "effectivePermissions")}</h2>{effective.length===0?<p>{authorizationText(locale,"empty")}</p>:<ul>{effective.map((i)=><li key={`${i.capabilityId}:${i.scopeType}:${i.scopeReference??"all"}`}>{i.capabilityId} · {i.source} · {i.scopeType}</li>)}</ul>}</section>:null}
  </main>;
}
