"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { hrPayrollApi, type PayrollRun, type PayrollItem, type PayrollMutation } from "@/lib/api/hr-payroll-api";
import { hrmErrorMessage } from "../components/hr-feedback";
import { HrWorkspace } from "../components/hr-workspace";
import styles from "./payroll.module.css";

/**
 * G4-T9 review surface. The authenticated API client adds Idempotency-Key to
 * each mutation; the backend remains authoritative for HRM.PAYROLL.VIEW,
 * CALCULATE, REVIEW, APPROVE and EXPORT capabilities and tenant isolation.
 */
const CAP = {
  view: "HRM.PAYROLL.VIEW",
  calculate: "HRM.PAYROLL.CALCULATE",
  review: "HRM.PAYROLL.REVIEW",
  approve: "HRM.PAYROLL.APPROVE",
  export: "HRM.PAYROLL.EXPORT",
} as const;
const ACTIONS: Record<PayrollMutation, { from: string[]; cap: string }> = {
  calculate: { from: ["DRAFT"], cap: CAP.calculate },
  recalculate: { from: ["CALCULATED"], cap: CAP.calculate },
  review: { from: ["CALCULATED"], cap: CAP.review },
  approve: { from: ["REVIEWED"], cap: CAP.approve },
  export: { from: ["APPROVED"], cap: CAP.export },
};
const COPY = {
  ar: {
    title: "مراجعة مسيرات الرواتب", subtitle: "مراجعة داخلية وطلب تصدير محاسبي دون تنفيذ مدفوعات",
    runs: "المسيرات", details: "تفاصيل المسير", items: "بنود المسير", exceptions: "الاستثناءات",
    empty: "لا توجد مسيرات رواتب", noItems: "لا توجد بنود لهذا المسير", loading: "جارٍ التحميل",
    error: "تعذر تحميل البيانات", forbidden: "ليس لديك صلاحية عرض مسيرات الرواتب",
    conflict: "تغيرت حالة المسير. حدّث البيانات قبل متابعة الإجراء.",
    refresh: "تحديث", period: "الفترة", status: "الحالة", version: "الإصدار",
    gross: "إجمالي المستحقات", deductions: "الاستقطاعات", net: "الصافي",
    base: "الراتب الأساسي", employee: "معرّف التوظيف", exception: "رمز الاستثناء",
    noExceptions: "لا توجد استثناءات ظاهرة", reason: "سبب الإجراء",
    calculate: "حساب", recalculate: "إعادة حساب", review: "مراجعة", approve: "اعتماد",
    export: "طلب تصدير للمحاسبة", confirm: "تأكيد الإجراء", cancel: "إلغاء",
    success: "تم تنفيذ الإجراء", failed: "تعذر تنفيذ الإجراء",
    create: "إنشاء مسير", entity: "معرّف الكيان القانوني", start: "بداية الفترة",
    end: "نهاية الفترة", currency: "العملة", cutoff: "موعد قطع البيانات",
    createDone: "تم إنشاء المسير", detailsPrompt: "اختر مسيرًا لعرض التفاصيل",
    pending: "جارٍ التنفيذ", readOnly: "عرض فقط: لا تملك صلاحية إجراء التغيير",
  },
  en: {
    title: "Payroll review", subtitle: "Internal review and accounting export request; no payment execution",
    runs: "Payroll runs", details: "Run details", items: "Payroll items", exceptions: "Exceptions",
    empty: "No payroll runs found", noItems: "No items in this run", loading: "Loading",
    error: "Unable to load data", forbidden: "You do not have permission to view payroll",
    conflict: "The run has changed. Refresh before taking another action.",
    refresh: "Refresh", period: "Period", status: "Status", version: "Version",
    gross: "Gross earnings", deductions: "Deductions", net: "Net pay",
    base: "Base amount", employee: "Employment ID", exception: "Exception code",
    noExceptions: "No visible exceptions", reason: "Reason for action",
    calculate: "Calculate", recalculate: "Recalculate", review: "Review", approve: "Approve",
    export: "Request accounting export", confirm: "Confirm action", cancel: "Cancel",
    success: "Action completed", failed: "Action failed",
    create: "Create payroll run", entity: "Legal entity ID", start: "Period start",
    end: "Period end", currency: "Currency", cutoff: "Source cutoff",
    createDone: "Run created", detailsPrompt: "Select a run to see details",
    pending: "Processing", readOnly: "Read-only: insufficient permission to mutate",
  },
} as const;

