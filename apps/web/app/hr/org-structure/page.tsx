"use client";

/**
 * Org Structure workspace — WS5 Task 10 (/hr/org-structure).
 *
 * Effective-dated Org Chart: an `asOf` date input selects the snapshot date;
 * the hierarchy is rendered as an accessible nested list derived from the
 * canonical read model (parentOrgUnitId links among units effective at the
 * snapshot). No chart dependency; no invented structure.
 *
 * Org Unit revision is the governed command (HRM.ORG_STRUCTURE.MANAGE):
 * effective-dated revision with Idempotency-Key; the backend remains
 * authoritative for versioning and validation.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hrmV2Api, newIdempotencyKey, type OrgUnitResponse } from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrWorkspace } from "../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrCommandDialog } from "../components/hr-command-dialog";
import { formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Is the unit visible at the snapshot date (effective window covers asOf)? */
function effectiveAt(unit: OrgUnitResponse, asOf: string): boolean {
  return unit.effectiveFrom <= asOf && (unit.effectiveTo === null || unit.effectiveTo >= asOf);
}

export default function OrgStructurePage() {
  const { state, me } = useAuth();
  const capabilities = me?.capabilities ?? [];
  const canManage = capabilities.includes(HRM_CAPABILITIES.ORG_STRUCTURE_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [units, setUnits] = useState<OrgUnitResponse[]>([]);
  const [asOf, setAsOf] = useState(todayIso());

  // Revision dialog state.
  const [revising, setRevising] = useState<OrgUnitResponse | null>(null);
  const [revName, setRevName] = useState("");
  const [revCode, setRevCode] = useState("");
  const [revType, setRevType] = useState("");
  const [revDate, setRevDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await hrmV2Api.listOrgUnits();
      setUnits(list);
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

  /** As-of hierarchy: nodes = units effective at asOf, linked by parent. */
  const visibleUnits = useMemo(() => units.filter((u) => effectiveAt(u, asOf)), [units, asOf]);

  const childrenByParent = useMemo(() => {
    const map = new Map<string | null, OrgUnitResponse[]>();
    const known = new Set(visibleUnits.map((u) => u.orgUnitId));
    for (const u of visibleUnits) {
      // A unit whose parent is not itself visible at asOf is rendered as a root.
      const parent = u.parentOrgUnitId !== null && known.has(u.parentOrgUnitId) ? u.parentOrgUnitId : null;
      const arr = map.get(parent) ?? [];
      arr.push(u);
      map.set(parent, arr);
    }
    for (const arr of map.values()) arr.sort((a, b) => a.code.localeCompare(b.code));
    return map;
  }, [visibleUnits]);

  const roots = childrenByParent.get(null) ?? [];

  function openRevise(u: OrgUnitResponse) {
    setRevising(u);
    setRevName(u.name);
    setRevCode(u.code);
    setRevType(u.unitType);
    setRevDate(todayIso());
    setDialogError(null);
  }

  async function submitRevise() {
    if (!revising) return;
    setBusy(true);
    setDialogError(null);
    try {
      await hrmV2Api.reviseOrgUnit(
        revising.orgUnitId,
        {
          name: revName.trim() || undefined,
          code: revCode.trim() || undefined,
          unitType: revType.trim() || undefined,
          effectiveDate: revDate,
        },
        newIdempotencyKey(),
      );
      setRevising(null);
      setNotice("تم تسجيل تعديل الوحدة بتاريخ السريان المحدد");
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  function renderNode(u: OrgUnitResponse, depth: number): ReactNode {
    const children = childrenByParent.get(u.orgUnitId) ?? [];
    return (
      <li key={u.orgUnitId}>
        <div className={styles.orgNode}>
          <span className={styles.orgName}>{u.name}</span>
          <span className={styles.orgMeta}>
            {u.code} · {u.unitType} · {formatArabicDate(u.effectiveFrom)}
          </span>
          {canManage ? (
            <button type="button" className={styles.linkButton} onClick={() => openRevise(u)}>
              تعديل
            </button>
          ) : null}
        </div>
        {children.length > 0 ? (
          <ul className={styles.orgChildren}>
            {children.map((c) => renderNode(c, depth + 1))}
          </ul>
        ) : null}
      </li>
    );
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/org-structure">
      {notice ? <p role="status" className={styles.notice}>{notice}</p> : null}
      <div className={styles.toolbar}>
        <label className={styles.inlineField}>
          تاريخ العرض (سريان)
          <input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} />
        </label>
      </div>

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : visibleUnits.length === 0 ? (
        <HrEmptyState
          title="لا توجد وحدات تنظيمية سارية"
          description={`لا توجد وحدات سارية في تاريخ ${formatArabicDate(asOf)}.`}
        />
      ) : (
        <ul className={styles.orgTree} aria-label="الهيكل التنظيمي">
          {roots.map((r) => renderNode(r, 0))}
        </ul>
      )}

      {revising ? (
        <HrCommandDialog
          title={`تعديل الوحدة: ${revising.name}`}
          description="التعديل بتاريخ سريان؛ لا يحذف التاريخ السابق."
          busy={busy}
          error={dialogError}
          onConfirm={submitRevise}
          onCancel={() => setRevising(null)}
          fields={[
            { label: "الاسم", type: "text", value: revName, onChange: setRevName },
            { label: "الرمز", type: "text", value: revCode, onChange: setRevCode },
            { label: "نوع الوحدة", type: "text", value: revType, onChange: setRevType },
            { label: "تاريخ السريان", type: "date", value: revDate, onChange: setRevDate, required: true },
          ]}
        />
      ) : null}
    </HrWorkspace>
  );
}
