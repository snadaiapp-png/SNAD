"use client";

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  platformOperationsApi,
  type BillingInvoice,
  type ExecutiveDashboard,
  type ManagedMembership,
  type ManagedOrganization,
  type ManagedTenant,
  type SaasPlan,
  type SystemService,
  type TenantSubscription,
} from "@/lib/api/platform-operations";
import styles from "./control-plane.module.css";

type Tab = "tenants" | "directory" | "plans" | "subscriptions" | "billing" | "operations";
type Snapshot = {
  dashboard: ExecutiveDashboard;
  tenants: ManagedTenant[];
  plans: SaasPlan[];
  subscriptions: TenantSubscription[];
  invoices: BillingInvoice[];
  systems: SystemService[];
};

const tabs: Array<[Tab, string]> = [
  ["tenants", "المستأجرون"], ["directory", "الشركات والعضويات"],
  ["plans", "الباقات"], ["subscriptions", "الاشتراكات والترقية"],
  ["billing", "الفوترة"], ["operations", "الأنظمة والتدقيق"],
];
const ask = (text: string, initial = "") => window.prompt(text, initial)?.trim() ?? "";
const money = (minor: number, currency: string) =>
  new Intl.NumberFormat("ar-SA", { style: "currency", currency }).format(minor / 100);
const day = (text: string | null) =>
  text ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium" }).format(new Date(text)) : "—";

function Badge({ value }: { value: string }) {
  return <span className={styles.status} data-status={value}>{value}</span>;
}
function Buttons({ children }: { children: ReactNode }) {
  return <div className={styles.actions}>{children}</div>;
}

