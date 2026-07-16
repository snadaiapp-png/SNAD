"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { crmApi, type Customer360 } from "@/lib/api/crm";
import {
  crmAccountMasterApi,
  type AccountExternalIdentifier,
  type AccountHistory,
  type AccountMasterOverview,
  type AccountMasterRisk,
  type AccountRelationship,
  type AccountTaxonomy,
} from "@/lib/api/crm-account-master";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, formatDate, formatNumber } from "../../../crm-view-utils";
import { CrmLoading } from "../../../components/crm-loading";
import { CrmError } from "../../../components/crm-error";
import styles from "../../../crm.module.css";

type Tab = "overview" | "contacts" | "opportunities" | "activities" | "interactions" |
  "financial" | "orders" | "service" | "audit";

const COPY = {
  ar: {
    title: "ملف العميل المؤسسي 360",
    back: "العودة إلى الحسابات",
    overview: "نظرة عامة",
    contacts: "جهات الاتصال",
    opportunities: "الفرص",
    activities: "الأنشطة",
    interactions: "التفاعلات",
    financial: "الملخص المالي",
    orders: "الطلبات",
    service: "الخدمة",
    audit: "السجل والتدقيق",
    profile: "البيانات النظامية والتجارية",
    legalName: "الاسم القانوني",
    tradeName: "الاسم التجاري",
    registration: "رقم السجل",
    taxRegistration: "الرقم الضريبي",
    industry: "القطاع",
    size: "حجم المنشأة",
    website: "الموقع الإلكتروني",
    tier: "فئة العميل",
    classification: "التصنيف",
    segment: "شريحة العميل",
    save: "حفظ التغييرات",
    risk: "المخاطر والحالة",
    riskLevel: "مستوى المخاطر",
    riskFlags: "علامات المخاطر (مفصولة بفواصل)",
    mergeCandidate: "مرشح للدمج",
    relationships: "علاقات الحساب",
    targetAccount: "معرف الحساب المرتبط",
    relationshipType: "نوع العلاقة",
    addRelationship: "إضافة علاقة",
    endRelationship: "إنهاء العلاقة",
    identifiers: "المعرفات الخارجية",
    provider: "المزود",
    systemScope: "نطاق النظام",
    externalId: "المعرف الخارجي",
    label: "الوصف",
    addIdentifier: "إضافة معرف",
    remove: "إزالة",
    notConnected: "مصدر البيانات غير متصل. لم يتم إنشاء أرقام وهمية.",
    empty: "لا توجد بيانات في هذا القسم.",
    saved: "تم حفظ بيانات الحساب.",
    relationshipAdded: "تمت إضافة العلاقة.",
    relationshipEnded: "تم إنهاء العلاقة.",
    identifierAdded: "تمت إضافة المعرف.",
    identifierRemoved: "تمت إزالة المعرف.",
    statusHistory: "تاريخ الحالة",
    ownershipHistory: "تاريخ الملكية",
  },
  en: {
    title: "Enterprise Customer 360",
    back: "Back to accounts",
    overview: "Overview",
    contacts: "Contacts",
    opportunities: "Opportunities",
    activities: "Activities",
    interactions: "Interactions",
    financial: "Financial Summary",
    orders: "Orders",
    service: "Service",
    audit: "Audit & History",
    profile: "Legal and commercial profile",
    legalName: "Legal name",
    tradeName: "Trade name",
    registration: "Registration number",
    taxRegistration: "Tax registration number",
    industry: "Industry",
    size: "Organization size",
    website: "Website",
    tier: "Customer tier",
    classification: "Classification",
    segment: "Customer segment",
    save: "Save changes",
    risk: "Risk and state",
    riskLevel: "Risk level",
    riskFlags: "Risk flags (comma-separated)",
    mergeCandidate: "Merge candidate",
    relationships: "Account relationships",
    targetAccount: "Related account ID",
    relationshipType: "Relationship type",
    addRelationship: "Add relationship",
    endRelationship: "End relationship",
    identifiers: "External identifiers",
    provider: "Provider",
    systemScope: "System scope",
    externalId: "External ID",
    label: "Label",
    addIdentifier: "Add identifier",
    remove: "Remove",
    notConnected: "The source system is not connected. No synthetic figures are displayed.",
    empty: "No data is available in this section.",
    saved: "Account master data was saved.",
    relationshipAdded: "Relationship added.",
    relationshipEnded: "Relationship ended.",
    identifierAdded: "External identifier added.",
    identifierRemoved: "External identifier removed.",
    statusHistory: "Status history",
    ownershipHistory: "Ownership history",
  },
} as const;

