"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { platformOperationsApi, type BillingInvoice, type ExecutiveDashboard, type ManagedMembership, type ManagedOrganization, type ManagedTenant, type SaasPlan, type SystemService, type TenantSubscription } from "@/lib/api/platform-operations";
import styles from "./control-plane.module.css";

type Tab = "tenants" | "directory" | "plans" | "subscriptions" | "billing" | "operations";
const tabs: Array<[Tab, string]> = [["tenants", "المستأجرون"], ["directory", "الشركات والعضويات"], ["plans", "الباقات"], ["subscriptions", "الاشتراكات والترقية"], ["billing", "الفوترة"], ["operations", "الأنظمة والتدقيق"]];

const ask = (message: string, initial = "") => window.prompt(message, initial)?.trim() ?? "";
const date = (value: string | null) => value ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
const money = (minor: number, currency: string) => new Intl.NumberFormat("ar-SA", { style: "currency", currency }).format(minor / 100);
const label = (status: string) => ({ ACTIVE: "نشط", INACTIVE: "غير نشط", TRIAL: "تجريبي", TRIALING: "فترة تجريبية", SUSPENDED: "موقوف", PAST_DUE: "متأخر", CANCELLED: "ملغى", ARCHIVED: "مؤرشف", INVITED: "مدعو", REMOVED: "محذوف", OPEN: "مفتوحة", PAID: "مدفوعة", DRAFT: "مسودة", VOID: "ملغاة", OPERATIONAL: "يعمل", DEGRADED: "متدهور", MAINTENANCE: "صيانة", DISABLED: "معطل", INCIDENT: "حادث" }[status] ?? status);

