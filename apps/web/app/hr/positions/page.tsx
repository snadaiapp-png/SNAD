"use client";

/**
 * Positions workspace — WS5 Task 10 (/hr/positions).
 *
 * VACANT/OCCUPIED display is DERIVED from effective occupying assignments
 * (an ACTIVE assignment whose positionId matches and whose effective window
 * covers today). There is no manual occupancy toggle — the backend owns the
 * occupancy model; the UI only projects it.
 *
 * Governed commands: freeze/close with HRM.ORG_STRUCTURE.MANAGE,
 * Idempotency-Key and confirmation dialog.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hrmV2Api, newIdempotencyKey, type AssignmentResponse, type PositionResponse } from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrWorkspace } from "../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrCommandDialog } from "../components/hr-command-dialog";
import { formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

const POSITION_STATUS_AR: Record<string, string> = {
  ACTIVE: "ساري",
  FROZEN: "مجمّد",
  CLOSED: "مغلق",
  SUPERSEDED: "مُستبدل",
};

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function PositionsPage() {
  const { state, me } = useAuth();
  const capabilities = me?.capabilities ?? [];
  const canManage = capabilities.includes(HRM_CAPABILITIES.ORG_STRUCTURE_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);

  // Command dialog state: "freeze" | "close" | null.
  const [command, setCommand] = useState<"freeze" | "close" | null>(null);
  const [target, setTarget] = useState<PositionResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [pos, asg] = await Promise.all([hrmV2Api.listPositions(), hrmV2Api.listAssignments()]);
      setPositions(pos);
      setAssignments(asg);
      setError(null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  /**
   * Occupancy derivation (documented projection of the canonical model):
   * a position is OCCUPIED when an assignment references it, is ACTIVE and
   * its effective window covers today. Nothing here mutates occupancy.
   */
  const today = todayIso();
  const occupiedPositionIds = useMemo(() => {
    const set = new Set<string>();
    for (const a of assignments) {
      if (!a.positionId) continue;
      const effective = a.effectiveFrom <= today && (a.effectiveTo === null || a.effectiveTo >= today);
      if (a.status === "ACTIVE" && effective) set.add(a.positionId);
    }
    return set;
  }, [assignments, today]);

  function openCommand(kind: "freeze" | "close", p: PositionResponse) {
    setCommand(kind);
    setTarget(p);
    setDialogError(null);
  }

  async function submitCommand() {
    if (!target || !command) return;
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      if (command === "freeze") await hrmV2Api.freezePosition(target.positionId, key);
      else await hrmV2Api.closePosition(target.positionId, key);
      setCommand(null);
      setTarget(null);
      setNotice(command === "freeze" ? "تم تجميد المنصب" : "تم إغلاق المنصب");
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/positions">
      {notice ? <p role="status" className={styles.notice}>{notice}</p> : null}
      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : positions.length === 0 ? (
        <HrEmptyState title="لا توجد مناصب" description="لم تُسجَّل مناصب بعد." />
      ) : (
        <div className={styles.hrTableWrap}>
          <table className={styles.hrTable}>
            <caption>المناصب — الإشغال مشتق من الإسنادات السارية</caption>
            <thead>
              <tr>
                <th scope="col">المسمى</th>
                <th scope="col">الرمز</th>
                <th scope="col">سريان من</th>
                <th scope="col">الحالة</th>
                <th scope="col">الإشغال</th>
                {canManage ? <th scope="col">إجراءات</th> : null}
              </tr>
            </thead>
            <tbody>
              {positions.map((p) => {
                const occupied = occupiedPositionIds.has(p.positionId);
                return (
                  <tr key={p.positionId}>
                    <td>{p.title}</td>
                    <td>{p.staffability ?? "—"}</td>
                    <td>{formatArabicDate(p.effectiveFrom)}</td>
                    <td>{POSITION_STATUS_AR[p.status] ?? p.status}</td>
                    <td>
                      <span className={occupied ? styles.occupiedBadge : styles.vacantBadge}>
                        {occupied ? "مشغول" : "شاغر"}
                      </span>
                    </td>
                    {canManage ? (
                      <td>
                        {p.status === "ACTIVE" ? (
                          <span className={styles.actionRow}>
                            <button type="button" className={styles.linkButton} onClick={() => openCommand("freeze", p)}>
                              تجميد
                            </button>
                            <button type="button" className={styles.linkButton} onClick={() => openCommand("close", p)}>
                              إغلاق
                            </button>
                          </span>
                        ) : "—"}
                      </td>
                    ) : null}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {command && target ? (
        <HrCommandDialog
          title={command === "freeze" ? `تجميد المنصب: ${target.title}` : `إغلاق المنصب: ${target.title}`}
          description="المنصب لن يقبل إسنادات جديدة. لا يتم حذف أي بيانات."
          busy={busy}
          error={dialogError}
          onConfirm={submitCommand}
          onCancel={() => { setCommand(null); setTarget(null); }}
        />
      ) : null}
    </HrWorkspace>
  );
}