export default function Customer360Page() {
  const { locale } = useI18n();
  const copy = COPY[locale];
  const params = useParams<{ accountId: string }>();
  const accountId = params?.accountId ?? "";

  const [customer, setCustomer] = useState<Customer360 | null>(null);
  const [master, setMaster] = useState<AccountMasterOverview | null>(null);
  const [risk, setRisk] = useState<AccountMasterRisk | null>(null);
  const [relationships, setRelationships] = useState<AccountRelationship[]>([]);
  const [identifiers, setIdentifiers] = useState<AccountExternalIdentifier[]>([]);
  const [history, setHistory] = useState<AccountHistory | null>(null);
  const [classifications, setClassifications] = useState<AccountTaxonomy[]>([]);
  const [segments, setSegments] = useState<AccountTaxonomy[]>([]);
  const [tab, setTab] = useState<Tab>("overview");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    if (!accountId) return;
    setLoading(true);
    setError("");
    try {
      const base = await crmApi.customer360(accountId);
      setCustomer(base);
      const [masterResult, riskResult, relationResult, identifierResult, historyResult, classResult, segmentResult] =
        await Promise.allSettled([
          crmAccountMasterApi.overview(accountId),
          crmAccountMasterApi.risk(accountId),
          crmAccountMasterApi.relationships(accountId),
          crmAccountMasterApi.externalIdentifiers(accountId),
          crmAccountMasterApi.history(accountId),
          crmAccountMasterApi.taxonomies("CLASSIFICATION"),
          crmAccountMasterApi.taxonomies("SEGMENT"),
        ]);
      setMaster(masterResult.status === "fulfilled" ? masterResult.value : null);
      setRisk(riskResult.status === "fulfilled" ? riskResult.value : null);
      setRelationships(relationResult.status === "fulfilled" ? relationResult.value : []);
      setIdentifiers(identifierResult.status === "fulfilled" ? identifierResult.value : []);
      setHistory(historyResult.status === "fulfilled" ? historyResult.value : null);
      setClassifications(classResult.status === "fulfilled" ? classResult.value : []);
      setSegments(segmentResult.status === "fulfilled" ? segmentResult.value : []);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setCustomer(null);
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  async function mutate(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(success);
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!master) return;
    const form = new FormData(event.currentTarget);
    await mutate(() => crmAccountMasterApi.updateProfile(accountId, master.profile.version, {
      legalName: formValue(form, "legalName"),
      tradeName: formValue(form, "tradeName"),
      registrationNumber: formValue(form, "registrationNumber"),
      taxRegistrationNumber: formValue(form, "taxRegistrationNumber"),
      industry: formValue(form, "industry"),
      organizationSize: formValue(form, "organizationSize") || undefined,
      websiteUrl: formValue(form, "websiteUrl"),
      customerTier: formValue(form, "customerTier"),
      classificationId: formValue(form, "classificationId") || undefined,
      segmentId: formValue(form, "segmentId") || undefined,
    }), copy.saved);
  }

  async function updateRisk(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!risk) return;
    const form = new FormData(event.currentTarget);
    const flags = formValue(form, "riskFlags").split(",").map((item) => item.trim()).filter(Boolean);
    await mutate(() => crmAccountMasterApi.updateRisk(accountId, risk.version, {
      riskLevel: formValue(form, "riskLevel") || "UNKNOWN",
      riskFlags: flags,
      mergeCandidate: form.get("mergeCandidate") === "on",
    }), copy.saved);
  }

  async function createRelationship(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(() => crmAccountMasterApi.createRelationship(accountId, {
      targetAccountId: formValue(form, "targetAccountId"),
      relationshipType: formValue(form, "relationshipType"),
      effectiveFrom: new Date().toISOString().slice(0, 10),
      description: formValue(form, "description") || undefined,
    }), copy.relationshipAdded);
    formElement.reset();
  }

  async function createIdentifier(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(() => crmAccountMasterApi.createExternalIdentifier(accountId, {
      provider: formValue(form, "provider"),
      systemScope: formValue(form, "systemScope"),
      externalId: formValue(form, "externalId"),
      label: formValue(form, "label") || undefined,
    }), copy.identifierAdded);
    formElement.reset();
  }

  const tabs = useMemo<Array<{ id: Tab; label: string }>>(() => [
    { id: "overview", label: copy.overview },
    { id: "contacts", label: copy.contacts },
    { id: "opportunities", label: copy.opportunities },
    { id: "activities", label: copy.activities },
    { id: "interactions", label: copy.interactions },
    { id: "financial", label: copy.financial },
    { id: "orders", label: copy.orders },
    { id: "service", label: copy.service },
    { id: "audit", label: copy.audit },
  ], [copy]);

  if (loading) return <div className={styles.contentInner}><CrmLoading /></div>;
  if (error && !customer) return (
    <div className={styles.contentInner}>
      <CrmError message={error} onRetry={() => void reload()} />
      <Link href="/crm/accounts">{copy.back}</Link>
    </div>
  );
  if (!customer) return <div className={styles.contentInner}><p>{copy.empty}</p></div>;

  const account = customer.account;
  const projection = (type: AccountMasterOverview["projections"][number]["projectionType"]) =>
    master?.projections.find((item) => item.projectionType === type);

  return (
    <div className={styles.contentInner}>
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{copy.title}</h1>
          <p className={styles.pageDescription}>{master?.profile.tradeName || account.display_name}</p>
        </div>
        <Link href="/crm/accounts">{copy.back}</Link>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <nav className={styles.rowActions} aria-label={copy.title}>
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-selected={tab === item.id}
            className={tab === item.id ? styles.primaryButton : undefined}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {tab === "overview" ? (
        <div className={styles.workspace}>
          <section className={styles.listCard}>
            <h2 className={styles.sectionHeading}>{copy.profile}</h2>
            {master ? (
              <form className={styles.formCard} onSubmit={updateProfile}>
                <label>{copy.legalName}<input name="legalName" defaultValue={master.profile.legalName} required disabled={busy} /></label>
                <label>{copy.tradeName}<input name="tradeName" defaultValue={master.profile.tradeName ?? ""} disabled={busy} /></label>
                <label>{copy.registration}<input name="registrationNumber" defaultValue={master.profile.registrationNumber ?? ""} disabled={busy} /></label>
                <label>{copy.taxRegistration}<input name="taxRegistrationNumber" defaultValue={master.profile.taxRegistrationNumber ?? ""} disabled={busy} /></label>
                <label>{copy.industry}<input name="industry" defaultValue={master.profile.industry ?? ""} disabled={busy} /></label>
                <label>{copy.size}
                  <select name="organizationSize" defaultValue={master.profile.organizationSize ?? ""} disabled={busy}>
                    <option value="">—</option><option value="MICRO">MICRO</option><option value="SMALL">SMALL</option>
                    <option value="MEDIUM">MEDIUM</option><option value="LARGE">LARGE</option><option value="ENTERPRISE">ENTERPRISE</option>
                  </select>
                </label>
                <label>{copy.website}<input name="websiteUrl" type="url" defaultValue={master.profile.websiteUrl ?? ""} disabled={busy} /></label>
                <label>{copy.tier}<input name="customerTier" defaultValue={master.profile.customerTier ?? ""} disabled={busy} /></label>
                <label>{copy.classification}
                  <select name="classificationId" defaultValue={master.profile.classificationId ?? ""} disabled={busy}>
                    <option value="">—</option>{classifications.map((item) => <option key={item.id} value={item.id}>{locale === "ar" ? item.nameAr : item.nameEn}</option>)}
                  </select>
                </label>
                <label>{copy.segment}
                  <select name="segmentId" defaultValue={master.profile.segmentId ?? ""} disabled={busy}>
                    <option value="">—</option>{segments.map((item) => <option key={item.id} value={item.id}>{locale === "ar" ? item.nameAr : item.nameEn}</option>)}
                  </select>
                </label>
                <button type="submit" disabled={busy}>{copy.save}</button>
              </form>
            ) : <p className={styles.notice}>{copy.empty}</p>}
          </section>

          <section className={styles.listCard}>
            <h2 className={styles.sectionHeading}>{copy.risk}</h2>
            {risk ? (
              <form className={styles.formCard} onSubmit={updateRisk}>
                <label>{copy.riskLevel}
                  <select name="riskLevel" defaultValue={risk.riskLevel} disabled={busy}>
                    <option value="UNKNOWN">UNKNOWN</option><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option>
                    <option value="HIGH">HIGH</option><option value="CRITICAL">CRITICAL</option>
                  </select>
                </label>
                <label>{copy.riskFlags}<input name="riskFlags" defaultValue={risk.riskFlags.join(", ")} disabled={busy} /></label>
                <label><input name="mergeCandidate" type="checkbox" defaultChecked={risk.mergeCandidate} disabled={busy} /> {copy.mergeCandidate}</label>
                <button type="submit" disabled={busy}>{copy.save}</button>
              </form>
            ) : <p className={styles.notice}>{copy.empty}</p>}
          </section>

          <section className={styles.listCard}>
            <h2 className={styles.sectionHeading}>{copy.relationships}</h2>
            <form className={styles.formCard} onSubmit={createRelationship}>
              <label>{copy.targetAccount}<input name="targetAccountId" required disabled={busy} /></label>
              <label>{copy.relationshipType}
                <select name="relationshipType" defaultValue="PARTNER" disabled={busy}>
                  <option value="PARENT">PARENT</option><option value="SUBSIDIARY">SUBSIDIARY</option>
                  <option value="BRANCH">BRANCH</option><option value="PARTNER">PARTNER</option>
                </select>
              </label>
              <label>{copy.label}<input name="description" disabled={busy} /></label>
              <button type="submit" disabled={busy}>{copy.addRelationship}</button>
            </form>
            {relationships.length ? <div className={styles.tableWrap}><table><tbody>{relationships.map((item) => (
              <tr key={item.id}><td>{item.relationshipType}</td><td>{item.targetAccountId}</td><td>{item.status}</td><td>
                {item.status === "ACTIVE" ? <button type="button" disabled={busy} onClick={() => void mutate(() => crmAccountMasterApi.endRelationship(accountId, item), copy.relationshipEnded)}>{copy.endRelationship}</button> : null}
              </td></tr>
            ))}</tbody></table></div> : <p className={styles.notice}>{copy.empty}</p>}
          </section>

          <section className={styles.listCard}>
            <h2 className={styles.sectionHeading}>{copy.identifiers}</h2>
            <form className={styles.formCard} onSubmit={createIdentifier}>
              <label>{copy.provider}<input name="provider" required disabled={busy} /></label>
              <label>{copy.systemScope}<input name="systemScope" required disabled={busy} /></label>
              <label>{copy.externalId}<input name="externalId" required disabled={busy} /></label>
              <label>{copy.label}<input name="label" disabled={busy} /></label>
              <button type="submit" disabled={busy}>{copy.addIdentifier}</button>
            </form>
            {identifiers.length ? <div className={styles.tableWrap}><table><tbody>{identifiers.map((item) => (
              <tr key={item.id}><td>{item.provider}</td><td>{item.systemScope}</td><td>{item.externalId}</td><td>
                <button type="button" disabled={busy} onClick={() => void mutate(() => crmAccountMasterApi.removeExternalIdentifier(accountId, item.id), copy.identifierRemoved)}>{copy.remove}</button>
              </td></tr>
            ))}</tbody></table></div> : <p className={styles.notice}>{copy.empty}</p>}
          </section>
        </div>
      ) : null}

      {tab === "contacts" ? <DataTable empty={copy.empty} headers={["Name", "Email", "Status"]} rows={customer.contacts.map((item) => [item.display_name, item.primary_email ?? "—", item.lifecycle_status])} /> : null}
      {tab === "opportunities" ? <DataTable empty={copy.empty} headers={["Name", "Amount", "Status", "Stage"]} rows={customer.opportunities.map((item) => [item.name, `${formatNumber(item.amount)} ${item.currency_code}`, item.status, item.stage_name ?? "—"])} /> : null}
      {tab === "activities" ? <DataTable empty={copy.empty} headers={["Subject", "Type", "Status", "Due"]} rows={customer.activities.map((item) => [item.subject, item.activity_type, item.status, formatDate(item.due_at)])} /> : null}
      {tab === "interactions" ? (
        <section className={styles.listCard}>{customer.timeline.length ? <ol className={styles.timeline}>{customer.timeline.map((item) => <li key={item.id}><strong>{item.summary}</strong><span>{formatDate(item.occurred_at)}</span></li>)}</ol> : <p className={styles.notice}>{copy.empty}</p>}</section>
      ) : null}
      {tab === "financial" ? <ProjectionPanel projection={projection("FINANCIAL_SUMMARY")} empty={copy.notConnected} /> : null}
      {tab === "orders" ? <ProjectionPanel projection={projection("ORDERS")} empty={copy.notConnected} /> : null}
      {tab === "service" ? <ProjectionPanel projection={projection("SERVICE")} empty={copy.notConnected} /> : null}
      {tab === "audit" ? (
        <div className={styles.workspace}>
          <section className={styles.listCard}><h2 className={styles.sectionHeading}>{copy.statusHistory}</h2>
            <DataTable empty={copy.empty} headers={["From", "To", "Reason", "At"]} rows={(history?.statusHistory ?? []).map((item) => [item.fromStatus ?? "—", item.toStatus, item.reason ?? "—", formatDate(item.changedAt)])} />
          </section>
          <section className={styles.listCard}><h2 className={styles.sectionHeading}>{copy.ownershipHistory}</h2>
            <DataTable empty={copy.empty} headers={["From", "To", "Reason", "At"]} rows={(history?.ownershipHistory ?? []).map((item) => [item.fromOwnerUserId ?? "—", item.toOwnerUserId ?? "—", item.reason ?? "—", formatDate(item.changedAt)])} />
          </section>
        </div>
      ) : null}
    </div>
  );
}

function DataTable({ headers, rows, empty }: { headers: string[]; rows: string[][]; empty: string }) {
  if (!rows.length) return <p className={styles.notice}>{empty}</p>;
  return <div className={styles.tableWrap}><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={`${rowIndex}-${cellIndex}`}>{cell}</td>)}</tr>)}</tbody></table></div>;
}

function ProjectionPanel({ projection, empty }: { projection?: AccountMasterOverview["projections"][number]; empty: string }) {
  if (!projection || projection.connectionStatus === "NOT_CONNECTED") {
    return <section className={styles.listCard}><p className={styles.notice}>{empty}</p></section>;
  }
  return <section className={styles.listCard}>
    <div className={styles.rowHeader}><strong>{projection.sourceSystem}</strong><span className={styles.badge}>{projection.connectionStatus}</span></div>
    <pre>{projection.payload ? JSON.stringify(projection.payload, null, 2) : empty}</pre>
  </section>;
}