export function ControlPlaneConsole() {
  const { state, me } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>("tenants");
  const [data, setData] = useState<Snapshot | null>(null);
  const [organizations, setOrganizations] = useState<ManagedOrganization[]>([]);
  const [memberships, setMemberships] = useState<ManagedMembership[]>([]);
  const [tenantId, setTenantId] = useState("");
  const [organizationId, setOrganizationId] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const [dashboard, tenants, plans, subscriptions, invoices, systems] = await Promise.all([
      platformOperationsApi.dashboard(), platformOperationsApi.tenants(),
      platformOperationsApi.plans(), platformOperationsApi.subscriptions(),
      platformOperationsApi.invoices(), platformOperationsApi.systems(),
    ]);
    setData({ dashboard, tenants, plans, subscriptions, invoices, systems });
    setTenantId((current) => current || tenants[0]?.id || "");
  }, []);

  const reloadDirectory = useCallback(async () => {
    if (!tenantId) return;
    const orgs = await platformOperationsApi.organizations(tenantId);
    const selected = orgs.some((item) => item.id === organizationId)
      ? organizationId : orgs[0]?.id ?? "";
    const members = selected
      ? await platformOperationsApi.memberships(tenantId, selected) : [];
    setOrganizations(orgs);
    setOrganizationId(selected);
    setMemberships(members);
  }, [organizationId, tenantId]);

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    let cancelled = false;
    Promise.all([
      platformOperationsApi.dashboard(),
      platformOperationsApi.tenants(),
      platformOperationsApi.plans(),
      platformOperationsApi.subscriptions(),
      platformOperationsApi.invoices(),
      platformOperationsApi.systems(),
    ]).then(([dashboard, tenants, plans, subscriptions, invoices, systems]) => {
      if (cancelled) return;
      setData({ dashboard, tenants, plans, subscriptions, invoices, systems });
      setTenantId((current) => current || tenants[0]?.id || "");
    }).catch(() => {
      if (!cancelled) setMessage("تعذر تحميل مركز الإدارة.");
    });
    return () => { cancelled = true; };
  }, [state]);

  useEffect(() => {
    if (state !== "AUTHENTICATED" || !tenantId) return;
    let cancelled = false;
    platformOperationsApi.organizations(tenantId).then(async (orgs) => {
      const selected = orgs.some((item) => item.id === organizationId)
        ? organizationId : orgs[0]?.id ?? "";
      const members = selected
        ? await platformOperationsApi.memberships(tenantId, selected) : [];
      if (!cancelled) {
        setOrganizations(orgs); setOrganizationId(selected); setMemberships(members);
      }
    }).catch(() => { if (!cancelled) {
      setOrganizations([]); setOrganizationId(""); setMemberships([]);
    } });
    return () => { cancelled = true; };
  }, [organizationId, state, tenantId]);

  const execute = useCallback(async (success: string, task: () => Promise<unknown>) => {
    setBusy(true); setMessage(null);
    try { await task(); await reload(); await reloadDirectory(); setMessage(success); }
    catch (error) { setMessage(error instanceof Error ? error.message : "تعذر تنفيذ العملية."); }
    finally { setBusy(false); }
  }, [reload, reloadDirectory]);

  if (state !== "AUTHENTICATED" || !data) return <AuthLoadingState />;
  const tenant = data.tenants.find((item) => item.id === tenantId);
  const organization = organizations.find((item) => item.id === organizationId);

  const addTenant = () => {
    const name = ask("اسم المستأجر (بالعربية أو الإنجليزية)");
    const subdomain = ask("النطاق الفرعي (أحرف لاتينية صغيرة وأرقام فقط، مثال: my-company)").toLowerCase().replace(/[^a-z0-9-]/g, "");
    const adminEmail = ask("بريد المدير (example@sanad.com)");
    if (!name || !subdomain || !adminEmail) return;
    if (!/^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/.test(subdomain)) {
      setMessage("النطاق الفرعي غير صالح. استخدم أحرف لاتينية صغيرة وأرقام وواصلات فقط.");
      return;
    }
    void execute("تم إنشاء المستأجر.", () => platformOperationsApi.createTenant({
      name, subdomain, adminEmail,
      legalName: ask("الاسم القانوني", name),
      billingEmail: ask("بريد الفوترة", adminEmail),
      adminDisplayName: ask("اسم المدير", "مدير النظام") || "مدير النظام",
      countryCode: "SA", locale: "ar-SA", timezone: "Asia/Riyadh",
      currencyCode: "SAR", trialDays: Number(ask("أيام التجربة", "14") || 14),
    }));
  };

  const addPlan = () => {
    const code = ask("رمز الباقة");
    const name = ask("اسم الباقة");
    if (!code || !name) return;
    const features = ask("الخصائص", "CRM,WORKFLOW").split(",").map((item) => item.trim()).filter(Boolean);
    void execute("تم إنشاء الباقة.", () => platformOperationsApi.createPlan({
      code, name, description: ask("الوصف"), currencyCode: "SAR",
      monthlyPriceMinor: Number(ask("السعر الشهري", "99")) * 100,
      annualPriceMinor: Number(ask("السعر السنوي", "990")) * 100,
      trialDays: Number(ask("أيام التجربة", "14")),
      maxUsers: Number(ask("حد المستخدمين", "5")),
      maxOrganizations: Number(ask("حد الشركات", "1")),
      storageMb: Number(ask("التخزين MB", "5120")),
      entitlements: features.map((featureCode) => ({ featureCode, enabled: true, limitValue: null })),
    }));
  };

  return <main className={styles.root}>
    <header className={styles.header}>
      <div><p className={styles.eyebrow}>SNAD CONTROL PLANE</p><h1>مركز إدارة منصة سند</h1>
        <p>إدارة المستأجرين والشركات والعضويات والباقات والاشتراكات والفوترة.</p></div>
      <Buttons><span>{me?.displayName || me?.email}</span>
        <button type="button" onClick={() => router.push("/workspace")}>مساحة العمل</button></Buttons>
    </header>
    {message ? <section className={styles.notice}>{message}</section> : null}
    <section className={styles.metrics}>
      <article><span>المستأجرون</span><strong>{data.dashboard.totalTenants}</strong></article>
      <article><span>المستخدمون</span><strong>{data.dashboard.totalUsers}</strong></article>
      <article><span>الاشتراكات</span><strong>{data.subscriptions.length}</strong></article>
      <article><span>الفواتير المفتوحة</span><strong>{data.invoices.filter((item) => item.status === "OPEN").length}</strong></article>
    </section>
    <nav className={styles.tabs}>{tabs.map(([id, text]) =>
      <button type="button" key={id} data-active={tab === id} onClick={() => setTab(id)}>{text}</button>)}</nav>

    {tab === "tenants" ? <section className={styles.panel}>
      <div className={styles.panelHeading}><h2>المستأجرون</h2>
        <button type="button" disabled={busy} onClick={addTenant}>إضافة مستأجر</button></div>
      <div className={styles.cards}>{data.tenants.map((item) => <article key={item.id}>
        <Badge value={item.status} /><h3>{item.name}</h3><p dir="ltr">{item.subdomain}</p>
        <Buttons>
          <button type="button" onClick={() => { setTenantId(item.id); setTab("directory"); }}>الشركات</button>
          <button type="button" onClick={() => {
            const status = ask("الحالة", item.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE");
            const reason = ask("السبب", "إدارة المستأجر");
            if (status && reason) void execute("تم تحديث المستأجر.", () =>
              platformOperationsApi.changeTenantStatus(item.id, status.toUpperCase(), reason));
          }}>تغيير الحالة</button>
        </Buttons>
      </article>)}</div>
    </section> : null}

    {tab === "directory" ? <>
      <section className={styles.panel}>
        <div className={styles.panelHeading}><h2>شركات {tenant?.name || "المستأجر"}</h2><Buttons>
          <select value={tenantId} onChange={(event) => setTenantId(event.target.value)}>
            {data.tenants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
          <button type="button" onClick={() => {
            const name = ask("اسم الشركة");
            if (name) void execute("تم إنشاء الشركة.", () =>
              platformOperationsApi.createOrganization(tenantId, { name, description: ask("الوصف") }));
          }}>إضافة شركة</button>
        </Buttons></div>
        <div className={styles.cards}>
          {organizations.length === 0 ? (
            <p style={{ padding: "2rem", textAlign: "center", color: "var(--snad-color-text-muted)" }}>
              {`لا توجد شركات بعد. اضغط "إضافة شركة" لإنشاء أول شركة.`}
            </p>
          ) : organizations.map((item) => <article key={item.id}>
          <Badge value={item.status} /><h3>{item.name}</h3><p>{item.description || "بلا وصف"}</p>
          <Buttons>
            <button type="button" onClick={() => setOrganizationId(item.id)}>العضويات</button>
            <button type="button" onClick={() => {
              const name = ask("الاسم", item.name);
              if (name) void execute("تم تحديث الشركة.", () =>
                platformOperationsApi.updateOrganization(tenantId, item.id, {
                  name, description: ask("الوصف", item.description ?? ""),
                }));
            }}>تعديل</button>
            <button type="button" onClick={() => void execute("تم تحديث الحالة.", () =>
              platformOperationsApi.changeOrganizationStatus(
                tenantId, item.id, item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE", "إدارة الشركة",
              ))}>{item.status === "ACTIVE" ? "تعطيل" : "تنشيط"}</button>
          </Buttons>
        </article>)}</div>
      </section>
      <section className={styles.panel}>
        <div className={styles.panelHeading}><h2>عضويات {organization?.name || "الشركة"}</h2><Buttons>
          <select value={organizationId} onChange={(event) => setOrganizationId(event.target.value)}>
            {organizations.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
          <button type="button" onClick={() => {
            const email = ask("البريد الإلكتروني");
            if (email) void execute("تم إنشاء العضوية.", () =>
              platformOperationsApi.createMembership(tenantId, organizationId, {
                email, displayName: ask("الاسم"), roleCode: ask("الدور", "MEMBER") || "MEMBER",
              }));
          }}>دعوة عضو</button>
        </Buttons></div>
        <div className={styles.cards}>{memberships.map((item) => <article key={item.id}>
          <Badge value={item.status} /><h3>{item.displayName || item.email}</h3><p>{item.roleCode}</p>
          <button type="button" onClick={() => {
            const status = ask("الحالة", item.status);
            const roleCode = ask("الدور", item.roleCode);
            if (status && roleCode) void execute("تم تحديث العضوية.", () =>
              platformOperationsApi.updateMembership(tenantId, organizationId, item.id, {
                status: status.toUpperCase(), roleCode: roleCode.toUpperCase(),
                reason: ask("السبب", "إدارة العضوية") || "إدارة العضوية",
              }));
          }}>إدارة</button>
        </article>)}</div>
      </section>
    </> : null}

    {tab === "plans" ? <section className={styles.panel}>
      <div className={styles.panelHeading}><h2>الباقات والاستحقاقات</h2>
        <button type="button" onClick={addPlan}>إضافة باقة</button></div>
      <div className={styles.cards}>{data.plans.map((item) => <article key={item.id}>
        <Badge value={item.status} /><h3>{item.name}</h3>
        <p>{money(item.monthlyPriceMinor, item.currencyCode)} شهريًا</p>
        <small>{item.maxUsers} مستخدم · {item.maxOrganizations} شركة</small>
        <Buttons>
          <button type="button" onClick={() => {
            const name = ask("الاسم", item.name);
            if (name) void execute("تم تحديث الباقة.", () => platformOperationsApi.updatePlan(item.id, {
              name, description: ask("الوصف", item.description ?? ""), currencyCode: item.currencyCode,
              monthlyPriceMinor: Number(ask("السعر الشهري", String(item.monthlyPriceMinor / 100))) * 100,
              annualPriceMinor: Number(ask("السعر السنوي", String(item.annualPriceMinor / 100))) * 100,
              trialDays: item.trialDays, maxUsers: Number(ask("حد المستخدمين", String(item.maxUsers))),
              maxOrganizations: Number(ask("حد الشركات", String(item.maxOrganizations))),
              storageMb: item.storageMb,
              entitlements: item.entitlements.map(({ featureCode, enabled, limitValue }) =>
                ({ featureCode, enabled, limitValue })),
            }));
          }}>تعديل</button>
          <button type="button" onClick={() => void execute("تم تحديث حالة الباقة.", () =>
            platformOperationsApi.changePlanStatus(
              item.id, item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE", "إدارة الباقة",
            ))}>{item.status === "ACTIVE" ? "تعطيل" : "تنشيط"}</button>
        </Buttons>
      </article>)}</div>
    </section> : null}

    {tab === "subscriptions" ? <section className={styles.panel}>
      <div className={styles.panelHeading}><h2>الاشتراكات والترقية</h2>
        <button type="button" onClick={() => {
          const selectedTenant = data.tenants.find((item) => item.id === (ask("معرف المستأجر", tenantId) || tenantId));
          const code = ask("رمز الباقة", data.plans[0]?.code ?? "");
          const selectedPlan = data.plans.find((item) => item.code.toLowerCase() === code.toLowerCase());
          if (selectedTenant && selectedPlan) void execute("تم إنشاء الاشتراك.", () =>
            platformOperationsApi.createSubscription({
              tenantId: selectedTenant.id, planId: selectedPlan.id,
              billingCycle: ask("MONTHLY أو ANNUAL", "MONTHLY").toUpperCase(),
              seatQuantity: Number(ask("المقاعد", "1")), trialDays: selectedPlan.trialDays,
            }));
        }}>إنشاء اشتراك</button></div>
      <div className={styles.cards}>{data.subscriptions.map((item) => <article key={item.id}>
        <Badge value={item.status} /><h3>{item.tenantName}</h3>
        <p>{item.planName} · {item.seatQuantity} مقعد</p><small>{day(item.currentPeriodEnd)}</small>
        <Buttons>
          <button type="button" onClick={() => {
            const code = ask("رمز الباقة", item.planCode);
            const target = data.plans.find((plan) => plan.code.toLowerCase() === code.toLowerCase());
            if (target) void execute("تم تغيير الباقة.", () =>
              platformOperationsApi.changeSubscriptionPlan(item.id, {
                planId: target.id, billingCycle: ask("الدورة", item.billingCycle).toUpperCase(),
                effectiveMode: ask("IMMEDIATE أو NEXT_CYCLE", "IMMEDIATE").toUpperCase(),
                reason: ask("السبب", "تغيير الباقة") || "تغيير الباقة",
              }));
          }}>تغيير الباقة</button>
          <button type="button" onClick={() => {
            const seats = Number(ask("المقاعد", String(item.seatQuantity)));
            if (seats) void execute("تم تعديل المقاعد.", () =>
              platformOperationsApi.changeSubscriptionSeats(item.id, seats, "تعديل المقاعد"));
          }}>المقاعد</button>
          <button type="button" onClick={() => void execute("تم تحديث الاشتراك.", () =>
            item.status === "CANCELLED"
              ? platformOperationsApi.resumeSubscription(item.id)
              : platformOperationsApi.cancelSubscription(item.id, false, "إلغاء بنهاية الدورة"),
          )}>{item.status === "CANCELLED" ? "استئناف" : "إلغاء"}</button>
          <button type="button" onClick={() => void execute("تم التجديد.", () =>
            platformOperationsApi.renewSubscription(item.id))}>تجديد</button>
        </Buttons>
      </article>)}</div>
    </section> : null}

    {tab === "billing" ? <section className={styles.panel}>
      <div className={styles.panelHeading}><h2>الفواتير</h2></div>
      <div className={styles.cards}>{data.invoices.map((item) => <article key={item.id}>
        <Badge value={item.status} /><h3>{item.invoiceNumber}</h3>
        <p>{item.tenantName} · {money(item.totalMinor, item.currencyCode)}</p><small>{day(item.dueAt)}</small>
        {item.status !== "PAID" ? <button type="button" onClick={() => {
          const reference = ask("مرجع الدفع");
          if (reference) void execute("تم تسجيل السداد.", () =>
            platformOperationsApi.markInvoicePaid(item.id, reference, "تأكيد دفع يدوي"));
        }}>تسجيل السداد</button> : null}
      </article>)}</div>
    </section> : null}

    {tab === "operations" ? <>
      <section className={styles.panel}><div className={styles.systemGrid}>{data.systems.map((item) =>
        <article key={item.id}><div><Badge value={item.status} /><small>{item.criticality}</small></div>
          <h3>{item.name}</h3><p>{item.code} · {item.environment}</p></article>)}</div></section>
      <section className={styles.panel}><div className={styles.auditList}>{data.dashboard.recentActivity.map((item) =>
        <article key={item.id}><div><strong>{item.action}</strong><span>{item.resourceType}</span></div>
          <p>{item.reason || "عملية إدارية"}</p><time>{day(item.createdAt)}</time></article>)}</div></section>
    </> : null}
  </main>;
}