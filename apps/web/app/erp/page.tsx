"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { erpApi, type ErpDashboardSummary, type ItemResponse } from "@/lib/api/erp-api";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ErpWorkspace } from "./components/erp-workspace";
import { ErpEmpty, ErpFeedback, ErpLoading } from "./components/erp-feedback";
import styles from "./erp.module.css";

const QUICK_LINKS = [
  { href: "/erp/items", title: "إدارة الأصناف", hint: "إنشاء الأصناف وتفعيلها وضبط مستويات إعادة الطلب." },
  { href: "/erp/warehouses", title: "إدارة المستودعات", hint: "إنشاء المستودعات وتحديد مواقعها وحالتها." },
  { href: "/erp/suppliers", title: "إدارة الموردين", hint: "إضافة الموردين وتجهيزهم للمشتريات." },
  { href: "/erp/inventory", title: "تشغيل المخزون", hint: "الأرصدة والحجوزات والتحويلات والتسويات." },
  { href: "/erp/requisitions", title: "طلبات الشراء", hint: "إنشاء الطلبات وإرسالها للاعتماد." },
  { href: "/erp/purchase-orders", title: "أوامر الشراء", hint: "إنشاء واعتماد أوامر الشراء ومتابعة حالتها." },
  { href: "/erp/goods-receipts", title: "استلام البضاعة", hint: "استلام أوامر الشراء وترحيلها إلى المخزون." },
] as const;

export default function ErpPage() {
  return (
    <ErpWorkspace title="منصة ERP" description="مركز التشغيل للمخزون والموردين والمشتريات والاستلام.">
      <ErpDashboardContent />
    </ErpWorkspace>
  );
}

function ErpDashboardContent() {
  const [summary, setSummary] = useState<ErpDashboardSummary | null>(null);
  const [lowStock, setLowStock] = useState<ItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [dashboard, low] = await Promise.all([erpApi.dashboard(), erpApi.lowStockItems()]);
      setSummary(dashboard);
      setLowStock(low ?? []);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  if (loading) return <ErpLoading />;

  return (
    <>
      <ErpFeedback error={error} />
      {summary ? (
        <section className={styles.metrics} aria-label="ملخص ERP">
          <Metric label="الأصناف" value={summary.totalItems} />
          <Metric label="المستودعات" value={summary.totalWarehouses} />
          <Metric label="الموردون" value={summary.totalSuppliers} />
          <Metric label="مخزون منخفض" value={summary.lowStockItems} />
          <Metric label="طلبات شراء معلقة" value={summary.pendingRequisitions} />
          <Metric label="أوامر شراء معلقة" value={summary.pendingPurchaseOrders} />
          <Metric label="أصناف نشطة" value={summary.activeItems} />
          <Metric label="قيمة المخزون" value={`${Number(summary.totalInventoryValue || 0).toLocaleString("ar-SA")} ر.س`} />
        </section>
      ) : null}

      <section className={styles.quickGrid} aria-label="إجراءات ERP السريعة">
        {QUICK_LINKS.map((item) => (
          <Link className={styles.quickLink} href={item.href} key={item.href}>
            <div className={styles.quickTitle}>{item.title}</div>
            <div className={styles.muted}>{item.hint}</div>
          </Link>
        ))}
      </section>

      <section className={styles.sectionCard}>
        <div className={styles.toolbar}>
          <h2 className={styles.sectionHeading}>الأصناف التي وصلت إلى حد إعادة الطلب</h2>
          <button className={styles.secondaryButton} type="button" onClick={() => void reload()}>تحديث</button>
        </div>
        {lowStock.length === 0 ? (
          <ErpEmpty>لا توجد أصناف منخفضة المخزون حاليًا.</ErpEmpty>
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead><tr><th>الكود</th><th>الصنف</th><th>الوحدة</th><th>حد إعادة الطلب</th><th>كمية إعادة الطلب</th></tr></thead>
              <tbody>
                {lowStock.map((item) => (
                  <tr key={item.id}>
                    <td>{item.code}</td><td>{item.name}</td><td>{item.unitOfMeasure}</td>
                    <td>{item.reorderLevel}</td><td>{item.reorderQuantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return <div className={styles.metric}><div className={styles.metricLabel}>{label}</div><div className={styles.metricValue}>{value}</div></div>;
}
