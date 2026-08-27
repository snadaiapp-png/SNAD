"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { erpApi, type ItemResponse, type PurchaseRequisitionResponse, type RequisitionPriority } from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

type RequisitionLine = { itemId: string; quantity: number; requiredDate: string; estimatedUnitCost: number; notes: string };

export default function ErpRequisitionsPage() {
  return <ErpWorkspace title="طلبات الشراء" description="إنشاء احتياج الشراء وإرساله للاعتماد قبل تحويله إلى أمر شراء."><RequisitionsContent /></ErpWorkspace>;
}

function RequisitionsContent() {
  const [rows, setRows] = useState<PurchaseRequisitionResponse[]>([]);
  const [items, setItems] = useState<ItemResponse[]>([]);
  const [lines, setLines] = useState<RequisitionLine[]>([emptyLine()]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try { const [requisitions, itemRows] = await Promise.all([erpApi.listRequisitions(), erpApi.listItems()]); setRows(requisitions); setItems(itemRows); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);
  const activeItems = useMemo(() => items.filter((item) => item.status === "ACTIVE"), [items]);

  async function mutate(action: () => Promise<unknown>, message: string) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(message); await reload(); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setBusy(false); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    const validLines = lines.filter((line) => line.itemId && line.quantity > 0);
    if (validLines.length === 0) { setError("أضف سطر شراء واحدًا على الأقل."); return; }
    await mutate(() => erpApi.createRequisition({
      reason: text(form, "reason"), priority: (String(form.get("priority") || "NORMAL") as RequisitionPriority), requesterId: null,
      items: validLines.map((line) => ({ itemId: line.itemId, quantity: line.quantity, requiredDate: line.requiredDate || null, estimatedUnitCost: line.estimatedUnitCost > 0 ? line.estimatedUnitCost : null, notes: line.notes || null })),
    }), "تم إنشاء طلب الشراء.");
    setLines([emptyLine()]); event.currentTarget.reset();
  }

  if (loading) return <ErpLoading />;
  return <>
    <ErpFeedback error={error} notice={notice} />
    <div className={styles.workspace}>
      <form className={styles.formCard} onSubmit={submit}>
        <h2 className={styles.sectionHeading}>طلب شراء جديد</h2>
        <label>سبب الطلب<textarea name="reason" disabled={busy} placeholder="وصف الحاجة التجارية للشراء" /></label>
        <label>الأولوية<select name="priority" defaultValue="NORMAL" disabled={busy}><option value="LOW">منخفضة</option><option value="NORMAL">عادية</option><option value="HIGH">عالية</option><option value="URGENT">عاجلة</option></select></label>
        <div className={styles.lineEditor}>
          <strong>بنود الطلب</strong>
          {lines.map((line, index) => <div className={styles.lineRowWide} key={index}>
            <label>الصنف<select value={line.itemId} disabled={busy} onChange={(e) => changeLine(index, { itemId: e.target.value })}><option value="">اختر</option>{activeItems.map((item) => <option value={item.id} key={item.id}>{item.code} — {item.name}</option>)}</select></label>
            <label>الكمية<input type="number" min="0.0001" step="0.0001" value={line.quantity} disabled={busy} onChange={(e) => changeLine(index, { quantity: Number(e.target.value) })} /></label>
            <label>التاريخ المطلوب<input type="date" value={line.requiredDate} disabled={busy} onChange={(e) => changeLine(index, { requiredDate: e.target.value })} /></label>
            <label>تكلفة تقديرية<input type="number" min="0" step="0.01" value={line.estimatedUnitCost} disabled={busy} onChange={(e) => changeLine(index, { estimatedUnitCost: Number(e.target.value) })} /></label>
            <button type="button" className={styles.dangerButton} disabled={busy || lines.length === 1} onClick={() => setLines((current) => current.filter((_, i) => i !== index))}>حذف</button>
            <label>ملاحظات<input value={line.notes} disabled={busy} onChange={(e) => changeLine(index, { notes: e.target.value })} /></label>
          </div>)}
          <button type="button" className={styles.secondaryButton} disabled={busy} onClick={() => setLines((current) => [...current, emptyLine()])}>إضافة بند</button>
        </div>
        <button className={styles.primaryButton} type="submit" disabled={busy}>إنشاء طلب الشراء</button>
      </form>

      <section className={styles.listCard}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>طلبات الشراء</h2><button className={styles.secondaryButton} type="button" onClick={() => void reload()} disabled={busy}>تحديث</button></div>
        {rows.length === 0 ? <ErpEmpty>لا توجد طلبات شراء.</ErpEmpty> : <div className={styles.tableWrap}><table>
          <thead><tr><th>الرقم</th><th>الأولوية</th><th>السبب</th><th>البنود</th><th>الحالة</th><th>إجراءات</th></tr></thead>
          <tbody>{rows.map((row) => <tr key={row.id}><td>{row.requisitionNumber}</td><td>{row.priority}</td><td>{row.reason || "—"}</td><td><ul className={styles.documentLines}>{row.items.map((line) => <li key={line.id}>{line.itemCode || ""} {line.itemName || itemName(items, line.itemId)} — {line.quantity}</li>)}</ul></td><td><Status status={row.status} /></td><td><div className={styles.rowActions}>
            {row.status === "DRAFT" ? <button className={styles.button} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.submitRequisition(row.id), "تم إرسال الطلب للاعتماد.")}>إرسال</button> : null}
            {row.status === "SUBMITTED" ? <><button className={styles.primaryButton} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.approveRequisition(row.id), "تم اعتماد طلب الشراء.")}>اعتماد</button><button className={styles.dangerButton} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.rejectRequisition(row.id), "تم رفض طلب الشراء.")}>رفض</button></> : null}
          </div></td></tr>)}</tbody>
        </table></div>}
      </section>
    </div>
  </>;

  function changeLine(index: number, patch: Partial<RequisitionLine>) { setLines((current) => current.map((line, i) => i === index ? { ...line, ...patch } : line)); }
}

function emptyLine(): RequisitionLine { return { itemId: "", quantity: 1, requiredDate: "", estimatedUnitCost: 0, notes: "" }; }
function text(form: FormData, key: string) { const value = String(form.get(key) ?? "").trim(); return value || null; }
function itemName(items: ItemResponse[], id: string) { return items.find((item) => item.id === id)?.name ?? id; }
function Status({ status }: { status: string }) { const cls = status === "APPROVED" ? styles.badgeSuccess : ["DRAFT", "SUBMITTED"].includes(status) ? styles.badgeWarning : styles.badgeInfo; return <span className={`${styles.badge} ${cls}`}>{status}</span>; }
