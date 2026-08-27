"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  erpApi,
  type ItemResponse,
  type PurchaseOrderResponse,
  type PurchaseRequisitionResponse,
  type SupplierResponse,
} from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

type PoLine = { itemId: string; quantity: number; unitCost: number };

export default function ErpPurchaseOrdersPage() {
  return <ErpWorkspace title="أوامر الشراء" description="إصدار أوامر الشراء واعتمادها ومتابعة الكميات المستلمة والتكلفة."><PurchaseOrdersContent /></ErpWorkspace>;
}

function PurchaseOrdersContent() {
  const [orders, setOrders] = useState<PurchaseOrderResponse[]>([]);
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
  const [items, setItems] = useState<ItemResponse[]>([]);
  const [requisitions, setRequisitions] = useState<PurchaseRequisitionResponse[]>([]);
  const [lines, setLines] = useState<PoLine[]>([emptyLine()]);
  const [requisitionId, setRequisitionId] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [poRows, supplierRows, itemRows, requisitionRows] = await Promise.all([
        erpApi.listPurchaseOrders(), erpApi.listSuppliers(), erpApi.listItems(), erpApi.listRequisitions(),
      ]);
      setOrders(poRows); setSuppliers(supplierRows); setItems(itemRows); setRequisitions(requisitionRows);
    } catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  const activeSuppliers = useMemo(() => suppliers.filter((supplier) => supplier.status === "ACTIVE"), [suppliers]);
  const activeItems = useMemo(() => items.filter((item) => item.status === "ACTIVE"), [items]);
  const approvedRequisitions = useMemo(() => requisitions.filter((row) => row.status === "APPROVED"), [requisitions]);
  const draftTotal = lines.reduce((sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitCost) || 0), 0);

  async function mutate(action: () => Promise<unknown>, message: string): Promise<boolean> {
    setBusy(true); setError(""); setNotice("");
    try {
      await action();
      setNotice(message);
      await reload();
      return true;
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      return false;
    } finally { setBusy(false); }
  }

  function chooseRequisition(id: string) {
    setRequisitionId(id);
    const requisition = requisitions.find((row) => row.id === id);
    if (requisition) {
      setLines(requisition.items.map((line) => ({ itemId: line.itemId, quantity: Number(line.quantity), unitCost: Number(line.estimatedUnitCost || 0) })));
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const validLines = lines.filter((line) => line.itemId && line.quantity > 0 && line.unitCost >= 0);
    if (validLines.length === 0) { setError("أضف بند شراء واحدًا على الأقل."); return; }
    const succeeded = await mutate(() => erpApi.createPurchaseOrder({
      supplierId: String(form.get("supplierId") ?? ""), currency: String(form.get("currency") || "SAR").toUpperCase(),
      expectedDate: String(form.get("expectedDate") || "") || null, requisitionId: requisitionId || null,
      items: validLines,
    }), "تم إنشاء أمر الشراء.");
    if (succeeded) {
      setLines([emptyLine()]);
      setRequisitionId("");
      formElement.reset();
    }
  }

  if (loading) return <ErpLoading />;
  return <>
    <ErpFeedback error={error} notice={notice} />
    <div className={styles.workspace}>
      <form className={styles.formCard} onSubmit={submit}>
        <h2 className={styles.sectionHeading}>أمر شراء جديد</h2>
        <label>المورد<select name="supplierId" required disabled={busy}><option value="">اختر المورد</option>{activeSuppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.supplierCode} — {supplier.name}</option>)}</select></label>
        <label>طلب شراء معتمد (اختياري)<select value={requisitionId} disabled={busy} onChange={(e) => chooseRequisition(e.target.value)}><option value="">بدون ربط</option>{approvedRequisitions.map((row) => <option key={row.id} value={row.id}>{row.requisitionNumber} — {row.reason || "طلب معتمد"}</option>)}</select></label>
        <div className={styles.formGrid}><label>العملة<input name="currency" defaultValue="SAR" maxLength={3} required disabled={busy} /></label><label>تاريخ التسليم المتوقع<input name="expectedDate" type="date" disabled={busy} /></label></div>
        <div className={styles.lineEditor}>
          <strong>بنود أمر الشراء</strong>
          {lines.map((line, index) => <div className={styles.lineRow} key={index}>
            <label>الصنف<select value={line.itemId} disabled={busy} onChange={(e) => changeLine(index, { itemId: e.target.value })}><option value="">اختر</option>{activeItems.map((item) => <option value={item.id} key={item.id}>{item.code} — {item.name}</option>)}</select></label>
            <label>الكمية<input type="number" min="0.0001" step="0.0001" value={line.quantity} disabled={busy} onChange={(e) => changeLine(index, { quantity: Number(e.target.value) })} /></label>
            <label>تكلفة الوحدة<input type="number" min="0" step="0.01" value={line.unitCost} disabled={busy} onChange={(e) => changeLine(index, { unitCost: Number(e.target.value) })} /></label>
            <button type="button" className={styles.dangerButton} disabled={busy || lines.length === 1} onClick={() => setLines((current) => current.filter((_, i) => i !== index))}>حذف</button>
          </div>)}
          <button type="button" className={styles.secondaryButton} disabled={busy} onClick={() => setLines((current) => [...current, emptyLine()])}>إضافة بند</button>
          <div className={styles.muted}>الإجمالي التقديري للنموذج: {draftTotal.toLocaleString("ar-SA")} ر.س — الإجمالي النهائي يعتمد من الخادم.</div>
        </div>
        <button className={styles.primaryButton} type="submit" disabled={busy}>إنشاء أمر الشراء</button>
      </form>

      <section className={styles.listCard}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>أوامر الشراء</h2><button className={styles.secondaryButton} type="button" onClick={() => void reload()} disabled={busy}>تحديث</button></div>
        {orders.length === 0 ? <ErpEmpty>لا توجد أوامر شراء. فعّل موردًا وصنفًا ثم أنشئ الأمر الأول.</ErpEmpty> : <div className={styles.tableWrap}><table>
          <thead><tr><th>الرقم</th><th>المورد</th><th>البنود</th><th>الإجمالي</th><th>المتوقع</th><th>الحالة</th><th>إجراءات</th></tr></thead>
          <tbody>{orders.map((order) => <tr key={order.id}>
            <td>{order.poNumber}</td><td>{order.supplierName || supplierName(suppliers, order.supplierId)}</td>
            <td><ul className={styles.documentLines}>{order.items.map((line) => <li key={line.id}>{line.itemCode || ""} {line.itemName || itemName(items, line.itemId)} — {line.quantity} × {line.unitCost}، مستلم {line.receivedQuantity}</li>)}</ul></td>
            <td>{Number(order.total).toLocaleString("ar-SA")} {order.currency}</td><td>{order.expectedDate || "—"}</td><td><Status status={order.status} /></td>
            <td><div className={styles.rowActions}>
              {order.status === "DRAFT" ? <button className={styles.button} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.submitPurchaseOrder(order.id), "تم إرسال أمر الشراء للاعتماد.")}>إرسال</button> : null}
              {order.status === "SUBMITTED" ? <button className={styles.primaryButton} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.approvePurchaseOrder(order.id), "تم اعتماد أمر الشراء.")}>اعتماد</button> : null}
              {["DRAFT", "SUBMITTED", "APPROVED", "SENT"].includes(order.status) ? <button className={styles.dangerButton} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.cancelPurchaseOrder(order.id), "تم إلغاء أمر الشراء.")}>إلغاء</button> : null}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
      </section>
    </div>
  </>;

  function changeLine(index: number, patch: Partial<PoLine>) { setLines((current) => current.map((line, i) => i === index ? { ...line, ...patch } : line)); }
}

function emptyLine(): PoLine { return { itemId: "", quantity: 1, unitCost: 0 }; }
function itemName(items: ItemResponse[], id: string) { return items.find((item) => item.id === id)?.name ?? id; }
function supplierName(suppliers: SupplierResponse[], id: string) { return suppliers.find((supplier) => supplier.id === id)?.name ?? id; }
function Status({ status }: { status: string }) { const cls = ["APPROVED", "RECEIVED", "CLOSED"].includes(status) ? styles.badgeSuccess : ["DRAFT", "SUBMITTED", "SENT", "PARTIALLY_RECEIVED"].includes(status) ? styles.badgeWarning : styles.badgeInfo; return <span className={`${styles.badge} ${cls}`}>{status}</span>; }
