"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { erpApi, type SupplierResponse } from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

export default function ErpSuppliersPage() {
  return <ErpWorkspace title="الموردون" description="سجل الموردين وبيانات الاتصال والضريبة وشروط الدفع ودورة الاعتماد."><SuppliersContent /></ErpWorkspace>;
}

function SuppliersContent() {
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]);
  const [editing, setEditing] = useState<SupplierResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try { setSuppliers(await erpApi.listSuppliers()); }
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
    const common = {
      name: required(form, "name"), contactEmail: text(form, "contactEmail"), contactPhone: text(form, "contactPhone"),
      address: text(form, "address"), taxNumber: text(form, "taxNumber"), paymentTerms: text(form, "paymentTerms"),
      currency: required(form, "currency") || "SAR",
    };
    if (!common.name) { setError("اسم المورد مطلوب."); return; }
    if (editing) {
      const saved = await mutate(() => erpApi.updateSupplier(editing.id, { ...common, expectedVersion: editing.version }), "تم تحديث المورد.");
      if (saved) setEditing(null);
    } else {
      const supplierCode = required(form, "supplierCode");
      if (!supplierCode) { setError("كود المورد مطلوب."); return; }
      const saved = await mutate(() => erpApi.createSupplier({ supplierCode, ...common }), "تم إنشاء المورد.");
      if (saved) formElement.reset();
    }
  }

  return <>
    <ErpFeedback error={error} notice={notice} />
    <div className={styles.workspace}>
      <form key={editing?.id ?? "new"} className={styles.formCard} onSubmit={submit}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>{editing ? "تعديل المورد" : "إضافة مورد"}</h2>{editing ? <button type="button" className={styles.secondaryButton} onClick={() => setEditing(null)}>إلغاء</button> : null}</div>
        <label>كود المورد<input name="supplierCode" required={!editing} disabled={Boolean(editing) || busy} defaultValue={editing?.supplierCode ?? ""} /></label>
        <label>اسم المورد<input name="name" required disabled={busy} defaultValue={editing?.name ?? ""} /></label>
        <div className={styles.formGrid}>
          <label>البريد الإلكتروني<input name="contactEmail" type="email" disabled={busy} defaultValue={editing?.contactEmail ?? ""} /></label>
          <label>الهاتف<input name="contactPhone" disabled={busy} defaultValue={editing?.contactPhone ?? ""} /></label>
          <label>الرقم الضريبي<input name="taxNumber" disabled={busy} defaultValue={editing?.taxNumber ?? ""} /></label>
          <label>العملة<input name="currency" maxLength={3} disabled={busy} defaultValue={editing?.currency ?? "SAR"} /></label>
        </div>
        <label>العنوان<textarea name="address" disabled={busy} defaultValue={editing?.address ?? ""} /></label>
        <label>شروط الدفع<input name="paymentTerms" disabled={busy} defaultValue={editing?.paymentTerms ?? ""} placeholder="مثال: 30 يوم" /></label>
        <button className={styles.primaryButton} type="submit" disabled={busy}>{editing ? "حفظ التعديل" : "إنشاء المورد"}</button>
      </form>

      <section className={styles.listCard}>
        <div className={styles.toolbar}><h2 className={styles.sectionHeading}>سجل الموردين</h2><button type="button" className={styles.secondaryButton} onClick={() => void reload()} disabled={busy}>تحديث</button></div>
        {loading ? <ErpLoading /> : suppliers.length === 0 ? <ErpEmpty>لا يوجد موردون. أضف المورد الأول لبدء دورة المشتريات.</ErpEmpty> : <div className={styles.tableWrap}><table>
          <thead><tr><th>الكود</th><th>المورد</th><th>الاتصال</th><th>العملة</th><th>الحالة</th><th>إجراءات</th></tr></thead>
          <tbody>{suppliers.map((supplier) => <tr key={supplier.id}>
            <td>{supplier.supplierCode}</td><td>{supplier.name}<div className={styles.muted}>{supplier.taxNumber || "بدون رقم ضريبي"}</div></td>
            <td>{supplier.contactEmail || "—"}<div className={styles.muted}>{supplier.contactPhone || "—"}</div></td><td>{supplier.currency}</td>
            <td><span className={`${styles.badge} ${supplier.status === "ACTIVE" ? styles.badgeSuccess : supplier.status === "PENDING" ? styles.badgeWarning : ""}`}>{supplier.status}</span></td>
            <td><div className={styles.rowActions}>
              <button type="button" className={styles.secondaryButton} disabled={busy} onClick={() => setEditing(supplier)}>تعديل</button>
              {supplier.status !== "ACTIVE" && supplier.status !== "BLOCKED" && supplier.status !== "ARCHIVED" ? <button type="button" className={styles.button} disabled={busy} onClick={() => void mutate(() => erpApi.activateSupplier(supplier.id), "تم تفعيل المورد.")}>تفعيل</button> : null}
              {supplier.status !== "BLOCKED" && supplier.status !== "ARCHIVED" ? <button type="button" className={styles.dangerButton} disabled={busy} onClick={() => void mutate(() => erpApi.blockSupplier(supplier.id), "تم حظر المورد.")}>حظر</button> : null}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
      </section>
    </div>
  </>;
}

function required(form: FormData, key: string) { return String(form.get(key) ?? "").trim(); }
function text(form: FormData, key: string) { const value = required(form, key); return value || null; }
