"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { apiClient } from "@/lib/api/client";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import styles from "../../../crm.module.css";

interface CustomerMasterProfile {
  accountId: string;
  version: number;
  displayName: string;
  legalName?: string | null;
  tradingName?: string | null;
  registrationNumber?: string | null;
  taxNumber?: string | null;
  industryCode?: string | null;
  customerSegment?: string | null;
  customerTier?: string | null;
  website?: string | null;
  primaryEmail?: string | null;
  primaryPhone?: string | null;
  countryCode?: string | null;
  riskRating?: string | null;
  creditLimit?: number | null;
  paymentTermsDays?: number | null;
  dataQualityScore: number;
  mergedIntoAccountId?: string | null;
}

interface AccountAddress {
  id: string;
  addressType: string;
  label?: string | null;
  line1: string;
  city: string;
  countryCode: string;
  primaryAddress: boolean;
}

interface AccountIdentifier {
  id: string;
  identifierType: string;
  identifierValue: string;
  verified: boolean;
}

interface DuplicateCandidate {
  accountId: string;
  displayName: string;
  confidenceScore: number;
  matchedFields: string[];
}

const root = "/api/v1/crm/accounts";

function field(form: FormData, name: string): string | undefined {
  const value = form.get(name)?.toString().trim();
  return value ? value : undefined;
}

