"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  platformOperationsApi,
  type ExecutiveDashboard,
  type ManagedTenant,
  type SystemService,
} from "@/lib/api/platform-operations";
import styles from "./control-plane.module.css";

function number(value: number): string {
  return new Intl.NumberFormat("ar-SA").format(value);
}

function date(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function statusLabel(value: string): string {
  const labels: Record<string, string> = {
    ACTIVE: "نشط",
    TRIAL: "تجريبي",
    SUSPENDED: "موقوف",
    PAST_DUE: "متأخر",
    CANCELLED: "ملغى",
    ARCHIVED: "مؤرشف",
    PENDING: "قيد التجهيز",
    OPERATIONAL: "يعمل",
    DEGRADED: "متدهور",
    MAINTENANCE: "صيانة",
    DISABLED: "معطل",
    INCIDENT: "حادث",
  };
  return labels[value] ?? value;
}

export default function ControlPlanePage() {
  const { state, me } = useAuth();
  const router = useRouter();
  const [dashboard, setDashboard] = useState<ExecutiveDashboard | null>(null);
  const [tenants, setTenants] = useState<ManagedTenant[]>([]);
  const [systems, setSystems] = useState<SystemService[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([
      platformOperationsApi.dashboard(),
      platformOperationsApi.tenants(),
      platformOperationsApi.systems(),
    ])
      .then(([dashboardData, tenantData, systemData]) => {
        if (cancelled) return;
        setDashboard(dashboardData);
        setTenants(tenantData);
        setSystems(systemData);
      })
      .catch(() => {
        if (cancelled) return;
        setError("تعذر تحميل مركز الإدارة. تحقق من صلاحية مستأجر التحكم وإعدادات الخادم.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [state]);

  const filteredTenants = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return tenants;
    return tenants.filter((tenant) =>
      [tenant.name, tenant.legalName ?? "", tenant.subdomain, tenant.status]
        .some((value) => value.toLowerCase().includes(normalized)),
    );
  }, [query, tenants]);

  if (state !== "AUTHENTICATED" || loading) return <AuthLoadingState />;

  return (
    <main className={styles.root}>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>SNAD CONTROL PLANE</p>
          <h1>مركز الإدارة العليا</h1>
          <p>المستأجرون والأنظمة والمؤشرات التشغيلية من مصدر واحد.</p>
        </div>
        <div className={styles.identity}>
          <span>{me?.displayName || me?.email}</span>
          <button type="button" onClick={() => router.push("/workspace")}>العودة لمساحة العمل</button>
        </div>
      </header>

      {error ? <section className={styles.error}>{error}</section> : null}

      {dashboard ? (
        <section className={styles.metrics} aria-label="المؤشرات التنفيذية">
          <article><span>إجمالي المستأجرين</span><strong>{number(dashboard.totalTenants)}</strong></article>
          <article><span>المستأجرون النشطون</span><strong>{number(dashboard.activeTenants)}</strong></article>
          <article><span>الفترات التجريبية</span><strong>{number(dashboard.trialTenants)}</strong></article>
          <article><span>المستأجرون الموقوفون</span><strong>{number(dashboard.suspendedTenants)}</strong></article>
          <article><span>إجمالي المستخدمين</span><strong>{number(dashboard.totalUsers)}</strong></article>
          <article><span>المستخدمون النشطون</span><strong>{number(dashboard.activeUsers)}</strong></article>
          <article><span>الخدمات العاملة</span><strong>{number(dashboard.operationalServices)}</strong></article>
          <article><span>خدمات تحتاج متابعة</span><strong>{number(dashboard.degradedServices)}</strong></article>
        </section>
      ) : null}

      <section className={styles.panel}>
        <div className={styles.panelHeading}>
          <div><p className={styles.eyebrow}>TENANTS</p><h2>إدارة المستأجرين</h2></div>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="ابحث بالاسم أو النطاق أو الحالة"
            aria-label="البحث في المستأجرين"
          />
        </div>
        <div className={styles.tableWrap}>
          <table>
            <thead><tr><th>المستأجر</th><th>النطاق</th><th>الحالة</th><th>العملة</th><th>آخر تحديث</th></tr></thead>
            <tbody>
              {filteredTenants.map((tenant) => (
                <tr key={tenant.id}>
                  <td><strong>{tenant.name}</strong><small>{tenant.legalName || tenant.billingEmail || "—"}</small></td>
                  <td dir="ltr">{tenant.subdomain}</td>
                  <td><span className={styles.status} data-status={tenant.status}>{statusLabel(tenant.status)}</span></td>
                  <td>{tenant.currencyCode}</td>
                  <td>{date(tenant.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className={styles.panel}>
        <div className={styles.panelHeading}>
          <div><p className={styles.eyebrow}>SYSTEMS</p><h2>حالة الأنظمة والخدمات</h2></div>
        </div>
        <div className={styles.systemGrid}>
          {systems.map((service) => (
            <article key={service.id}>
              <div><span className={styles.status} data-status={service.status}>{statusLabel(service.status)}</span><small>{service.criticality}</small></div>
              <h3>{service.name}</h3>
              <p>{service.code} · {service.environment}</p>
              <dl><dt>المالك</dt><dd>{service.ownerName || "—"}</dd><dt>آخر فحص</dt><dd>{date(service.lastCheckedAt)}</dd></dl>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.panel}>
        <div className={styles.panelHeading}>
          <div><p className={styles.eyebrow}>AUDIT</p><h2>آخر الأنشطة الإدارية</h2></div>
        </div>
        <div className={styles.auditList}>
          {(dashboard?.recentActivity ?? []).map((entry) => (
            <article key={entry.id}>
              <div><strong>{entry.action}</strong><span>{entry.resourceType}</span></div>
              <p>{entry.reason || "عملية إدارية مسجلة"}</p>
              <time>{date(entry.createdAt)}</time>
            </article>
          ))}
          {!dashboard?.recentActivity.length ? <p className={styles.empty}>لا توجد أنشطة إدارية مسجلة بعد.</p> : null}
        </div>
      </section>
    </main>
  );
}
