"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { erpApi, type ItemResponse, type ItemType, type UnitOfMeasure } from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "../components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "../components/erp-feedback";
import styles from "../erp.module.css";

const ITEM_TYPES: { value: ItemType; label: string }[] = [
  { value: "GOODS", label: "بضاعة" }, { value: "SERVICE", label: "خدمة" },
  { value: "DIGITAL", label: "رقمي" }, { value: "RAW_MATERIAL", label: "مادة خام" },
  { value: "FINISHED_GOOD", label: "منتج نهائي" },
];
const UNITS: UnitOfMeasure[] = ["EACH", "UNIT", "KG", "G", "L", "M", "CM", "BOX", "PACK"];

export default function ErpItemsPage() {
  return <ErpWorkspace title="الأصناف" description="إنشاء وإدارة سجل الأصناف ودورة حياتها ومستويات إعادة الطلب."><ItemsContent /></ErpWorkspace>;
}

function ItemsContent() {
  const [items, setItems] = useState<ItemResponse[]>([]);
  const [editing, setEditing] = useState<ItemResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    setLoading(true); setError("");
    try { setItems(await erpApi.listItems()); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  async function mutate(action: () => Promise<unknown>, message: string) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(message); await reload(); }
    catch (reason) { setError(toUserFacingError(reason).message); }
    finally { setBusy(false); }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const common = {
      sku: text(form, "sku"), name: required(form, "name"), description: text(form, "description"),
      itemType: required(form, "itemType") as ItemType,
      unitOfMeasure: required(form, "unitOfMeasure") as UnitOfMeasure,
      trackInventory: form.get("trackInventory") === "on",
      reorderLevel: numeric(form, "reorderLevel"), reorderQuantity: numeric(form, "reorderQuantity"),
    };
    if (!common.name) { setError("اسم الصنف مطلوب."); return; }
    if (editing) {
      await mutate(() => erpApi.updateItem(editing.id, { ...common, expectedVersion: editing.version }), "تم تحديث الصنف.");
      setEditing(null);
    } else {
      const code = required(form, "code");
      if (!code) { setError("كود الصنف مطلوب."); return; }
      await mutate(() => erpApi.createItem({ code, ...common }), "تم إنشاء الصنف.");
      event.currentTarget.reset();
    }
  }

  return (
    <>
      <ErpFeedback error={error} notice={notice} />
      <div className={styles.workspace}>
        <form key={editing?.id ?? "new"} className={styles.formCard} onSubmit={handleSubmit}>
          <div className={styles.toolbar}>
            <h2 className={styles.sectionHeading}>{editing ? "تعديل الصنف" : "إضافة صنف"}</h2>
            {editing ? <button className={styles.secondaryButton} type="button" onClick={() => setEditing(null)}>إلغاء التعديل</button> : null}
          </div>
          <label>الكود<input name="code" required={!editing} disabled={Boolean(editing) || busy} defaultValue={editing?.code ?? ""} /></label>
          <label>SKU<input name="sku" disabled={busy} defaultValue={editing?.sku ?? ""} /></label>
          <label>اسم الصنف<input name="name" required disabled={busy} defaultValue={editing?.name ?? ""} /></label>
          <label>الوصف<textarea name="description" disabled={busy} defaultValue={editing?.description ?? ""} /></label>
          <div className={styles.formGrid}>
            <label>النوع<select name="itemType" disabled={busy} defaultValue={editing?.itemType ?? "GOODS"}>{ITEM_TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}</select></label>
            <label>وحدة القياس<select name="unitOfMeasure" disabled={busy} defaultValue={editing?.unitOfMeasure ?? "EACH"}>{UNITS.map((unit) => <option key={unit} value={unit}>{unit}</option>)}</select></label>
            <label>حد إعادة الطلب<input name="reorderLevel" type="number" step="0.0001" min="0" disabled={busy} defaultValue={editing?.reorderLevel ?? 0} /></label>
            <label>كمية إعادة الطلب<input name="reorderQuantity" type="number" step="0.0001" min="0" disabled={busy} defaultValue={editing?.reorderQuantity ?? 0} /></label>
          </div>
          <label className={styles.checkRow}><input name="trackInventory" type="checkbox" disabled={busy} defaultChecked={editing?.trackInventory ?? true} /> تتبع المخزون لهذا الصنف</label>
          <button className={styles.primaryButton} type="submit" disabled={busy}>{editing ? "حفظ التعديل" : "إنشاء الصنف"}</button>
        </form>

        <section className={styles.listCard}>
          <div className={styles.toolbar}><h2 className={styles.sectionHeading}>سجل الأصناف</h2><button className={styles.secondaryButton} type="button" onClick={() => void reload()} disabled={busy}>تحديث</button></div>
          {loading ? <ErpLoading /> : items.length === 0 ? <ErpEmpty>لا توجد أصناف. استخدم النموذج لإنشاء أول صنف.</ErpEmpty> : (
            <div className={styles.tableWrap}><table>
              <thead><tr><th>الكود</th><th>الاسم</th><th>النوع</th><th>الوحدة</th><th>إعادة الطلب</th><th>الحالة</th><th>إجراءات</th></tr></thead>
              <tbody>{items.map((item) => <tr key={item.id}>
                <td>{item.code}<div className={styles.muted}>{item.sku || "—"}</div></td>
                <td>{item.name}</td><td>{item.itemType}</td><td>{item.unitOfMeasure}</td>
                <td>{item.reorderLevel} / {item.reorderQuantity}</td><td><Status status={item.status} /></td>
                <td><div className={styles.rowActions}>
                  <button className={styles.secondaryButton} type="button" disabled={busy} onClick={() => setEditing(item)}>تعديل</button>
                  {item.status !== "ACTIVE" && item.status !== "ARCHIVED" ? <button className={styles.button} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.activateItem(item.id), "تم تفعيل الصنف.")}>تفعيل</button> : null}
                  {item.status === "ACTIVE" ? <button className={styles.button} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.inactivateItem(item.id), "تم إيقاف الصنف.")}>إيقاف</button> : null}
                  {item.status !== "ARCHIVED" ? <button className={styles.dangerButton} type="button" disabled={busy} onClick={() => void mutate(() => erpApi.archiveItem(item.id), "تمت أرشفة الصنف.")}>أرشفة</button> : null}
                </div></td>
              </tr>)}</tbody>
            </table></div>
          )}
        </section>
      </div>
    </>
  );
}

function Status({ status }: { status: string }) {
  const cls = status === "ACTIVE" ? styles.badgeSuccess : status === "DRAFT" ? styles.badgeWarning : "";
  return <span className={`${styles.badge} ${cls}`}>{status}</span>;
}
function required(form: FormData, key: string) { return String(form.get(key) ?? "").trim(); }
function text(form: FormData, key: string) { const value = required(form, key); return value || null; }
function numeric(form: FormData, key: string) { const value = Number(form.get(key) ?? 0); return Number.isFinite(value) ? value : 0; }