export function CustomerMasterPanel({ accountId }: { accountId: string }) {
  const [profile, setProfile] = useState<CustomerMasterProfile | null>(null);
  const [addresses, setAddresses] = useState<AccountAddress[]>([]);
  const [identifiers, setIdentifiers] = useState<AccountIdentifier[]>([]);
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const reload = useCallback(async () => {
    if (!accountId) return;
    setError("");
    try {
      const [nextProfile, nextAddresses, nextIdentifiers, nextDuplicates] = await Promise.all([
        apiClient.get<CustomerMasterProfile>(`${root}/${accountId}/master`, { cache: "no-store" }),
        apiClient.get<AccountAddress[]>(`${root}/${accountId}/addresses`, { cache: "no-store" }),
        apiClient.get<AccountIdentifier[]>(`${root}/${accountId}/identifiers`, { cache: "no-store" }),
        apiClient.get<DuplicateCandidate[]>(`${root}/${accountId}/duplicates`, { cache: "no-store" }),
      ]);
      setProfile(nextProfile);
      setAddresses(nextAddresses);
      setIdentifiers(nextIdentifiers);
      setDuplicates(nextDuplicates);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    }
  }, [accountId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  async function execute(action: () => Promise<unknown>, success: string) {
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
    if (!profile) return;
    const form = new FormData(event.currentTarget);
    await execute(
      () => apiClient.patch<CustomerMasterProfile, Record<string, unknown>>(`${root}/${accountId}/master`, {
        expectedVersion: profile.version,
        legalName: field(form, "legalName"),
        tradingName: field(form, "tradingName"),
        registrationNumber: field(form, "registrationNumber"),
        taxNumber: field(form, "taxNumber"),
        industryCode: field(form, "industryCode"),
        customerSegment: field(form, "customerSegment"),
        customerTier: field(form, "customerTier"),
        website: field(form, "website"),
        primaryEmail: field(form, "primaryEmail"),
        primaryPhone: field(form, "primaryPhone"),
        countryCode: field(form, "countryCode"),
        riskRating: field(form, "riskRating"),
        creditLimit: field(form, "creditLimit") ? Number(field(form, "creditLimit")) : undefined,
        paymentTermsDays: field(form, "paymentTermsDays") ? Number(field(form, "paymentTermsDays")) : undefined,
      }),
      "تم تحديث ملف العميل المؤسسي.",
    );
  }

  async function addAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const element = event.currentTarget;
    const form = new FormData(element);
    await execute(
      () => apiClient.post(`${root}/${accountId}/addresses`, {
        addressType: field(form, "addressType") ?? "OFFICE",
        label: field(form, "label"),
        line1: field(form, "line1"),
        city: field(form, "city"),
        postalCode: field(form, "postalCode"),
        countryCode: field(form, "addressCountry") ?? "SA",
        primaryAddress: form.get("primaryAddress") === "on",
      }),
      "تمت إضافة العنوان.",
    );
    element.reset();
  }

  async function addIdentifier(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const element = event.currentTarget;
    const form = new FormData(element);
    await execute(
      () => apiClient.post(`${root}/${accountId}/identifiers`, {
        identifierType: field(form, "identifierType") ?? "COMMERCIAL_REGISTRATION",
        identifierValue: field(form, "identifierValue"),
        issuerCountryCode: field(form, "issuerCountryCode") ?? "SA",
        primaryIdentifier: true,
        verified: form.get("verified") === "on",
      }),
      "تمت إضافة المعرّف المؤسسي.",
    );
    element.reset();
  }

  async function merge(candidate: DuplicateCandidate) {
    if (!profile || !window.confirm(`دمج ${profile.displayName} في ${candidate.displayName}؟ لا يمكن التراجع عن العملية.`)) return;
    const target = await apiClient.get<CustomerMasterProfile>(`${root}/${candidate.accountId}/master`, { cache: "no-store" });
    await execute(
      () => apiClient.post(`${root}/${accountId}/merge/${candidate.accountId}`, {
        expectedSourceVersion: profile.version,
        expectedTargetVersion: target.version,
        reason: "Confirmed duplicate from Customer Master workspace",
      }),
      "تم دمج سجل العميل بنجاح.",
    );
  }

  if (!profile) {
    return <section className={styles.overviewSection}><p className={styles.notice}>تحميل ملف العميل المؤسسي…</p></section>;
  }

  return (
    <section className={styles.overviewSection} aria-label="Enterprise Customer Master">
      <div className={styles.rowHeader}>
        <div>
          <h2 className={styles.overviewSectionTitle}>ملف العميل المؤسسي · Enterprise Customer Master</h2>
          <p className={styles.pageDescription}>جودة البيانات: {profile.dataQualityScore}% · الإصدار {profile.version}</p>
        </div>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <div className={styles.workspace}>
        <form className={styles.formCard} onSubmit={updateProfile}>
          <h3 className={styles.sectionHeading}>الهوية والتصنيف والائتمان</h3>
          <label>الاسم القانوني<input name="legalName" defaultValue={profile.legalName ?? ""} disabled={busy} /></label>
          <label>الاسم التجاري<input name="tradingName" defaultValue={profile.tradingName ?? ""} disabled={busy} /></label>
          <label>السجل التجاري<input name="registrationNumber" defaultValue={profile.registrationNumber ?? ""} disabled={busy} /></label>
          <label>الرقم الضريبي<input name="taxNumber" defaultValue={profile.taxNumber ?? ""} disabled={busy} /></label>
          <label>القطاع<input name="industryCode" defaultValue={profile.industryCode ?? ""} disabled={busy} /></label>
          <label>الشريحة<input name="customerSegment" defaultValue={profile.customerSegment ?? ""} disabled={busy} /></label>
          <label>الفئة<select name="customerTier" defaultValue={profile.customerTier ?? "STANDARD"} disabled={busy}><option>STANDARD</option><option>SILVER</option><option>GOLD</option><option>PLATINUM</option><option>STRATEGIC</option></select></label>
          <label>تقييم المخاطر<select name="riskRating" defaultValue={profile.riskRating ?? "UNASSESSED"} disabled={busy}><option>UNASSESSED</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>RESTRICTED</option></select></label>
          <label>البريد الرئيسي<input name="primaryEmail" type="email" defaultValue={profile.primaryEmail ?? ""} disabled={busy} /></label>
          <label>الهاتف الرئيسي<input name="primaryPhone" defaultValue={profile.primaryPhone ?? ""} disabled={busy} /></label>
          <label>الدولة<input name="countryCode" maxLength={2} defaultValue={profile.countryCode ?? "SA"} disabled={busy} /></label>
          <label>الموقع الإلكتروني<input name="website" type="url" defaultValue={profile.website ?? ""} disabled={busy} /></label>
          <label>حد الائتمان<input name="creditLimit" type="number" min="0" step="0.01" defaultValue={profile.creditLimit ?? ""} disabled={busy} /></label>
          <label>أيام السداد<input name="paymentTermsDays" type="number" min="0" max="365" defaultValue={profile.paymentTermsDays ?? ""} disabled={busy} /></label>
          <button type="submit" disabled={busy}>حفظ الملف المؤسسي</button>
        </form>

        <div className={styles.listCard}>
          <h3 className={styles.sectionHeading}>العناوين</h3>
          <form onSubmit={addAddress}>
            <select name="addressType" defaultValue="OFFICE" disabled={busy}><option>REGISTERED</option><option>BILLING</option><option>SHIPPING</option><option>OFFICE</option><option>OTHER</option></select>
            <input name="label" placeholder="الوصف" disabled={busy} />
            <input name="line1" placeholder="العنوان" required disabled={busy} />
            <input name="city" placeholder="المدينة" required disabled={busy} />
            <input name="postalCode" placeholder="الرمز البريدي" disabled={busy} />
            <input name="addressCountry" defaultValue="SA" maxLength={2} disabled={busy} />
            <label><input name="primaryAddress" type="checkbox" /> عنوان رئيسي</label>
            <button type="submit" disabled={busy}>إضافة عنوان</button>
          </form>
          <ul>{addresses.map((address) => <li key={address.id}><strong>{address.label ?? address.addressType}</strong> — {address.line1}، {address.city}، {address.countryCode}{address.primaryAddress ? " · رئيسي" : ""}</li>)}</ul>

          <h3 className={styles.sectionHeading}>المعرّفات الرسمية</h3>
          <form onSubmit={addIdentifier}>
            <select name="identifierType" defaultValue="COMMERCIAL_REGISTRATION" disabled={busy}><option>COMMERCIAL_REGISTRATION</option><option>TAX</option><option>VAT</option><option>NATIONAL_ID</option><option>DUNS</option><option>EXTERNAL</option><option>OTHER</option></select>
            <input name="identifierValue" placeholder="قيمة المعرّف" required disabled={busy} />
            <input name="issuerCountryCode" defaultValue="SA" maxLength={2} disabled={busy} />
            <label><input name="verified" type="checkbox" /> موثّق</label>
            <button type="submit" disabled={busy}>إضافة معرّف</button>
          </form>
          <ul>{identifiers.map((identifier) => <li key={identifier.id}><strong>{identifier.identifierType}</strong> — {identifier.identifierValue}{identifier.verified ? " · موثّق" : ""}</li>)}</ul>

          <h3 className={styles.sectionHeading}>مرشحو التكرار والدمج</h3>
          {duplicates.length === 0 ? <p className={styles.notice}>لا توجد سجلات متكررة محتملة.</p> : (
            <div className={styles.tableWrap}><table><thead><tr><th>السجل</th><th>الثقة</th><th>الحقول</th><th>الإجراء</th></tr></thead><tbody>{duplicates.map((candidate) => <tr key={candidate.accountId}><td>{candidate.displayName}</td><td>{candidate.confidenceScore}%</td><td>{candidate.matchedFields.join(", ")}</td><td><button type="button" disabled={busy} onClick={() => void merge(candidate)}>دمج في هذا السجل</button></td></tr>)}</tbody></table></div>
          )}
        </div>
      </div>
    </section>
  );
}
