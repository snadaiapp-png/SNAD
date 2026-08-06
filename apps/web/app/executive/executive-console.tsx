"use client";

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { SnadLogo } from "@/components/sds";
import {
  executiveApi,
  type BillingInvoice,
  type ExecutiveDashboard,
  type ManagedMembership,
  type ManagedOrganization,
  type ManagedTenant,
  type SaasPlan,
  type TenantSubscription,
} from "@/lib/api/executive-api";
import styles from "./executive.module.css";

type Tab = "tenants" | "directory" | "plans" | "subscriptions" | "billing";
type Snapshot = {
  dashboard: ExecutiveDashboard;
  tenants: ManagedTenant[];
  plans: SaasPlan[];
  subscriptions: TenantSubscription[];
  invoices: BillingInvoice[];
};

const tabs: Array<[Tab, string]> = [
  ["tenants", "المستأجرون"], ["directory", "الشركات والعضويات"],
  ["plans", "الباقات"], ["subscriptions", "الاشتراكات والترقية"],
  ["billing", "الفوترة"],
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

export function ExecutiveConsole() {
  const { state } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>("tenants");
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const [dashboard, tenants, plans, subscriptions, invoices] = await Promise.all([
        executiveApi.dashboard(),
        executiveApi.tenants(),
        executiveApi.plans(),
        executiveApi.subscriptions(),
        executiveApi.invoices(),
      ]);
      setSnapshot({ dashboard, tenants, plans, subscriptions, invoices });
    } catch (reason) {
      setError(String(reason));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (state === "AUTHENTICATED") void refresh();
  }, [state, refresh]);

  if (state !== "AUTHENTICATED") return <AuthLoadingState phase="session" />;

  return (
    <div className={styles.shell}>
      <nav className={styles.tabs} aria-label="Executive Management">
        {tabs.map(([id, label]) => (
          <button
            key={id}
            className={tab === id ? styles.tabActive : styles.tab}
            onClick={() => setTab(id)}
            aria-current={tab === id ? "page" : undefined}
          >
            {label}
          </button>
        ))}
      </nav>

      {error ? <div className={styles.error}>{error}</div> : null}
      {notice ? <div className={styles.notice}>{notice}</div> : null}
      {busy && !snapshot ? <div className={styles.loading}>جارٍ التحميل…</div> : null}

      {snapshot ? (
        <div className={styles.content}>
          {tab === "tenants" ? (
            <TenantsTab snapshot={snapshot} busy={busy} />
          ) : tab === "directory" ? (
            <DirectoryTab snapshot={snapshot} busy={busy} />
          ) : tab === "plans" ? (
            <PlansTab snapshot={snapshot} busy={busy} />
          ) : tab === "subscriptions" ? (
            <SubscriptionsTab snapshot={snapshot} busy={busy} />
          ) : tab === "billing" ? (
            <BillingTab snapshot={snapshot} busy={busy} />
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function TenantsTab({ snapshot, busy }: { snapshot: Snapshot; busy: boolean }) {
  return (
    <section aria-label="Tenants">
      <div className={styles.summaryGrid}>
        <article><span>إجمالي المستأجرين</span><strong>{snapshot.tenants.length}</strong></article>
        <article><span>نشط</span><strong>{snapshot.tenants.filter(t => t.status === "ACTIVE").length}</strong></article>
        <article><span>تجريبي</span><strong>{snapshot.tenants.filter(t => t.status === "TRIAL").length}</strong></article>
        <article><span>متوقف</span><strong>{snapshot.tenants.filter(t => t.status === "SUSPENDED").length}</strong></article>
      </div>
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>الرمز</th><th>الاسم</th><th>الحالة</th><th>تاريخ الإنشاء</th></tr></thead>
          <tbody>
            {snapshot.tenants.map(t => (
              <tr key={t.id}><td>{t.name}</td><td>{t.name}</td><td><Badge value={t.status} /></td><td>{day(t.createdAt)}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function DirectoryTab({ snapshot, busy }: { snapshot: Snapshot; busy: boolean }) {
  const [orgs, setOrgs] = useState<ManagedOrganization[]>([]);
  const [members, setMembers] = useState<ManagedMembership[]>([]);
  useEffect(() => {
    Promise.all([executiveApi.organizations(snapshot?.tenants[0]?.id ?? ""), executiveApi.memberships(snapshot?.tenants[0]?.id ?? "", "")])
      .then(([o, m]) => { setOrgs(o); setMembers(m); })
      .catch(() => {});
  }, []);
  return (
    <section aria-label="Directory">
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>الاسم</th><th>النطاق</th><th>الأعضاء</th></tr></thead>
          <tbody>
            {orgs.map(o => (
              <tr key={o.id}><td>{o.name}</td><td>{o.description ?? "—"}</td><td>{members.filter(m => m.organizationId === o.id).length}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function PlansTab({ snapshot, busy }: { snapshot: Snapshot; busy: boolean }) {
  return (
    <section aria-label="Plans">
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>الرمز</th><th>الاسم</th><th>السعر</th><th>الحالة</th></tr></thead>
          <tbody>
            {snapshot.plans.map(p => (
              <tr key={p.id}><td>{p.code}</td><td>{p.name}</td><td>{money(p.monthlyPriceMinor, p.currencyCode)}</td><td><Badge value={p.status} /></td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SubscriptionsTab({ snapshot, busy }: { snapshot: Snapshot; busy: boolean }) {
  return (
    <section aria-label="Subscriptions">
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>المستأجر</th><th>الباقة</th><th>الحالة</th><th>تاريخ التجديد</th></tr></thead>
          <tbody>
            {snapshot.subscriptions.map(s => (
              <tr key={s.id}><td>{s.tenantId}</td><td>{s.planId}</td><td><Badge value={s.status} /></td><td>{day(s.currentPeriodEnd)}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function BillingTab({ snapshot, busy }: { snapshot: Snapshot; busy: boolean }) {
  return (
    <section aria-label="Billing">
      <div className={styles.tableWrap}>
        <table>
          <thead><tr><th>الرقم</th><th>المستأجر</th><th>المبلغ</th><th>الحالة</th><th>التاريخ</th></tr></thead>
          <tbody>
            {snapshot.invoices.map(i => (
              <tr key={i.id}><td>{i.invoiceNumber}</td><td>{i.tenantId}</td><td>{money(i.totalMinor, i.currencyCode)}</td><td><Badge value={i.status} /></td><td>{day(i.createdAt)}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
