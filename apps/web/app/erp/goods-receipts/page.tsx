"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { erpApi, type GoodsReceiptResponse, type PurchaseOrderResponse, type WarehouseResponse } from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

type ReceiptLine = { poItemId: string; itemId: string; label: string; ordered: number; received: number; quantity: number };

export default function ErpGoodsReceiptsPage() {
  return <ErpWorkspace title="استلام البضاعة" description="تسجيل الكميات المستلمة من أوامر الشراء ثم ترحيلها فعليًا إلى رصيد المستودع."><GoodsReceiptsContent /></ErpWorkspace>;
}

function GoodsReceiptsContent() {
  const [receipts, setReceipts] = useState<GoodsReceiptResponse[]>([]);
  const [orders, setOrders] = useState<PurchaseOrderResponse[]>([]);
  const [warehouses, setWarehouses] = useState<WarehouseResponse[]>([]);
  const [poId, setPoId] = useState("");
  const [lines, setLines] = useState<ReceiptLine[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try { const [receiptRows, poRows, warehouseRows] = await Promise.all([erpApi.listGoodsReceipts(), erpApi.listPurchaseOrders(), erpApi.listWarehouses()]); setReceipts(receiptRows); setOrders(poRows); setWarehouses(warehouseRows); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  const receivableOrders = useMemo(() => orders.filter((order) => ["APPROVED", "SENT", "PARTIALLY_RECEIVED"].includes(order.status)), [orders]);
  const activeWarehouses = useMemo(() => warehouses.filter((warehouse) => warehouse.status === "ACTIVE"), [warehouses]);

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

  function selectOrder(id: string) {
    setPoId(id);
    const order = orders.find((candidate) => candidate.id === id);
    setLines(order ? order.items.map((line) => ({
      poItemId: line.id, itemId: line.itemId, label: `${line.itemCode || ""} ${line.itemName || line.itemId}`.trim(),
      ordered: Number(line.quantity), received: Number(line.receivedQuantity),
      quantity: Math.max(0, Number(line.quantity) - Number(line.receivedQuantity)),
    })) : []);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const receiptLines = lines.filter((line) => line.quantity > 0).map((line) => ({ poItemId: line.poItemId, itemId: line.itemId, quantity: line.quantity }));
    if (!poId) { setError("اختر أمر شراء."); return; }
    if (receiptLines.length === 0) { setError("أدخل كمية مستلمة لسطر واحد على الأقل."); return; }
    const succeeded = await mutate(() => erpApi.createGoodsReceipt({ poId, warehouseId: String(form.get("warehouseId") ?? ""), items: receiptLines }), "تم إنشاء سند الاستلام كمسودة. راجعه ثم قم بالترحيل.");
    if (succeeded) {
      setPoId("");
      setLines([]);
      formElement.reset();
    }
  }

  if (loading) return <ErpLoading />;
  return <>
    <ErpFeedback error={error} notice={notice} />
    <div className={styles.workspace}>
      <form className={styles.formCard} onSubmit={submit}>
        <h2 className={styles.sectionHeading}>سند استلام جديد</h2>
        <label>أمر الشراء<select value={poId} required disabled={busy} onChange={(e) => selectOrder(e.target.value)}><option value="">اختر أمرًا قابلًا للاستلام</option>{receivableOrders.map((order) => <option key={order.id} value={order.id}>{order.poNumber} — {order.supplierName || "مورد"} — {order.status}</option>)}</select></label>
        <label>المستودع المستلم<select name="warehouseId" required disabled={busy}><option value="">اختر المستودع</option>{activeWarehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.code} — {warehouse.name}</option>)}</select></label>
        {lines.length > 0 ? <div className={styles.lineEditor}><strong>الكميات المستلمة</strong>{lines.map((line, index) => <div className={styles.lineRowWide} key={line.poItemId}>
          <label>الصنف<input value={line.label} readOnly /></label><label>المطلوب<input value={line.ordered} readOnly /></label><label>مستلم سابقًا<input value={line.received} readOnly /></label><label>استلام الآن<input type="number" min="0" max={Math.max(0, line.ordered - line.received)} step="0.0001" value={line.quantity} disabled={busy} onChange={(e) => setLines((current) => current.map((row, i) => i === index ? { ...row, quantity: Number(e.target.value) } : row))} /></label><span /></div>)}</div> : <ErpEmpty>اختر أمر شراء لعرض بنوده والكميات المتبقية.</ErpEmpty>}
        <button className={styles.primaryButton} type="submit" disabled={busy || lines.length === 0}>إنشاء سند الاستلام</button>
      </form>

      <section className={styles.listCard}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>سندات الاستلام</h2><button className={styles.secondaryButton} type="button" onClick={() => void reload()} disabled={busy}>تحديث</button></div>
        {receipts.length === 0 ? <ErpEmpty>لا توجد سندات استلام.</ErpEmpty> : <div className={styles.tableWrap}><table>
          <thead><tr><th>الرقم</th><th>أمر الشراء</th><th>المستودع</th><th>البنود</th><th>الحالة</th><th>الترحيل</th></tr></thead>
          <tbody>{receipts.map((receipt) => <tr key={receipt.id}><td>{receipt.receiptNumber}</td><td>{orders.find((order) => order.id === receipt.poId)?.poNumber || receipt.poId || "—"}</td><td>{receipt.warehouseCode || warehouses.find((warehouse) => warehouse.id === receipt.warehouseId)?.name || receipt.warehouseId}</td><td><ul className={styles.documentLines}>{receipt.items.map((line) => <li key={line.id}>{line.itemCode || ""} {line.itemName || line.itemId} — {line.quantity}</li>)}</ul></td><td><Status status={receipt.status} /></td><td>{receipt.status === "DRAFT" ? <button type="button" className={styles.primaryButton} disabled={busy} onClick={() => void mutate(() => erpApi.postGoodsReceipt(receipt.id), "تم ترحيل الاستلام وتحديث المخزون وأمر الشراء.")}>ترحيل إلى المخزون</button> : receipt.postedAt ? `رُحّل ${dateTime(receipt.postedAt)}` : "—"}</td></tr>)}</tbody>
        </table></div>}
      </section>
    </div>
  </>;
}

function Status({ status }: { status: string }) { const cls = status === "POSTED" ? styles.badgeSuccess : status === "DRAFT" ? styles.badgeWarning : styles.badgeInfo; return <span className={`${styles.badge} ${cls}`}>{status}</span>; }
function dateTime(value: string) { try { return new Intl.DateTimeFormat("ar-SA", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); } catch { return value; } }
