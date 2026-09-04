"use client";

/**
 * Assignments workspace — WS5 Task 10 (/hr/assignments).
 *
 * Lists canonical assignments with Arabic labels. Governed commands:
 * transfer and change-manager (HRM.ASSIGNMENT.MANAGE) — both send
 * effectiveDate + expectedVersion + a generated Idempotency-Key; the
 * backend remains authoritative for period superseding semantics.
 */

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hrmV2Api, newIdempotencyKey, type AssignmentResponse } from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrWorkspace } from "../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrCommandDialog } from "../components/hr-command-dialog";
import { ASSIGNMENT_STATUS_AR, formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function AssignmentsPage() {
  const { state, me } = useAuth();
  const capabilities = me?.capabilities ?? [];
  const canManage = capabilities.includes(HRM_CAPABILITIES.ASSIGNMENT_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [notice, setNotice] = useState<string | null>(null);

  // Command dialog state.
  const [transferring, setTransferring] = useState<AssignmentResponse | null>(null);
  const [transferOrgUnit, setTransferOrgUnit] = useState("");
  const [transferDate, setTransferDate] = useState(todayIso());
  const [managerFor, setManagerFor] = useState<AssignmentResponse | null>(null);
  const [managerRef, setManagerRef] = useState("");
  const [managerDate, setManagerDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setAssignments(await hrmV2Api.listAssignments());
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

  async function submitTransfer() {
    if (!transferring) return;
    setBusy(true);
    setDialogError(null);
    try {
      await hrmV2Api.transferAssignment(
        transferring.assignmentId,
        {
          orgUnitId: transferOrgUnit.trim(),
          effectiveDate: transferDate,
          expectedVersion: transferring.version,
        },
        newIdempotencyKey(),
      );
      setTransferring(null);
      setTransferOrgUnit("");
      setNotice("تم تسجيل نقل الإسناد بتاريخ السريان المحدد");
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  async function submitChangeManager() {
    if (!managerFor) return;
    setBusy(true);
    setDialogError(null);
    try {
      await hrmV2Api.changeAssignmentManager(
        managerFor.assignmentId,
        {
          reportsToAssignmentId: managerRef.trim(),
          effectiveDate: managerDate,
          expectedVersion: managerFor.version,
        },
        newIdempotencyKey(),
      );
      setManagerFor(null);
      setManagerRef("");
      setNotice("تم تسجيل تغيير المدير بتاريخ السريان المحدد");
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
    <HrWorkspace capabilities={capabilities} activeHref="/hr/assignments">
      {notice ? <p role="status" className={styles.notice}>{notice}</p> : null}
      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : assignments.length === 0 ? (
        <HrEmptyState title="لا توجد إسنادات" description="لم تُسجَّل إسنادات بعد." />
      ) : (
        <div className={styles.hrTableWrap}>
          <table className={styles.hrTable}>
            <caption>الإسنادات</caption>
            <thead>
              <tr>
                <th scope="col">النوع</th>
                <th scope="col">الوحدة التنظيمية</th>
                <th scope="col">المنصب</th>
                <th scope="col">من</th>
                <th scope="col">إلى</th>
                <th scope="col">الحالة</th>
                {canManage ? <th scope="col">إجراءات</th> : null}
              </tr>
            </thead>
            <tbody>
              {assignments.map((a) => (
                <tr key={a.assignmentId}>
                  <td>{a.assignmentType}</td>
                  <td>{a.orgUnitId ?? "—"}</td>
                  <td>{a.positionId ?? "—"}</td>
                  <td>{formatArabicDate(a.effectiveFrom)}</td>
                  <td>{a.effectiveTo ? formatArabicDate(a.effectiveTo) : "—"}</td>
                  <td>{ASSIGNMENT_STATUS_AR[a.status] ?? a.status}</td>
                  {canManage ? (
                    <td>
                      {a.status === "ACTIVE" ? (
                        <span className={styles.actionRow}>
                          <button type="button" className={styles.linkButton}
                                  onClick={() => { setTransferring(a); setTransferDate(todayIso()); setDialogError(null); }}>
                            نقل
                          </button>
                          <button type="button" className={styles.linkButton}
                                  onClick={() => { setManagerFor(a); setManagerDate(todayIso()); setDialogError(null); }}>
                            تغيير المدير
                          </button>
                        </span>
                      ) : "—"}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {transferring ? (
        <HrCommandDialog
          title="نقل الإسناد"
          description="يُرسل التاريخ الفعلي وإصدار السجل الحالي مع مفتاح تفرد."
          busy={busy}
          error={dialogError}
          onConfirm={submitTransfer}
          onCancel={() => setTransferring(null)}
          fields={[
            { label: "الوحدة التنظيمية الجديدة (المعرّف)", type: "text", value: transferOrgUnit, onChange: setTransferOrgUnit, required: true },
            { label: "تاريخ السريان", type: "date", value: transferDate, onChange: setTransferDate, required: true },
          ]}
        />
      ) : null}

      {managerFor ? (
        <HrCommandDialog
          title="تغيير المدير المباشر"
          description="أدخل معرّف إسناد المدير الجديد. يُرسل التاريخ الفعلي وإصدار السجل مع مفتاح تفرد."
          busy={busy}
          error={dialogError}
          onConfirm={submitChangeManager}
          onCancel={() => setManagerFor(null)}
          fields={[
            { label: "إسناد المدير الجديد (المعرّف)", type: "text", value: managerRef, onChange: setManagerRef, required: true },
            { label: "تاريخ السريان", type: "date", value: managerDate, onChange: setManagerDate, required: true },
          ]}
        />
      ) : null}
    </HrWorkspace>
  );
}
