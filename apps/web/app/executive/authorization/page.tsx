"use client";

import Link from "next/link";
import { useState } from "react";
import { useScpAccess } from "../_components/ScpAccess";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { authorizationText } from "@/lib/i18n/authorization-l10n";

const READ_CAPABILITIES = [
  "ROLE.READ",
  "AUTHORIZATION.OVERRIDE.MANAGE",
  "AUTHORIZATION.RELATIONSHIP.MANAGE",
];

export default function AuthorizationPage() {
  const access = useScpAccess();
  const { locale } = useI18n();
  const [userId, setUserId] = useState("");
  const canRead = access.hasAny(READ_CAPABILITIES);

  if (!canRead) {
    return <main aria-labelledby="authorization-title"><h1 id="authorization-title">{authorizationText(locale, "title")}</h1><p>{authorizationText(locale, "noAccess")}</p></main>;
  }

  return (
    <main aria-labelledby="authorization-title">
      <header>
        <h1 id="authorization-title">{authorizationText(locale, "title")}</h1>
        <p>{authorizationText(locale, "subtitle")}</p>
      </header>

      <nav aria-label={authorizationText(locale, "title")}>
        <span>{authorizationText(locale, "users")}</span>{" · "}
        <span>{authorizationText(locale, "roles")}</span>{" · "}
        <span>{authorizationText(locale, "capabilities")}</span>{" · "}
        <span>{authorizationText(locale, "policies")}</span>{" · "}
        <span>{authorizationText(locale, "audit")}</span>
      </nav>

      <section aria-labelledby="authorization-user-lookup">
        <h2 id="authorization-user-lookup">{authorizationText(locale, "users")}</h2>
        <label>
          {authorizationText(locale, "userId")}
          <input value={userId} onChange={(event) => setUserId(event.target.value.trim())} />
        </label>
        {userId ? <Link href={`/executive/authorization/users/${encodeURIComponent(userId)}`}>{authorizationText(locale, "openUser")}</Link> : null}
      </section>
    </main>
  );
}