export default function PayrollClient() {
  const { state, me } = useAuth();
  const { locale } = useI18n();
  const t = COPY[locale === "ar" ? "ar" : "en"];
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes(CAP.view);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [runs, setRuns] = useState<PayrollRun[]>([]);
  const [selected, setSelected] = useState("");
  const [items, setItems] = useState<PayrollItem[]>([]);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [itemsError, setItemsError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [mutationError, setMutationError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");
  const [pendingAction, setPendingAction] = useState<PayrollMutation | null>(null);
  const [reason, setReason] = useState("");
  const [creating, setCreating] = useState(false);
  const [createFields, setCreateFields] = useState({
    legalEntityId: "", periodStart: "", periodEnd: "", currencyCode: "SAR", sourceCutoffAt: "",
  });
  const active = useMemo(() => runs.find((run) => run.id === selected), [runs, selected]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrPayrollApi.listRuns();
      setRuns(data);
      setSelected((before) => data.some((run) => run.id === before) ? before : (data[0]?.id ?? ""));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    if (state !== "AUTHENTICATED" || !canView) return;
    void load();
  }, [state, canView, load]);

  useEffect(() => {
    if (state !== "AUTHENTICATED" || !canView || !selected) {
      setItems([]);
      return;
    }
    let live = true;
    setItemsLoading(true);
    setItemsError(null);
    void hrPayrollApi.listItems(selected).then((data) => {
      if (live) setItems(data);
    }).catch((e: unknown) => {
      if (live) setItemsError(e);
    }).finally(() => {
      if (live) setItemsLoading(false);
    });
    return () => { live = false; };
  }, [selected, state, canView, runs]);

  const money = (amount: number | null | undefined) => {
    const value = Number(amount ?? 0);
    return new Intl.NumberFormat(locale === "ar" ? "ar-SA" : "en-US", {
      style: "currency", currency: active?.currencyCode ?? "SAR", maximumFractionDigits: 2,
    }).format(Number.isFinite(value) ? value : 0);
  };
  const totals = useMemo(() => items.reduce((acc, item) => ({
    gross: acc.gross + Number(item.grossAmount ?? 0),
    deductions: acc.deductions + Number(item.deductionTotal ?? 0),
    net: acc.net + Number(item.netAmount ?? 0),
  }), { gross: 0, deductions: 0, net: 0 }), [items]);
  const exceptions = useMemo(() => items.filter((item) => Boolean(item.exceptionCode) || item.status === "EXCEPTION"), [items]);
  const feedback = mutationError ? hrmErrorMessage(mutationError) : null;
  const loadFeedback = error ? hrmErrorMessage(error) : null;

  async function runAction(action: PayrollMutation) {
    if (!active || busy || !capabilities.includes(ACTIONS[action].cap) || !ACTIONS[action].from.includes(active.status)) return;
    setBusy(true);
    setMutationError(null);
    setNotice("");
    try {
      await hrPayrollApi.mutate(active.id, action, active.version, reason.trim() || "PAYROLL_OPERATOR_REVIEW");
      setPendingAction(null);
      setReason("");
      setNotice(t.success);
      await load();
    } catch (e) {
      setMutationError(e);
      if (hrmErrorMessage(e).kind === "conflict") await load();
    } finally {
      setBusy(false);
    }
  }

  async function createRun(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !capabilities.includes(CAP.calculate)) return;
    setBusy(true);
    setMutationError(null);
    try {
      const record = await hrPayrollApi.createRun({
        ...createFields, currencyCode: createFields.currencyCode.trim().toUpperCase(),
        sourceCutoffAt: new Date(createFields.sourceCutoffAt).toISOString(),
      });
      await load();
      setSelected(record.id);
      setCreating(false);
      setNotice(t.createDone);
    } catch (e) {
      setMutationError(e);
    } finally {
      setBusy(false);
    }
  }

  const input = (key: keyof typeof createFields, label: string, type: string) => (
    <label className={styles.field} key={key}>
      <span>{label}</span>
      <input required type={type} value={createFields[key]} onChange={(e) =>
        setCreateFields((old) => ({ ...old, [key]: e.target.value }))} />
    </label>
  );

  if (state !== "AUTHENTICATED") {
    return <HrWorkspace title={t.title}><p role="status">{t.loading}</p></HrWorkspace>;
  }
  if (!canView) {
    return <HrWorkspace title={t.title}><div role="alert" className={styles.alert}>{t.forbidden} (forbidden)</div></HrWorkspace>;
  }

  return <HrWorkspace title={t.title}>
    <main className={styles.page} dir={locale === "ar" ? "rtl" : "ltr"}>
      <header className={styles.header}>
        <div><h1>{t.title}</h1><p>{t.subtitle}</p></div>
        <div className={styles.actions}>
          <button type="button" onClick={() => void load()} disabled={busy || loading}>{t.refresh}</button>
          {capabilities.includes(CAP.calculate) && <button type="button" onClick={() => setCreating((v) => !v)}>{t.create}</button>}
        </div>
      </header>
      {notice && <p className={styles.notice} role="status">{notice}</p>}
      {feedback && <p className={styles.alert} role="alert">{feedback.kind === "conflict" ? t.conflict : feedback.message}</p>}
      {loadFeedback && <div className={styles.alert} role="alert">{loadFeedback.message} <button type="button" onClick={() => void load()}>{t.refresh}</button></div>}
      {creating && <form className={styles.panel} onSubmit={(e) => void createRun(e)}>
        <h2>{t.create}</h2>
        <div className={styles.fields}>
          {input("legalEntityId", t.entity, "text")}
          {input("periodStart", t.start, "date")}
          {input("periodEnd", t.end, "date")}
          {input("currencyCode", t.currency, "text")}
          {input("sourceCutoffAt", t.cutoff, "datetime-local")}
        </div>
        <button disabled={busy} type="submit">{busy ? t.pending : t.create}</button>
      </form>}
      <div className={styles.layout}>
        <section className={styles.panel} aria-label={t.runs}>
          <h2>{t.runs}</h2>
          {loading ? <p role="status">{t.loading} (loading)</p> : !runs.length ? <p>{t.empty} (empty)</p> :
          <div className={styles.runList}>
            {runs.map((run) => <button type="button" key={run.id} className={run.id === selected ? styles.selected : styles.run}
              onClick={() => { setSelected(run.id); setMutationError(null); setPendingAction(null); }}>
              <strong>{run.periodStart} — {run.periodEnd}</strong>
              <span>{run.status} · {run.currencyCode} · v{run.version}</span>
            </button>)}
          </div>}
        </section>
        <section className={styles.panel} aria-label={t.details}>
          <h2>{t.details}</h2>
          {!active ? <p>{t.detailsPrompt}</p> : <>
            <dl className={styles.summary}>
              <div><dt>{t.period}</dt><dd>{active.periodStart} — {active.periodEnd}</dd></div>
              <div><dt>{t.status}</dt><dd>{active.status}</dd></div>
              <div><dt>{t.version}</dt><dd>{active.version}</dd></div>
            </dl>
            <div className={styles.actions}>
              {(Object.keys(ACTIONS) as PayrollMutation[]).filter((action) =>
                ACTIONS[action].from.includes(active.status) && capabilities.includes(ACTIONS[action].cap)
              ).map((action) => <button key={action} type="button" disabled={busy}
                onClick={() => { setPendingAction(action); setMutationError(null); }}>{t[action]}</button>)}
            </div>
            {pendingAction && <div className={styles.confirm}>
              <strong>{t.confirm}: {t[pendingAction]}</strong>
              {pendingAction !== "export" && <label className={styles.field}><span>{t.reason}</span>
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} /></label>}
              <div className={styles.actions}>
                <button type="button" disabled={busy} onClick={() => void runAction(pendingAction)}>{busy ? t.pending : t.confirm}</button>
                <button type="button" disabled={busy} onClick={() => setPendingAction(null)}>{t.cancel}</button>
              </div>
            </div>}
            {!Object.values(ACTIONS).some((a) => a.from.includes(active.status) && capabilities.includes(a.cap)) &&
              <p>{t.readOnly}</p>}
            <div className={styles.totals}>
              <div><small>{t.gross}</small><strong>{money(totals.gross)}</strong></div>
              <div><small>{t.deductions}</small><strong>{money(totals.deductions)}</strong></div>
              <div><small>{t.net}</small><strong>{money(totals.net)}</strong></div>
            </div>
            <h3>{t.exceptions} ({exceptions.length})</h3>
            {itemsLoading ? <p>{t.loading}</p> : itemsError ? <p role="alert">{hrmErrorMessage(itemsError).message}</p> :
              !exceptions.length ? <p>{t.noExceptions}</p> : <ul>{exceptions.map((item) =>
                <li key={item.id}>{item.employmentId}: {item.exceptionCode || item.status}</li>)}</ul>}
            <h3>{t.items}</h3>
            {itemsLoading ? <p>{t.loading}</p> : !items.length ? <p>{t.noItems}</p> :
              <div className={styles.tableScroll}><table>
                <thead><tr><th>{t.employee}</th><th>{t.base}</th><th>{t.gross}</th><th>{t.deductions}</th><th>{t.net}</th><th>{t.status}</th><th>{t.exception}</th></tr></thead>
                <tbody>{items.map((item) => <tr key={item.id}>
                  <td>{item.employmentId}</td><td>{money(item.baseAmount)}</td><td>{money(item.grossAmount)}</td>
                  <td>{money(item.deductionTotal)}</td><td>{money(item.netAmount)}</td>
                  <td>{item.status}</td><td>{item.exceptionCode || "—"}</td>
                </tr>)}</tbody>
              </table></div>}
          </>}
        </section>
      </div>
    </main>
  </HrWorkspace>;
}