export default function ControlPlanePage() {
  const { state, me } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>("tenants");
  const [dashboard, setDashboard] = useState<ExecutiveDashboard | null>(null);
  const [tenants, setTenants] = useState<ManagedTenant[]>([]);
  const [plans, setPlans] = useState<SaasPlan[]>([]);
  const [subscriptions, setSubscriptions] = useState<TenantSubscription[]>([]);
  const [invoices, setInvoices] = useState<BillingInvoice[]>([]);
  const [systems, setSystems] = useState<SystemService[]>([]);
  const [organizations, setOrganizations] = useState<ManagedOrganization[]>([]);
  const [memberships, setMemberships] = useState<ManagedMembership[]>([]);
  const [tenantId, setTenantId] = useState("");
  const [organizationId, setOrganizationId] = useState("");
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => { if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) router.replace("/"); }, [router, state]);

  const loadAll = useCallback(async () => {
    const [d, t, p, s, i, y] = await Promise.all([platformOperationsApi.dashboard(), platformOperationsApi.tenants(), platformOperationsApi.plans(), platformOperationsApi.subscriptions(), platformOperationsApi.invoices(), platformOperationsApi.systems()]);
    setDashboard(d); setTenants(t); setPlans(p); setSubscriptions(s); setInvoices(i); setSystems(y);
    setTenantId((current) => current || t[0]?.id || "");
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    let cancelled = false; setLoading(true);
    loadAll().catch(() => { if (!cancelled) setError("تعذر تحميل مركز الإدارة."); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [loadAll, state]);

  const loadDirectory = useCallback(async () => {
    if (!tenantId) { setOrganizations([]); setMemberships([]); return; }
    const orgs = await platformOperationsApi.organizations(tenantId); setOrganizations(orgs);
    const selected = orgs.some((item) => item.id === organizationId) ? organizationId : orgs[0]?.id || "";
    setOrganizationId(selected);
    setMemberships(selected ? await platformOperationsApi.memberships(tenantId, selected) : []);
  }, [organizationId, tenantId]);

  useEffect(() => { if (state === "AUTHENTICATED") void loadDirectory(); }, [loadDirectory, state]);

  const run = useCallback(async (message: string, operation: () => Promise<unknown>) => {
    setBusy(true); setError(null); setNotice(null);
    try { await operation(); await loadAll(); await loadDirectory(); setNotice(message); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "تعذر تنفيذ العملية."); }
    finally { setBusy(false); }
  }, [loadAll, loadDirectory]);

  const filteredTenants = useMemo(() => {
    const q = query.toLowerCase().trim();
    return q ? tenants.filter((t) => [t.name, t.subdomain, t.status, t.billingEmail ?? ""].some((v) => v.toLowerCase().includes(q))) : tenants;
  }, [query, tenants]);

  async function createTenant() {
    const name = ask("اسم المستأجر"); const subdomain = ask("النطاق الفرعي"); const adminEmail = ask("بريد المدير");
    if (!name || !subdomain || !adminEmail) return;
    await run("تم إنشاء المستأجر والمدير والشركة الافتراضية.", async () => {
      const created = await platformOperationsApi.createTenant({ name, legalName: ask("الاسم القانوني", name), subdomain, billingEmail: ask("بريد الفوترة", adminEmail), adminEmail, adminDisplayName: ask("اسم المدير", "مدير النظام") || "مدير النظام", countryCode: "SA", locale: "ar-SA", timezone: "Asia/Riyadh", currencyCode: "SAR", trialDays: Number(ask("أيام التجربة", "14") || 14) });
      setTenantId(created.id);
    });
  }

  async function changeTenantStatus(tenant: ManagedTenant) {
    const status = ask("الحالة الجديدة", tenant.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE").toUpperCase(); const reason = ask("السبب", "إدارة المستأجر");
    if (status && reason) await run("تم تحديث حالة المستأجر.", () => platformOperationsApi.changeTenantStatus(tenant.id, status, reason));
  }

  async function createPlan() {
    const code = ask("رمز الباقة"); const name = ask("اسم الباقة"); if (!code || !name) return;
    const features = ask("الخصائص مفصولة بفاصلة", "CRM,WORKFLOW").split(",").map((v) => v.trim()).filter(Boolean);
    await run("تم إنشاء الباقة والاستحقاقات.", () => platformOperationsApi.createPlan({ code, name, description: ask("الوصف"), currencyCode: "SAR", monthlyPriceMinor: Number(ask("السعر الشهري", "99")) * 100, annualPriceMinor: Number(ask("السعر السنوي", "990")) * 100, trialDays: Number(ask("أيام التجربة", "14")), maxUsers: Number(ask("حد المستخدمين", "5")), maxOrganizations: Number(ask("حد الشركات", "1")), storageMb: Number(ask("التخزين بالميجابايت", "5120")), entitlements: features.map((featureCode) => ({ featureCode, enabled: true, limitValue: null })) }));
  }

  async function editPlan(plan: SaasPlan) {
    const name = ask("اسم الباقة", plan.name); if (!name) return;
    await run("تم تحديث الباقة.", () => platformOperationsApi.updatePlan(plan.id, { name, description: ask("الوصف", plan.description ?? ""), currencyCode: plan.currencyCode, monthlyPriceMinor: Number(ask("السعر الشهري", String(plan.monthlyPriceMinor / 100))) * 100, annualPriceMinor: Number(ask("السعر السنوي", String(plan.annualPriceMinor / 100))) * 100, trialDays: Number(ask("أيام التجربة", String(plan.trialDays))), maxUsers: Number(ask("حد المستخدمين", String(plan.maxUsers))), maxOrganizations: Number(ask("حد الشركات", String(plan.maxOrganizations))), storageMb: Number(ask("التخزين MB", String(plan.storageMb))), entitlements: plan.entitlements.map(({ featureCode, enabled, limitValue }) => ({ featureCode, enabled, limitValue })) }));
  }

  async function createSubscription() {
    const tenant = tenants.find((t) => t.id === (ask("معرف المستأجر", tenantId) || tenantId)); const planCode = ask("رمز الباقة", plans[0]?.code ?? ""); const plan = plans.find((p) => p.code.toLowerCase() === planCode.toLowerCase());
    if (!tenant || !plan) { setError("المستأجر أو الباقة غير موجودة."); return; }
    await run("تم إنشاء الاشتراك وتطبيق حدود الباقة.", () => platformOperationsApi.createSubscription({ tenantId: tenant.id, planId: plan.id, billingCycle: ask("دورة الفوترة MONTHLY/ANNUAL", "MONTHLY").toUpperCase(), seatQuantity: Number(ask("عدد المقاعد", "1")), trialDays: Number(ask("أيام التجربة", String(plan.trialDays))) }));
  }

  async function changePlan(subscription: TenantSubscription) {
    const code = ask("رمز الباقة الجديدة", subscription.planCode); const plan = plans.find((p) => p.code.toLowerCase() === code.toLowerCase()); if (!plan) return;
    await run("تم تنفيذ أو جدولة تغيير الباقة.", () => platformOperationsApi.changeSubscriptionPlan(subscription.id, { planId: plan.id, billingCycle: ask("الدورة MONTHLY/ANNUAL", subscription.billingCycle).toUpperCase(), effectiveMode: ask("التطبيق IMMEDIATE/NEXT_CYCLE", "IMMEDIATE").toUpperCase(), reason: ask("السبب", "تغيير الباقة") || "تغيير الباقة" }));
  }

  async function createOrganization() {
    if (!tenantId) return; const name = ask("اسم الشركة"); if (!name) return;
    await run("تم إنشاء الشركة ضمن حدود الباقة.", async () => { const created = await platformOperationsApi.createOrganization(tenantId, { name, description: ask("الوصف") }); setOrganizationId(created.id); });
  }

  async function createMembership() {
    if (!tenantId || !organizationId) return; const email = ask("البريد الإلكتروني"); if (!email) return;
    await run("تم إنشاء العضوية وربط الدور عند توفر المستخدم.", () => platformOperationsApi.createMembership(tenantId, organizationId, { email, displayName: ask("الاسم"), roleCode: ask("رمز الدور", "MEMBER") || "MEMBER" }));
  }

  if (state !== "AUTHENTICATED" || loading) return <AuthLoadingState />;
  const selectedTenant = tenants.find((t) => t.id === tenantId);
  const selectedOrganization = organizations.find((o) => o.id === organizationId);

  return <main className={styles.root}>
    <header className={styles.header}><div><p className={styles.eyebrow}>SNAD CONTROL PLANE</p><h1>مركز إدارة منصة سند</h1><p>المستأجرون والشركات والعضويات والباقات والاشتراكات والفوترة.</p></div><div className={styles.identity}><span>{me?.displayName || me?.email}</span><button onClick={() => router.push("/workspace")}>مساحة العمل</button></div></header>
    {error ? <section className={styles.error}>{error}</section> : null}{notice ? <section className={styles.notice}>{notice}</section> : null}
    {dashboard ? <section className={styles.metrics}><article><span>المستأجرون</span><strong>{dashboard.totalTenants}</strong></article><article><span>المستخدمون</span><strong>{dashboard.totalUsers}</strong></article><article><span>الاشتراكات</span><strong>{subscriptions.length}</strong></article><article><span>الفواتير المفتوحة</span><strong>{invoices.filter((i) => i.status === "OPEN").length}</strong></article></section> : null}
    <nav className={styles.tabs}>{tabs.map(([id, text]) => <button key={id} data-active={tab === id} onClick={() => setTab(id)}>{text}</button>)}</nav>

    {tab === "tenants" ? <section className={styles.panel}><div className={styles.panelHeading}><div><p className={styles.eyebrow}>TENANTS</p><h2>إدارة المستأجرين</h2></div><div className={styles.actions}><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="بحث" /><button disabled={busy} onClick={createTenant}>إضافة مستأجر</button></div></div><div className={styles.tableWrap}><table><thead><tr><th>المستأجر</th><th>النطاق</th><th>الحالة</th><th>العملة</th><th>الإدارة</th></tr></thead><tbody>{filteredTenants.map((tenant) => <tr key={tenant.id}><td><strong>{tenant.name}</strong><small>{tenant.billingEmail || tenant.legalName || "—"}</small></td><td dir="ltr">{tenant.subdomain}</td><td><span className={styles.status} data-status={tenant.status}>{label(tenant.status)}</span></td><td>{tenant.currencyCode}</td><td className={styles.actions}><button onClick={() => { setTenantId(tenant.id); setTab("directory"); }}>الشركات</button><button onClick={() => changeTenantStatus(tenant)}>تغيير الحالة</button></td></tr>)}</tbody></table></div></section> : null}

    {tab === "directory" ? <><section className={styles.panel}><div className={styles.panelHeading}><div><p className={styles.eyebrow}>ORGANIZATIONS</p><h2>شركات {selectedTenant?.name || "المستأجر"}</h2></div><div className={styles.actions}><select value={tenantId} onChange={(e) => setTenantId(e.target.value)}>{tenants.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</select><button onClick={createOrganization}>إضافة شركة</button></div></div><div className={styles.cards}>{organizations.map((org) => <article key={org.id}><span className={styles.status} data-status={org.status}>{label(org.status)}</span><h3>{org.name}</h3><p>{org.description || "بلا وصف"}</p><div className={styles.actions}><button onClick={() => setOrganizationId(org.id)}>اختيار</button><button onClick={() => { const name = ask("الاسم", org.name); if (name) void run("تم تحديث الشركة.", () => platformOperationsApi.updateOrganization(tenantId, org.id, { name, description: ask("الوصف", org.description ?? "") })); }}>تعديل</button><button onClick={() => run("تم تحديث حالة الشركة.", () => platformOperationsApi.changeOrganizationStatus(tenantId, org.id, org.status === "ACTIVE" ? "INACTIVE" : "ACTIVE", "إدارة الشركة"))}>{org.status === "ACTIVE" ? "تعطيل" : "تنشيط"}</button></div></article>)}</div></section><section className={styles.panel}><div className={styles.panelHeading}><div><p className={styles.eyebrow}>MEMBERSHIPS</p><h2>عضويات {selectedOrganization?.name || "الشركة"}</h2></div><div className={styles.actions}><select value={organizationId} onChange={(e) => setOrganizationId(e.target.value)}>{organizations.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}</select><button onClick={createMembership}>دعوة عضو</button></div></div><div className={styles.tableWrap}><table><thead><tr><th>العضو</th><th>الدور</th><th>الحالة</th><th>الإدارة</th></tr></thead><tbody>{memberships.map((m) => <tr key={m.id}><td><strong>{m.displayName || m.email}</strong><small>{m.email}</small></td><td>{m.roleCode}</td><td><span className={styles.status} data-status={m.status}>{label(m.status)}</span></td><td><button onClick={() => { const status = ask("الحالة", m.status); const roleCode = ask("الدور", m.roleCode); if (status && roleCode) void run("تم تحديث العضوية.", () => platformOperationsApi.updateMembership(tenantId, organizationId, m.id, { status: status.toUpperCase(), roleCode: roleCode.toUpperCase(), reason: ask("السبب", "إدارة العضوية") || "إدارة العضوية" })); }}>إدارة</button></td></tr>)}</tbody></table></div></section></> : null}

    {tab === "plans" ? <section className={styles.panel}><div className={styles.panelHeading}><div><p className={styles.eyebrow}>PLANS</p><h2>الباقات والاستحقاقات</h2></div><button onClick={createPlan}>إضافة باقة</button></div><div className={styles.cards}>{plans.map((plan) => <article key={plan.id}><span className={styles.status} data-status={plan.status}>{label(plan.status)}</span><h3>{plan.name}</h3><p>{plan.code} · {money(plan.monthlyPriceMinor, plan.currencyCode)} شهريًا</p><small>{plan.maxUsers} مستخدم · {plan.maxOrganizations} شركة · {plan.entitlements.map((e) => e.featureCode).join("، ")}</small><div className={styles.actions}><button onClick={() => editPlan(plan)}>تعديل</button><button onClick={() => run("تم تحديث حالة الباقة.", () => platformOperationsApi.changePlanStatus(plan.id, plan.status === "ACTIVE" ? "INACTIVE" : "ACTIVE", "إدارة الباقة"))}>{plan.status === "ACTIVE" ? "تعطيل" : "تنشيط"}</button></div></article>)}</div></section> : null}

    {tab === "subscriptions" ? <section className={styles.panel}><div className={styles.panelHeading}><div><p className={styles.eyebrow}>SUBSCRIPTIONS</p><h2>الاشتراكات والترقية</h2></div><button onClick={createSubscription}>إنشاء اشتراك</button></div><div className={styles.tableWrap}><table><thead><tr><th>المشترك</th><th>الباقة</th><th>الحالة</th><th>المقاعد</th><th>الدورة</th><th>الإدارة</th></tr></thead><tbody>{subscriptions.map((s) => <tr key={s.id}><td><strong>{s.tenantName}</strong><small>{s.currencyCode}</small></td><td>{s.planName}{s.pendingPlanCode ? <small>القادمة: {s.pendingPlanCode}</small> : null}</td><td><span className={styles.status} data-status={s.status}>{label(s.status)}</span></td><td>{s.seatQuantity}</td><td>{date(s.currentPeriodEnd)}</td><td className={styles.actions}><button onClick={() => changePlan(s)}>تغيير الباقة</button><button onClick={() => { const seats = Number(ask("المقاعد", String(s.seatQuantity))); if (seats) void run("تم تعديل المقاعد.", () => platformOperationsApi.changeSubscriptionSeats(s.id, seats, "تعديل المقاعد")); }}>المقاعد</button><button onClick={() => run("تم تحديث الاشتراك.", () => s.status === "CANCELLED" ? platformOperationsApi.resumeSubscription(s.id) : platformOperationsApi.cancelSubscription(s.id, false, "إلغاء بنهاية الدورة"))}>{s.status === "CANCELLED" ? "استئناف" : "إلغاء"}</button><button onClick={() => run("تم التجديد وإصدار الفاتورة.", () => platformOperationsApi.renewSubscription(s.id))}>تجديد</button></td></tr>)}</tbody></table></div></section> : null}

    {tab === "billing" ? <section className={styles.panel}><div className={styles.panelHeading}><div><p className={styles.eyebrow}>BILLING</p><h2>الفواتير</h2></div></div><div className={styles.tableWrap}><table><thead><tr><th>الفاتورة</th><th>المستأجر</th><th>الحالة</th><th>الإجمالي</th><th>الرصيد</th><th>الاستحقاق</th><th>الإدارة</th></tr></thead><tbody>{invoices.map((i) => <tr key={i.id}><td><strong>{i.invoiceNumber}</strong><small>{i.description || "—"}</small></td><td>{i.tenantName}</td><td><span className={styles.status} data-status={i.status}>{label(i.status)}</span></td><td>{money(i.totalMinor, i.currencyCode)}</td><td>{money(i.creditAppliedMinor, i.currencyCode)}</td><td>{date(i.dueAt)}</td><td>{i.status !== "PAID" ? <button onClick={() => { const ref = ask("مرجع الدفع"); if (ref) void run("تم تسجيل السداد.", () => platformOperationsApi.markInvoicePaid(i.id, ref, "تأكيد دفع يدوي")); }}>تسجيل السداد</button> : "—"}</td></tr>)}</tbody></table></div></section> : null}

    {tab === "operations" ? <><section className={styles.panel}><div className={styles.systemGrid}>{systems.map((s) => <article key={s.id}><div><span className={styles.status} data-status={s.status}>{label(s.status)}</span><small>{s.criticality}</small></div><h3>{s.name}</h3><p>{s.code} · {s.environment}</p><dl><dt>المالك</dt><dd>{s.ownerName || "—"}</dd><dt>آخر فحص</dt><dd>{date(s.lastCheckedAt)}</dd></dl></article>)}</div></section><section className={styles.panel}><div className={styles.auditList}>{dashboard?.recentActivity.map((a) => <article key={a.id}><div><strong>{a.action}</strong><span>{a.resourceType}</span></div><p>{a.reason || "عملية إدارية"}</p><time>{date(a.createdAt)}</time></article>)}</div></section></> : null}
  </main>;
}