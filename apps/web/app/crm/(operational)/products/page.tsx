"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmProduct } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue, formatDateTime } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/**
 * CRM Products route — /crm/products
 *
 * Product/service catalog management with pricing, tax, and category.
 *
 * Branch: feature/crm-products
 */
export default function CrmProductsPage() {
  const { t } = useI18n();
  const [products, setProducts] = useState<CrmProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");

  const reload = useCallback(async (s?: string) => {
    setLoading(true); setError("");
    try { setProducts(await crmApi.products(s || undefined)); }
    catch (r) { setError(toUserFacingError(r).message); setProducts([]); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(search), 300);
    return () => window.clearTimeout(timer);
  }, [reload, search]);

  async function mutate(action: () => Promise<unknown>, msg: string) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(msg); await reload(search); }
    catch (r) { setError(toUserFacingError(r).message); }
    finally { setBusy(false); }
  }

  async function handleCreate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    await mutate(() => crmApi.createProduct({
      name: formValue(f, "name"),
      sku: optionalValue(f, "sku"),
      description: optionalValue(f, "description"),
      productType: optionalValue(f, "productType") || "PRODUCT",
      category: optionalValue(f, "category"),
      unitPrice: optionalValue(f, "unitPrice") || "0",
      currencyCode: optionalValue(f, "currencyCode") || "USD",
      unit: optionalValue(f, "unit") || "EA",
    }), t("crm.products.created"));
    e.currentTarget.reset();
  }

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.products.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.products.description")}</p>
      </div>
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <form className={styles.formCard} onSubmit={handleCreate}>
          <h2 className={styles.sectionHeading}>{t("crm.products.create.title")}</h2>
          <label>{t("crm.products.create.name")}<input name="name" required maxLength={240} disabled={busy} /></label>
          <label>{t("crm.products.create.sku")}<input name="sku" maxLength={80} disabled={busy} /></label>
          <label>{t("crm.products.create.type")}
            <select name="productType" defaultValue="PRODUCT" disabled={busy}>
              <option value="PRODUCT">{t("crm.products.type.PRODUCT")}</option>
              <option value="SERVICE">{t("crm.products.type.SERVICE")}</option>
            </select>
          </label>
          <label>{t("crm.products.create.category")}<input name="category" maxLength={120} disabled={busy} /></label>
          <label>{t("crm.products.create.price")}<input name="unitPrice" type="number" step="0.01" defaultValue="0" disabled={busy} /></label>
          <label>{t("crm.products.create.currency")}<input name="currencyCode" maxLength={3} defaultValue="USD" disabled={busy} /></label>
          <label>{t("crm.products.create.unit")}<input name="unit" maxLength={40} defaultValue="EA" disabled={busy} /></label>
          <label>{t("crm.products.create.description")}<textarea name="description" rows={2} maxLength={4000} disabled={busy} /></label>
          <button type="submit" disabled={busy}>{t("crm.products.create.submit")}</button>
        </form>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.products.list.title")}</h2>
            <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t("crm.products.search")} disabled={busy} />
          </div>
          {loading ? <CrmLoading rows={4} /> : products.length === 0 ? (
            <CrmEmpty title={t("crm.products.empty")} hint={t("crm.state.emptyHint")} />
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.products.list.name")}</th>
                    <th>{t("crm.products.list.sku")}</th>
                    <th>{t("crm.products.list.type")}</th>
                    <th>{t("crm.products.list.price")}</th>
                    <th>{t("crm.products.list.active")}</th>
                    <th>{t("crm.products.list.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((p) => (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td>{p.sku || "—"}</td>
                      <td><span className={styles.badge}>{p.product_type}</span></td>
                      <td>{Number(p.unit_price).toFixed(2)} {p.currency_code}</td>
                      <td>{p.active ? "✓" : "—"}</td>
                      <td>
                        <button type="button" disabled={busy} onClick={() => void mutate(() => crmApi.deleteProduct(p.id), t("crm.products.deleted"))}>
                          {t("crm.products.list.delete")}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
