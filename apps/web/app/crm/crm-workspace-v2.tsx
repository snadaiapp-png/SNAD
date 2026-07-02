"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  crmApi,
  type CrmAccount,
  type CrmActivity,
  type CrmContact,
  type CrmDashboard,
  type CrmLead,
  type CrmOpportunity,
  type CrmPipeline,
  type CrmStage,
  type Customer360,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import styles from "./crm.module.css";

type Tab = "accounts" | "contacts" | "leads" | "sales" | "activities";

function formValue(form: FormData, key: string): string {
  return String(form.get(key) ?? "").trim();
}

function optionalValue(form: FormData, key: string): string | undefined {
  return formValue(form, key) || undefined;
}

function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat("ar-SA", { maximumFractionDigits: 2 }).format(value ?? 0);
}

function formatDate(value: string | null | undefined): string {
  return value
    ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium" }).format(new Date(value))
    : "—";
}

export function CrmWorkspaceV2() {
  const router = useRouter();
  const { state, me } = useAuth();
  const [tab, setTab] = useState<Tab>("accounts");
  const [dashboard, setDashboard] = useState<CrmDashboard | null>(null);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [contacts, setContacts] = useState<CrmContact[]>([]);
  const [leads, setLeads] = useState<CrmLead[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<Record<string, CrmStage[]>>({});
  const [opportunities, setOpportunities] = useState<CrmOpportunity[]>([]);
  const [activities, setActivities] = useState<CrmActivity[]>([]);
  const [customer, setCustomer] = useState<Customer360 | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  const reload = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const [
        nextDashboard,
        nextAccounts,
        nextContacts,
        nextLeads,
        nextPipelines,
        nextOpportunities,
        nextActivities,
      ] = await Promise.all([
        crmApi.dashboard(),
        crmApi.accounts(),
        crmApi.contacts(),
        crmApi.leads(),
        crmApi.pipelines(),
        crmApi.opportunities(),
        crmApi.activities(),
      ]);
      setDashboard(nextDashboard);
      setAccounts(nextAccounts);
      setContacts(nextContacts);
      setLeads(nextLeads);
      setPipelines(nextPipelines);
      setOpportunities(nextOpportunities);
      setActivities(nextActivities);
      const stageEntries = await Promise.all(
        nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
      );
      setStages(Object.fromEntries(stageEntries));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => {
      void reload();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [reload, state]);

  const accountNames = useMemo(
    () => new Map(accounts.map((account) => [account.id, account.display_name])),
    [accounts],
  );

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function openCustomer(accountId: string) {
    setBusy(true);
    setError("");
    try {
      setCustomer(await crmApi.customer360(accountId));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function createAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () => crmApi.createAccount({
        displayName: formValue(form, "displayName"),
        accountType: formValue(form, "accountType") || "BUSINESS",
        primaryCurrencyCode: formValue(form, "currency") || "SAR",
        preferredLocale: "ar-SA",
        timeZone: "Asia/Riyadh",
        source: "CRM_WEB",
      }),
      "تم إنشاء الحساب.",
    );
    formElement.reset();
  }

  async function createContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () => crmApi.createContact({
        accountId: optionalValue(form, "accountId"),
        givenName: formValue(form, "givenName"),
        familyName: optionalValue(form, "familyName"),
        preferredLocale: "ar-SA",
        timeZone: "Asia/Riyadh",
        consentSummary: "UNKNOWN",
      }),
      "تم إنشاء جهة الاتصال.",
    );
    formElement.reset();
  }

  async function createLead(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () => crmApi.createLead({
        displayName: formValue(form, "displayName"),
        companyName: optionalValue(form, "companyName"),
        source: "CRM_WEB",
      }),
      "تم إنشاء العميل المحتمل.",
    );
    formElement.reset();
  }

  async function createPipeline(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () => crmApi.createPipeline({
        name: formValue(form, "name"),
        currencyCode: formValue(form, "currency") || "SAR",
        stages: formValue(form, "stages").split(",").map((item) => item.trim()).filter(Boolean),
      }),
      "تم إنشاء قناة المبيعات.",
    );
    formElement.reset();
  }

  async function createOpportunity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const amount = optionalValue(form, "amount");
    await mutate(
      () => crmApi.createOpportunity({
        accountId: formValue(form, "accountId"),
        pipelineId: formValue(form, "pipelineId"),
        stageId: formValue(form, "stageId"),
        name: formValue(form, "name"),
        amount: amount ? Number(amount) : undefined,
        currencyCode: formValue(form, "currency") || "SAR",
        expectedCloseDate: optionalValue(form, "expectedCloseDate"),
      }),
      "تم إنشاء الفرصة.",
    );
    formElement.reset();
  }

  async function createActivity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const relatedId = optionalValue(form, "relatedId");
    await mutate(
      () => crmApi.createActivity({
        activityType: formValue(form, "activityType") || "TASK",
        subject: formValue(form, "subject"),
        relatedType: relatedId ? "ACCOUNT" : undefined,
        relatedId,
        priority: 50,
      }),
      "تم إنشاء النشاط.",
    );
    formElement.reset();
  }

  if (state !== "AUTHENTICATED" || !dashboard) return <AuthLoadingState />;

  return (
    <main className={styles.root} dir="rtl">
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>SNAD CRM</p>
          <h1>إدارة علاقات العملاء</h1>
          <p>تشغيل الحسابات والمبيعات والأنشطة من واجهة موحدة.</p>
        </div>
        <div className={styles.headerActions}>
          <span>{me?.displayName ?? "المستخدم"}</span>
          <button type="button" onClick={() => void reload()} disabled={busy}>تحديث</button>
          <button type="button" onClick={() => router.push("/workspace")}>مساحة العمل</button>
        </div>
      </header>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.metrics} aria-label="مؤشرات CRM">
        <article><span>الحسابات</span><strong>{dashboard.accounts}</strong></article>
        <article><span>جهات الاتصال</span><strong>{dashboard.contacts}</strong></article>
        <article><span>العملاء المفتوحون</span><strong>{dashboard.openLeads}</strong></article>
        <article><span>الفرص المفتوحة</span><strong>{dashboard.openOpportunities}</strong></article>
        <article><span>القيمة المرجحة</span><strong>{formatNumber(dashboard.weightedPipeline)}</strong></article>
        <article><span>أنشطة متأخرة</span><strong>{dashboard.overdueActivities}</strong></article>
      </section>

      <nav className={styles.tabs} aria-label="أقسام CRM">
        {([
          ["accounts", "الحسابات"],
          ["contacts", "جهات الاتصال"],
          ["leads", "العملاء المحتملون"],
          ["sales", "المبيعات"],
          ["activities", "الأنشطة"],
        ] as Array<[Tab, string]>).map(([key, label]) => (
          <button type="button" key={key} className={tab === key ? styles.activeTab : ""} onClick={() => setTab(key)}>{label}</button>
        ))}
      </nav>

      {tab === "accounts" ? (
        <section className={styles.workspace}>
          <form className={styles.formCard} onSubmit={createAccount}>
            <h2>حساب جديد</h2>
            <label>الاسم<input name="displayName" required /></label>
            <label>النوع<select name="accountType" defaultValue="BUSINESS"><option value="BUSINESS">منشأة</option><option value="PERSON">فرد</option><option value="PARTNER">شريك</option><option value="PROSPECT">عميل محتمل</option></select></label>
            <label>العملة<input name="currency" defaultValue="SAR" maxLength={3} /></label>
            <button type="submit" disabled={busy}>إنشاء</button>
          </form>
          <div className={styles.listCard}>
            <h2>الحسابات</h2>
            <div className={styles.tableWrap}><table><thead><tr><th>الاسم</th><th>النوع</th><th>العملة</th><th /></tr></thead><tbody>{accounts.map((account) => <tr key={account.id}><td>{account.display_name}</td><td>{account.account_type}</td><td>{account.primary_currency_code ?? "—"}</td><td className={styles.rowActions}><button type="button" onClick={() => void openCustomer(account.id)}>360°</button><button type="button" onClick={() => void mutate(() => crmApi.archiveAccount(account.id), "تمت أرشفة الحساب.")}>أرشفة</button></td></tr>)}</tbody></table></div>
          </div>
        </section>
      ) : null}

      {tab === "contacts" ? (
        <section className={styles.workspace}>
          <form className={styles.formCard} onSubmit={createContact}>
            <h2>جهة اتصال جديدة</h2>
            <label>الحساب<select name="accountId" defaultValue=""><option value="">بدون حساب</option>{accounts.map((account) => <option key={account.id} value={account.id}>{account.display_name}</option>)}</select></label>
            <label>الاسم الأول<input name="givenName" required /></label>
            <label>اسم العائلة<input name="familyName" /></label>
            <button type="submit" disabled={busy}>إنشاء</button>
          </form>
          <div className={styles.listCard}><h2>جهات الاتصال</h2><div className={styles.tableWrap}><table><thead><tr><th>الاسم</th><th>الحساب</th><th>الحالة</th><th /></tr></thead><tbody>{contacts.map((contact) => <tr key={contact.id}><td>{contact.display_name}</td><td>{contact.account_id ? accountNames.get(contact.account_id) ?? "—" : "—"}</td><td>{contact.lifecycle_status}</td><td><button type="button" onClick={() => void mutate(() => crmApi.archiveContact(contact.id), "تمت أرشفة جهة الاتصال.")}>أرشفة</button></td></tr>)}</tbody></table></div></div>
        </section>
      ) : null}

      {tab === "leads" ? (
        <section className={styles.workspace}>
          <form className={styles.formCard} onSubmit={createLead}><h2>عميل محتمل جديد</h2><label>الاسم<input name="displayName" required /></label><label>المنشأة<input name="companyName" /></label><button type="submit" disabled={busy}>إنشاء</button></form>
          <div className={styles.listCard}><h2>العملاء المحتملون</h2><div className={styles.tableWrap}><table><thead><tr><th>الاسم</th><th>المنشأة</th><th>الحالة</th><th /></tr></thead><tbody>{leads.map((lead) => <tr key={lead.id}><td>{lead.display_name}</td><td>{lead.company_name ?? "—"}</td><td>{lead.status}</td><td className={styles.rowActions}>{lead.status === "NEW" ? <button type="button" onClick={() => void mutate(() => crmApi.changeLeadStatus(lead.id, "QUALIFIED"), "تم تأهيل العميل.")}>تأهيل</button> : null}{!["CONVERTED", "ARCHIVED", "DISQUALIFIED"].includes(lead.status) ? <button type="button" onClick={() => void mutate(() => crmApi.convertLead(lead.id, { createOpportunity: true, currencyCode: "SAR" }), "تم تحويل العميل.")}>تحويل</button> : null}</td></tr>)}</tbody></table></div></div>
        </section>
      ) : null}

      {tab === "sales" ? (
        <section className={styles.salesGrid}>
          <form className={styles.formCard} onSubmit={createPipeline}><h2>قناة جديدة</h2><label>الاسم<input name="name" required /></label><label>العملة<input name="currency" defaultValue="SAR" /></label><label>المراحل<input name="stages" defaultValue="New, Qualified, Proposal, Won, Lost" /></label><button type="submit" disabled={busy}>إنشاء القناة</button></form>
          <form className={styles.formCard} onSubmit={createOpportunity}><h2>فرصة جديدة</h2><label>الحساب<select name="accountId" required defaultValue=""><option value="" disabled>اختر</option>{accounts.map((account) => <option key={account.id} value={account.id}>{account.display_name}</option>)}</select></label><label>القناة<select name="pipelineId" required defaultValue=""><option value="" disabled>اختر</option>{pipelines.map((pipeline) => <option key={pipeline.id} value={pipeline.id}>{pipeline.name}</option>)}</select></label><label>المرحلة<select name="stageId" required defaultValue=""><option value="" disabled>اختر</option>{pipelines.flatMap((pipeline) => (stages[pipeline.id] ?? []).map((stage) => <option key={stage.id} value={stage.id}>{pipeline.name} — {stage.name}</option>))}</select></label><label>الاسم<input name="name" required /></label><label>القيمة<input name="amount" type="number" min="0" step="0.01" /></label><label>العملة<input name="currency" defaultValue="SAR" /></label><label>الإغلاق المتوقع<input name="expectedCloseDate" type="date" /></label><button type="submit" disabled={busy}>إنشاء الفرصة</button></form>
          <div className={styles.listCard}><h2>الفرص</h2><div className={styles.tableWrap}><table><thead><tr><th>الفرصة</th><th>الحساب</th><th>القيمة</th><th>الحالة</th><th>المرحلة</th></tr></thead><tbody>{opportunities.map((opportunity) => <tr key={opportunity.id}><td>{opportunity.name}</td><td>{accountNames.get(opportunity.account_id) ?? "—"}</td><td>{formatNumber(opportunity.amount)} {opportunity.currency_code}</td><td>{opportunity.status}</td><td><select value={opportunity.stage_id} disabled={busy || opportunity.status !== "OPEN"} onChange={(event) => void mutate(() => crmApi.moveOpportunity(opportunity.id, event.target.value), "تم تحديث المرحلة.")}>{(stages[opportunity.pipeline_id] ?? []).map((stage) => <option key={stage.id} value={stage.id}>{stage.name}</option>)}</select></td></tr>)}</tbody></table></div></div>
        </section>
      ) : null}

      {tab === "activities" ? (
        <section className={styles.workspace}>
          <form className={styles.formCard} onSubmit={createActivity}><h2>نشاط جديد</h2><label>النوع<select name="activityType" defaultValue="TASK"><option value="TASK">مهمة</option><option value="CALL">اتصال</option><option value="MEETING">اجتماع</option><option value="NOTE">ملاحظة</option></select></label><label>الموضوع<input name="subject" required /></label><label>الحساب<select name="relatedId" defaultValue=""><option value="">بدون ارتباط</option>{accounts.map((account) => <option key={account.id} value={account.id}>{account.display_name}</option>)}</select></label><button type="submit" disabled={busy}>إنشاء</button></form>
          <div className={styles.listCard}><h2>الأنشطة</h2><div className={styles.tableWrap}><table><thead><tr><th>الموضوع</th><th>النوع</th><th>الحالة</th><th>الاستحقاق</th><th /></tr></thead><tbody>{activities.map((activity) => <tr key={activity.id}><td>{activity.subject}</td><td>{activity.activity_type}</td><td>{activity.status}</td><td>{formatDate(activity.due_at)}</td><td>{["OPEN", "IN_PROGRESS"].includes(activity.status) ? <button type="button" onClick={() => void mutate(() => crmApi.completeActivity(activity.id), "تم إكمال النشاط.")}>إكمال</button> : null}</td></tr>)}</tbody></table></div></div>
        </section>
      ) : null}

      {customer ? (
        <aside className={styles.drawer} aria-label="Customer 360">
          <div className={styles.drawerHeader}><div><p className={styles.eyebrow}>Customer 360</p><h2>{customer.account.display_name}</h2></div><button type="button" onClick={() => setCustomer(null)}>إغلاق</button></div>
          <div className={styles.drawerMetrics}><span>جهات الاتصال <strong>{customer.contacts.length}</strong></span><span>الفرص <strong>{customer.opportunities.length}</strong></span><span>الأنشطة <strong>{customer.activities.length}</strong></span></div>
          <h3>الخط الزمني</h3>
          <ol className={styles.timeline}>{customer.timeline.map((event) => <li key={event.id}><strong>{event.summary}</strong><span>{formatDate(event.occurred_at)}</span></li>)}</ol>
        </aside>
      ) : null}
    </main>
  );
}
