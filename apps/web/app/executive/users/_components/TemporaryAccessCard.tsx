"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/sds";
import { scpApi, type PlatformCapability, type PlatformTemporaryAccess } from "@/lib/api/scp-platform-iam-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpAccess } from "../../_components/ScpAccess";
import { ScpEmpty, ScpError, ScpSkeleton, ScpStatusPill } from "../../_components/ScpStates";
import { scpErrorMessage } from "../../_components/scp-errors";
import styles from "../../scp.module.css";

export function TemporaryAccessCard({ userId }: { userId: string }) {
  const { t } = useI18n();
  const { has } = useScpAccess();
  const canRead = has("PLATFORM.PERMISSION.READ");
  const canManage = has("PLATFORM.PERMISSION.MANAGE");
  const [grants, setGrants] = useState<PlatformTemporaryAccess[] | null>(null);
  const [capabilities, setCapabilities] = useState<PlatformCapability[]>([]);
  const [capabilityId, setCapabilityId] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");
  const [reason, setReason] = useState("");
  const [revokeId, setRevokeId] = useState<string | null>(null);
  const [revokeReason, setRevokeReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!canRead) return;
    setError("");
    try {
      const [rows, catalog] = await Promise.all([
        scpApi.platformUserTemporaryAccess(userId),
        canManage ? scpApi.platformCapabilities() : Promise.resolve([]),
      ]);
      setGrants(rows);
      setCapabilities(catalog.filter((cap) => cap.status === "ACTIVE"));
    } catch (cause) { setError(scpErrorMessage(cause)); }
  }, [canRead, canManage, userId]);

  useEffect(() => { void load(); }, [load]);

  async function grant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const expiry = new Date(effectiveTo);
    if (!capabilityId || !reason.trim() || !Number.isFinite(expiry.getTime()) || expiry.getTime() <= Date.now()) {
      setError(t("scp.temporary.invalid"));
      return;
    }
    setBusy(true);
    setError("");
    try {
      await scpApi.grantPlatformUserTemporaryAccess(userId, { capabilityId, effectiveTo: expiry.toISOString(), reason: reason.trim() });
      setReason("");
      setEffectiveTo("");
      await load();
    } catch (cause) { setError(scpErrorMessage(cause)); } finally { setBusy(false); }
  }

  async function revoke(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!revokeId || !revokeReason.trim()) return;
    setBusy(true);
    setError("");
    try {
      await scpApi.revokePlatformUserTemporaryAccess(userId, revokeId, revokeReason.trim());
      setRevokeId(null);
      setRevokeReason("");
      await load();
    } catch (cause) { setError(scpErrorMessage(cause)); } finally { setBusy(false); }
  }

  return (
    <section className={styles.panel}>
      <h2>{t("scp.temporary.title")}</h2>
      {!canRead ? <ScpError message={t("scp.temporary.forbidden")} /> : (
        <>
          {error ? <ScpError message={error} onRetry={load} /> : null}
          {grants === null && !error ? <ScpSkeleton lines={3} /> : null}
          {grants?.length === 0 ? <ScpEmpty message={t("scp.temporary.empty")} /> : null}
          {grants?.map((item) => (
            <article key={item.id} className={styles.appCard}>
              <h3>{item.capabilityCode}</h3>
              <ScpStatusPill value={item.status} />
              <p>{t("scp.temporary.expiry")}: <time dateTime={item.effectiveTo}>{item.effectiveTo}</time></p>
              <p>{t("scp.temporary.reason")}: {item.reason}</p>
              <p>{t("scp.temporary.actor")}: {item.grantedBy}</p>
              {canManage && item.status === "ACTIVE" ? <Button size="sm" variant="danger" disabled={busy} onClick={() => { setRevokeId(item.id); setRevokeReason(""); }}>{t("scp.temporary.revoke")}</Button> : null}
            </article>
          ))}
          {canManage ? (
            <form onSubmit={(event) => void grant(event)} className={styles.filters}>
              <label>{t("scp.temporary.capability")}
                <select required value={capabilityId} onChange={(event) => setCapabilityId(event.target.value)}>
                  <option value="">{t("scp.temporary.choose")}</option>
                  {capabilities.map((cap) => <option key={cap.id} value={cap.id}>{cap.code}</option>)}
                </select>
              </label>
              <label>{t("scp.temporary.expiry")}
                <input required type="datetime-local" value={effectiveTo} onChange={(event) => setEffectiveTo(event.target.value)} />
              </label>
              <label>{t("scp.temporary.reason")}
                <input required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
              </label>
              <Button type="submit" size="sm" variant="primary" disabled={busy}>{t("scp.temporary.grant")}</Button>
            </form>
          ) : null}
          {canManage && revokeId ? (
            <form onSubmit={(event) => void revoke(event)} className={styles.filters}>
              <label>{t("scp.temporary.revokeReason")}
                <input autoFocus required maxLength={500} value={revokeReason} onChange={(event) => setRevokeReason(event.target.value)} />
              </label>
              <Button type="submit" size="sm" variant="danger" disabled={busy}>{t("scp.temporary.confirmRevoke")}</Button>
              <Button type="button" size="sm" variant="secondary" disabled={busy} onClick={() => setRevokeId(null)}>{t("scp.temporary.cancel")}</Button>
            </form>
          ) : null}
        </>
      )}
    </section>
  );
}
