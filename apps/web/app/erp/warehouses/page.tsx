"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { erpApi, type WarehouseResponse } from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

export default function ErpWarehousesPage() {
  return <ErpWorkspace title="المستودعات" description="إنشاء المستودعات وتحديد مواقعها وإدارتها ضمن نطاق المستأجر."><WarehousesContent /></ErpWorkspace>;
}

function WarehousesContent() {
  const [warehouses, setWarehouses] = useState<WarehouseResponse[]>([]);
  const [editing, setEditing] = useState<WarehouseResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try { setWarehouses(await erpApi.listWarehouses()); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  async function mutate(action: () => Promise<unknown>, message: string): Promise<boolean> {
    setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(message); await reload(); return true; }
    catch (reason) { setError(toUserFacingError(reason).message); return false; }
    finally { setBusy(false); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const name = String(form.get("name") ?? "").trim();
    const location = String(form.get("location") ?? "").trim() || null;
    if (!name) { setError("اسم المستودع مطلوب."); return; }
    if (editing) {
      const saved = await mutate(() => erpApi.updateWarehouse(editing.id, { name, location, expectedVersion: editing.version }), "تم تحديث المستودع.");
      if (saved) setEditing(null);
    } else {
      const code = String(form.get("code") ?? "").trim();
      if (!code) { setError("كود المستودع مطلوب."); return; }
      const saved = await mutate(() => erpApi.createWarehouse({ code, name, location, isPrimary: form.get("isPrimary") === "on" }), "تم إنشاء المستودع.");
      if (saved) formElement.reset();
    }
  }

  return <>
    <ErpFeedback error={error} notice={notice} />
    <div className={styles.workspace}>
      <form key={editing?.id ?? "new"} className={styles.formCard} onSubmit={submit}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>{editing ? "تعديل المستودع" : "إضافة مستودع"}</h2>{editing ? <button type="button" className={styles.secondaryButton} onClick={() => setEditing(null)}>إلغاء</button> : null}</div>
        <label>كود المستودع<input name="code" required={!editing} disabled={Boolean(editing) || busy} defaultValue={editing?.code ?? ""} /></label>
        <label>اسم المستودع<input name="name" required disabled={busy} defaultValue={editing?.name ?? ""} /></label>
        <label>الموقع<input name="location" disabled={busy} defaultValue={editing?.location ?? ""} placeholder="مثال: جدة - المنطقة الصناعية" /></label>
        {!editing ? <label className={styles.checkRow}><input name="isPrimary" type="checkbox" disabled={busy} /> مستودع رئيسي</label> : <div className={styles.muted}>{editing.isPrimary ? "هذا هو المستودع الرئيسي." : "مستودع فرعي."}</div>}
        <button className={styles.primaryButton} type="submit" disabled={busy}>{editing ? "حفظ التعديل" : "إنشاء المستودع"}</button>
      </form>

      <section className={styles.listCard}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>سجل المستودعات</h2><button type="button" className={styles.secondaryButton} onClick={() => void reload()} disabled={busy}>تحديث</button></div>
        {loading ? <ErpLoading /> : warehouses.length === 0 ? <ErpEmpty>لا توجد مستودعات. أنشئ مستودعًا قبل تشغيل المخزون والاستلام.</ErpEmpty> : <div className={styles.tableWrap}><table>
          <thead><tr><th>الكود</th><th>الاسم</th><th>الموقع</th><th>النوع</th><th>الحالة</th><th>إجراءات</th></tr></thead>
          <tbody>{warehouses.map((warehouse) => <tr key={warehouse.id}>
            <td>{warehouse.code}</td><td>{warehouse.name}</td><td>{warehouse.location || "—"}</td><td>{warehouse.isPrimary ? "رئيسي" : "فرعي"}</td>
            <td><span className={`${styles.badge} ${warehouse.status === "ACTIVE" ? styles.badgeSuccess : ""}`}>{warehouse.status}</span></td>
            <td><div className={styles.rowActions}>
              <button type="button" className={styles.secondaryButton} disabled={busy} onClick={() => setEditing(warehouse)}>تعديل</button>
              {warehouse.status !== "ACTIVE" && warehouse.status !== "ARCHIVED" ? <button type="button" className={styles.button} disabled={busy} onClick={() => void mutate(() => erpApi.activateWarehouse(warehouse.id), "تم تفعيل المستودع.")}>تفعيل</button> : null}
              {warehouse.status !== "ARCHIVED" ? <button type="button" className={styles.dangerButton} disabled={busy} onClick={() => void mutate(() => erpApi.archiveWarehouse(warehouse.id), "تمت أرشفة المستودع.")}>أرشفة</button> : null}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
      </section>
    </div>
  </>;
}
