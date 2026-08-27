"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  erpApi,
  type AdjustmentResponse,
  type InventoryBalanceResponse,
  type InventorySummary,
  type ItemResponse,
  type MovementResponse,
  type ReservationResponse,
  type TransferResponse,
  type WarehouseResponse,
} from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

type TransferLine = { itemId: string; quantity: number };

export default function ErpInventoryPage() {
  return <ErpWorkspace title="تشغيل المخزون" description="الأرصدة والحجوزات والتحويلات والتسويات وسجل الحركة غير القابل للتعديل."><InventoryContent /></ErpWorkspace>;
}

function InventoryContent() {
  const [summary, setSummary] = useState<InventorySummary | null>(null);
  const [balances, setBalances] = useState<InventoryBalanceResponse[]>([]);
  const [items, setItems] = useState<ItemResponse[]>([]);
  const [warehouses, setWarehouses] = useState<WarehouseResponse[]>([]);
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [movements, setMovements] = useState<MovementResponse[]>([]);
  const [transfers, setTransfers] = useState<TransferResponse[]>([]);
  const [adjustments, setAdjustments] = useState<AdjustmentResponse[]>([]);
  const [warehouseFilter, setWarehouseFilter] = useState("");
  const [transferLines, setTransferLines] = useState<TransferLine[]>([{ itemId: "", quantity: 1 }]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [inventorySummary, balanceRows, itemRows, warehouseRows, reservationRows, movementRows, transferRows, adjustmentRows] = await Promise.all([
        erpApi.inventorySummary(), erpApi.listBalances(), erpApi.listItems(), erpApi.listWarehouses(),
        erpApi.listReservations(), erpApi.listMovements(), erpApi.listTransfers(), erpApi.listAdjustments(),
      ]);
      setSummary(inventorySummary); setBalances(balanceRows); setItems(itemRows); setWarehouses(warehouseRows);
      setReservations(reservationRows); setMovements(movementRows); setTransfers(transferRows); setAdjustments(adjustmentRows);
    } catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  const activeItems = useMemo(() => items.filter((item) => item.status === "ACTIVE" && item.trackInventory), [items]);
  const activeWarehouses = useMemo(() => warehouses.filter((warehouse) => warehouse.status === "ACTIVE"), [warehouses]);
  const visibleBalances = useMemo(() => warehouseFilter ? balances.filter((row) => row.warehouseId === warehouseFilter) : balances, [balances, warehouseFilter]);
  const itemName = (id: string) => items.find((item) => item.id === id)?.name ?? id;
  const warehouseName = (id: string) => warehouses.find((warehouse) => warehouse.id === id)?.name ?? id;

  async function mutate(action: () => Promise<unknown>, message: string) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(message); await reload(); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setBusy(false); }
  }

  async function createReservation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    await mutate(() => erpApi.createReservation({
      warehouseId: value(form, "warehouseId"), itemId: value(form, "itemId"), quantity: numberValue(form, "quantity"),
      source: nullable(form, "source"), externalReference: nullable(form, "externalReference"), expiresAt: null,
    }), "تم إنشاء حجز المخزون.");
    event.currentTarget.reset();
  }

  async function createTransfer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    const lines = transferLines.filter((line) => line.itemId && line.quantity > 0);
    if (lines.length === 0) { setError("أضف صنفًا واحدًا على الأقل للتحويل."); return; }
    await mutate(() => erpApi.createTransfer({ fromWarehouseId: value(form, "fromWarehouseId"), toWarehouseId: value(form, "toWarehouseId"), items: lines }), "تم إنشاء تحويل المخزون.");
    setTransferLines([{ itemId: "", quantity: 1 }]); event.currentTarget.reset();
  }

  async function createAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    await mutate(() => erpApi.createAdjustment({
      warehouseId: value(form, "warehouseId"), itemId: value(form, "itemId"), quantityDelta: numberValue(form, "quantityDelta"),
      reasonCode: value(form, "reasonCode"), notes: nullable(form, "notes"),
    }), "تم إنشاء تسوية المخزون وإرسالها للاعتماد.");
    event.currentTarget.reset();
  }

  if (loading) return <ErpLoading />;

  return <>
    <ErpFeedback error={error} notice={notice} />
    {summary ? <section className={styles.metrics}>
      <Metric label="الأصناف النشطة" value={summary.activeItems} />
      <Metric label="المستودعات" value={summary.totalWarehouses} />
      <Metric label="مخزون منخفض" value={summary.lowStockItems} />
      <Metric label="قيمة المخزون" value={`${Number(summary.totalInventoryValue || 0).toLocaleString("ar-SA")} ر.س`} />
    </section> : null}

    <section className={styles.sectionCard}>
      <div className={styles.toolbar}><h2 className={styles.sectionHeading}>أرصدة المخزون</h2><select aria-label="تصفية حسب المستودع" value={warehouseFilter} onChange={(e) => setWarehouseFilter(e.target.value)}><option value="">كل المستودعات</option>{activeWarehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>)}</select><button className={styles.secondaryButton} type="button" disabled={busy} onClick={() => void reload()}>تحديث</button></div>
      {visibleBalances.length === 0 ? <ErpEmpty>لا توجد أرصدة بعد. يتم إنشاء الرصيد عند أول حركة مخزون أو استلام.</ErpEmpty> : <div className={styles.tableWrap}><table>
        <thead><tr><th>المستودع</th><th>الصنف</th><th>المتاح</th><th>على اليد</th><th>محجوز</th><th>قادم</th><th>آخر تحديث</th></tr></thead>
        <tbody>{visibleBalances.map((row) => <tr key={row.id}><td>{row.warehouseCode || warehouseName(row.warehouseId)}</td><td>{row.itemName || itemName(row.itemId)}<div className={styles.muted}>{row.itemCode || ""}</div></td><td>{row.available}</td><td>{row.onHand}</td><td>{row.reserved}</td><td>{row.incoming}</td><td>{dateTime(row.updatedAt)}</td></tr>)}</tbody>
      </table></div>}
    </section>

    <div className={styles.splitSections}>
      <section className={styles.sectionCard}>
        <h2 className={styles.sectionHeading}>حجوزات المخزون</h2>
        <form className={styles.formCard} onSubmit={createReservation}>
          <label>المستودع<select name="warehouseId" required disabled={busy}><option value="">اختر</option>{activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</select></label>
          <label>الصنف<select name="itemId" required disabled={busy}><option value="">اختر</option>{activeItems.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}</select></label>
          <div className={styles.formGrid}><label>الكمية<input name="quantity" type="number" min="0.0001" step="0.0001" required disabled={busy} /></label><label>المصدر<input name="source" disabled={busy} placeholder="ORDER / MANUAL" /></label></div>
          <label>مرجع خارجي<input name="externalReference" disabled={busy} /></label>
          <button className={styles.primaryButton} type="submit" disabled={busy}>إنشاء حجز</button>
        </form>
        {reservations.length === 0 ? <ErpEmpty>لا توجد حجوزات.</ErpEmpty> : <div className={styles.tableWrap}><table><thead><tr><th>الصنف</th><th>المستودع</th><th>الكمية</th><th>الحالة</th><th>إجراء</th></tr></thead><tbody>{reservations.map((row) => <tr key={row.id}><td>{itemName(row.itemId)}</td><td>{warehouseName(row.warehouseId)}</td><td>{row.quantity}</td><td><Status status={row.status} /></td><td><div className={styles.rowActions}>{row.status === "RESERVED" ? <><button className={styles.button} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.confirmReservation(row.id), "تم تأكيد الحجز وصرف الكمية.")}>تأكيد/صرف</button><button className={styles.dangerButton} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.releaseReservation(row.id), "تم تحرير الحجز.")}>تحرير</button></> : null}</div></td></tr>)}</tbody></table></div>}
      </section>

      <section className={styles.sectionCard}>
        <h2 className={styles.sectionHeading}>تحويل بين المستودعات</h2>
        <form className={styles.formCard} onSubmit={createTransfer}>
          <div className={styles.formGrid}><label>من مستودع<select name="fromWarehouseId" required disabled={busy}><option value="">اختر</option>{activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</select></label><label>إلى مستودع<select name="toWarehouseId" required disabled={busy}><option value="">اختر</option>{activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</select></label></div>
          <div className={styles.lineEditor}><strong>أصناف التحويل</strong>{transferLines.map((line, index) => <div className={styles.lineRow} key={index}><label>الصنف<select value={line.itemId} onChange={(e) => changeTransferLine(index, { itemId: e.target.value })}><option value="">اختر</option>{activeItems.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}</select></label><label>الكمية<input type="number" min="0.0001" step="0.0001" value={line.quantity} onChange={(e) => changeTransferLine(index, { quantity: Number(e.target.value) })} /></label><span /><button type="button" className={styles.dangerButton} disabled={transferLines.length === 1} onClick={() => setTransferLines((rows) => rows.filter((_, i) => i !== index))}>حذف</button></div>)}<button type="button" className={styles.secondaryButton} onClick={() => setTransferLines((rows) => [...rows, { itemId: "", quantity: 1 }])}>إضافة سطر</button></div>
          <button className={styles.primaryButton} type="submit" disabled={busy}>إنشاء التحويل</button>
        </form>
        {transfers.length === 0 ? <ErpEmpty>لا توجد تحويلات.</ErpEmpty> : <div className={styles.tableWrap}><table><thead><tr><th>الرقم</th><th>المسار</th><th>الأصناف</th><th>الحالة</th><th>إجراء</th></tr></thead><tbody>{transfers.map((row) => <tr key={row.id}><td>{row.transferNumber}</td><td>{row.fromWarehouseCode || warehouseName(row.fromWarehouseId)} ← {row.toWarehouseCode || warehouseName(row.toWarehouseId)}</td><td>{row.items.map((line) => `${line.itemName || itemName(line.itemId)} (${line.quantity})`).join("، ")}</td><td><Status status={row.status} /></td><td><div className={styles.rowActions}>{row.status === "DRAFT" ? <button type="button" className={styles.button} disabled={busy} onClick={() => void mutate(() => erpApi.submitTransfer(row.id), "تم إرسال التحويل.")}>إرسال</button> : null}{["SUBMITTED", "IN_TRANSIT"].includes(row.status) ? <button type="button" className={styles.primaryButton} disabled={busy} onClick={() => void mutate(() => erpApi.receiveTransfer(row.id), "تم استلام التحويل وتحديث المخزون.")}>استلام</button> : null}</div></td></tr>)}</tbody></table></div>}
      </section>
    </div>

    <section className={styles.sectionCard}>
      <h2 className={styles.sectionHeading}>تسويات المخزون</h2>
      <form className={styles.formGrid} onSubmit={createAdjustment}>
        <label>المستودع<select name="warehouseId" required disabled={busy}><option value="">اختر</option>{activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</select></label>
        <label>الصنف<select name="itemId" required disabled={busy}><option value="">اختر</option>{activeItems.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}</select></label>
        <label>فرق الكمية<input name="quantityDelta" type="number" step="0.0001" required disabled={busy} placeholder="مثال: 5 أو -3" /></label>
        <label>سبب التسوية<input name="reasonCode" required disabled={busy} placeholder="COUNT / DAMAGE / CORRECTION" /></label>
        <label>ملاحظات<textarea name="notes" disabled={busy} /></label><div className={styles.inlineActions}><button className={styles.primaryButton} type="submit" disabled={busy}>إنشاء تسوية</button></div>
      </form>
      {adjustments.length === 0 ? <ErpEmpty>لا توجد تسويات.</ErpEmpty> : <div className={styles.tableWrap}><table><thead><tr><th>الرقم</th><th>المستودع</th><th>الصنف</th><th>الفرق</th><th>السبب</th><th>الحالة</th><th>إجراء</th></tr></thead><tbody>{adjustments.map((row) => <tr key={row.id}><td>{row.adjustmentNumber}</td><td>{row.warehouseCode || warehouseName(row.warehouseId)}</td><td>{row.itemName || itemName(row.itemId)}</td><td>{row.quantityDelta}</td><td>{row.reasonCode}</td><td><Status status={row.status} /></td><td>{row.status === "PENDING" ? <button type="button" className={styles.primaryButton} disabled={busy} onClick={() => void mutate(() => erpApi.approveAdjustment(row.id), "تم اعتماد التسوية.")}>اعتماد</button> : null}</td></tr>)}</tbody></table></div>}
    </section>

    <section className={styles.sectionCard}>
      <h2 className={styles.sectionHeading}>سجل حركة المخزون</h2>
      {movements.length === 0 ? <ErpEmpty>لا توجد حركات مخزون بعد.</ErpEmpty> : <div className={styles.tableWrap}><table><thead><tr><th>الوقت</th><th>النوع</th><th>المستودع</th><th>الصنف</th><th>الكمية</th><th>المرجع/السبب</th></tr></thead><tbody>{movements.map((row) => <tr key={row.id}><td>{dateTime(row.createdAt)}</td><td><Status status={row.movementType} /></td><td>{warehouseName(row.warehouseId)}</td><td>{itemName(row.itemId)}</td><td>{row.quantity}</td><td>{row.referenceType || "—"}<div className={styles.muted}>{row.reason || ""}</div></td></tr>)}</tbody></table></div>}
    </section>
  </>;

  function changeTransferLine(index: number, patch: Partial<TransferLine>) {
    setTransferLines((rows) => rows.map((row, i) => i === index ? { ...row, ...patch } : row));
  }
}

function Metric({ label, value }: { label: string; value: string | number }) { return <div className={styles.metric}><div className={styles.metricLabel}>{label}</div><div className={styles.metricValue}>{value}</div></div>; }
function Status({ status }: { status: string }) { const cls = ["ACTIVE", "APPROVED", "POSTED", "RECEIVED", "CONFIRMED"].includes(status) ? styles.badgeSuccess : ["PENDING", "DRAFT", "SUBMITTED", "RESERVED"].includes(status) ? styles.badgeWarning : styles.badgeInfo; return <span className={`${styles.badge} ${cls}`}>{status}</span>; }
function value(form: FormData, key: string) { return String(form.get(key) ?? "").trim(); }
function nullable(form: FormData, key: string) { const result = value(form, key); return result || null; }
function numberValue(form: FormData, key: string) { const result = Number(form.get(key)); return Number.isFinite(result) ? result : 0; }
function dateTime(value: string) { try { return new Intl.DateTimeFormat("ar-SA", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); } catch { return value; } }
