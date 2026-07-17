"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  addressCommunicationApi,
  type AddressInput,
  type AddressType,
  type CommunicationMethod,
  type CommunicationMethodInput,
  type CommunicationMethodType,
  type CrmAddress,
  type OwnerType,
  type PrivacyClassification,
  type VerificationStatus,
} from "@/lib/api/address-communication";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { CrmEmpty } from "./crm-empty";
import { CrmLoading } from "./crm-loading";
import styles from "../crm.module.css";

interface Props {
  accountId?: string;
  contactId?: string;
}

const ADDRESS_TYPES: AddressType[] = ["REGISTERED", "BILLING", "SHIPPING", "OFFICE", "HOME", "OTHER"];
const METHOD_TYPES: CommunicationMethodType[] = [
  "EMAIL", "PHONE", "MOBILE", "FAX", "WHATSAPP", "SMS", "MESSAGING_HANDLE", "WEBSITE", "OTHER",
];
const PRIVACY: PrivacyClassification[] = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];

const COPY = {
  en: {
    title: "Addresses and communication methods",
    addressForm: "Add or edit address",
    communicationForm: "Add or edit communication method",
    addresses: "Addresses",
    communications: "Communication methods",
    type: "Type",
    label: "Label",
    line1: "Address line 1",
    line2: "Address line 2",
    district: "District",
    city: "City",
    region: "State / region",
    postal: "Postal code",
    country: "Country code",
    rawAddress: "Original formatted address",
    building: "Building number",
    shortAddress: "Short address",
    primary: "Primary",
    verified: "Verified",
    verificationSource: "Verification source",
    value: "Value",
    displayValue: "Display value",
    privacy: "Privacy",
    countryHint: "Phone country hint",
    consent: "Consent reference",
    purpose: "Usage purpose",
    preferred: "Preferred",
    verification: "Verification",
    status: "Status",
    save: "Save",
    add: "Add",
    edit: "Edit",
    cancel: "Cancel",
    makePrimary: "Make primary",
    makePreferred: "Make preferred",
    requestVerification: "Request verification",
    verify: "Mark verified",
    archive: "Archive",
    reactivate: "Reactivate",
    noAddresses: "No addresses have been recorded.",
    noCommunications: "No communication methods have been recorded.",
    saved: "Changes saved.",
    created: "Record created.",
    lifecycleUpdated: "Lifecycle updated.",
    confirmArchive: "Archive this record while retaining its history?",
    actions: "Actions",
  },
  ar: {
    title: "العناوين ووسائل الاتصال",
    addressForm: "إضافة أو تعديل عنوان",
    communicationForm: "إضافة أو تعديل وسيلة اتصال",
    addresses: "العناوين",
    communications: "وسائل الاتصال",
    type: "النوع",
    label: "التسمية",
    line1: "سطر العنوان الأول",
    line2: "سطر العنوان الثاني",
    district: "الحي",
    city: "المدينة",
    region: "المنطقة",
    postal: "الرمز البريدي",
    country: "رمز الدولة",
    rawAddress: "العنوان الأصلي المنسق",
    building: "رقم المبنى",
    shortAddress: "العنوان المختصر",
    primary: "رئيسي",
    verified: "موثق",
    verificationSource: "مصدر التوثيق",
    value: "القيمة",
    displayValue: "قيمة العرض",
    privacy: "تصنيف الخصوصية",
    countryHint: "دولة رقم الهاتف",
    consent: "مرجع الموافقة",
    purpose: "غرض الاستخدام",
    preferred: "مفضلة",
    verification: "التحقق",
    status: "الحالة",
    save: "حفظ",
    add: "إضافة",
    edit: "تعديل",
    cancel: "إلغاء",
    makePrimary: "تعيين رئيسي",
    makePreferred: "تعيين مفضلة",
    requestVerification: "طلب التحقق",
    verify: "تأكيد التحقق",
    archive: "أرشفة",
    reactivate: "إعادة تفعيل",
    noAddresses: "لا توجد عناوين مسجلة.",
    noCommunications: "لا توجد وسائل اتصال مسجلة.",
    saved: "تم حفظ التغييرات.",
    created: "تم إنشاء السجل.",
    lifecycleUpdated: "تم تحديث دورة الحياة.",
    confirmArchive: "هل تريد أرشفة السجل مع الاحتفاظ بتاريخه؟",
    actions: "الإجراءات",
  },
} as const;

type Copy = { [Key in keyof typeof COPY.en]: string };

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}
function optional(form: FormData, name: string): string | null {
  return text(form, name) || null;
}

export function AddressCommunicationWorkspace({ accountId, contactId }: Props) {
  const { locale } = useI18n();
  const copy: Copy = COPY[locale];
  const ownerType: OwnerType = accountId ? "ACCOUNT" : "PERSON";
  const ownerId = accountId ?? contactId ?? "";
  const [addresses, setAddresses] = useState<CrmAddress[]>([]);
  const [methods, setMethods] = useState<CommunicationMethod[]>([]);
  const [editingAddress, setEditingAddress] = useState<CrmAddress | null>(null);
  const [editingMethod, setEditingMethod] = useState<CommunicationMethod | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    if (!ownerId) return;
    setLoading(true);
    setError("");
    try {
      const [addressRows, methodRows] = await Promise.all([
        addressCommunicationApi.addresses(ownerType, ownerId),
        addressCommunicationApi.communicationMethods(ownerType, ownerId),
      ]);
      setAddresses(addressRows);
      setMethods(methodRows);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }, [ownerId, ownerType]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function mutate(action: () => Promise<unknown>, message: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(message);
      setEditingAddress(null);
      setEditingMethod(null);
      await load();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function submitAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const extension: Record<string, string> = {};
    const buildingNumber = optional(form, "buildingNumber");
    const shortAddress = optional(form, "shortAddress");
    if (buildingNumber) extension.buildingNumber = buildingNumber;
    if (shortAddress) extension.shortAddress = shortAddress;
    const input: AddressInput = {
      addressType: text(form, "addressType") as AddressType,
      label: optional(form, "label"),
      rawFormattedAddress: optional(form, "rawFormattedAddress"),
      line1: text(form, "line1"),
      line2: optional(form, "line2"),
      district: optional(form, "district"),
      city: text(form, "city"),
      stateRegion: optional(form, "stateRegion"),
      postalCode: optional(form, "postalCode"),
      countryCode: text(form, "countryCode").toUpperCase(),
      countryExtension: Object.keys(extension).length ? extension : null,
      primaryAddress: form.get("primaryAddress") === "on",
      verified: form.get("verified") === "on",
      verificationSource: optional(form, "verificationSource"),
    };
    await mutate(
      () => editingAddress
        ? addressCommunicationApi.updateAddress(editingAddress, input)
        : addressCommunicationApi.createAddress(ownerType, ownerId, input),
      editingAddress ? copy.saved : copy.created,
    );
  }

  async function submitCommunication(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input: CommunicationMethodInput = {
      methodType: text(form, "methodType") as CommunicationMethodType,
      rawValue: text(form, "rawValue"),
      displayValue: optional(form, "displayValue"),
      label: optional(form, "label"),
      preferred: form.get("preferred") === "on",
      privacyClassification: text(form, "privacyClassification") as PrivacyClassification,
      countryHint: optional(form, "countryHint"),
      consentStateReference: optional(form, "consentStateReference"),
      usagePurpose: optional(form, "usagePurpose"),
    };
    await mutate(
      () => editingMethod
        ? addressCommunicationApi.updateCommunicationMethod(editingMethod, input)
        : addressCommunicationApi.createCommunicationMethod(ownerType, ownerId, input),
      editingMethod ? copy.saved : copy.created,
    );
  }

  const addressDefaults = useMemo(() => ({
    addressType: editingAddress?.addressType ?? "OFFICE",
    label: editingAddress?.label ?? "",
    rawFormattedAddress: editingAddress?.rawFormattedAddress ?? "",
    line1: editingAddress?.line1 ?? "",
    line2: editingAddress?.line2 ?? "",
    district: editingAddress?.district ?? "",
    city: editingAddress?.city ?? "",
    stateRegion: editingAddress?.stateRegion ?? "",
    postalCode: editingAddress?.postalCode ?? "",
    countryCode: editingAddress?.countryCode ?? "SA",
    buildingNumber: extensionValue(editingAddress?.countryExtensionJson, "buildingNumber"),
    shortAddress: extensionValue(editingAddress?.countryExtensionJson, "shortAddress"),
  }), [editingAddress]);

  if (loading) return <CrmLoading rows={4} />;

  return (
    <section aria-labelledby="crm-address-communication-title">
      <h2 id="crm-address-communication-title" className={styles.overviewSectionTitle}>{copy.title}</h2>
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <div className={styles.salesGrid}>
        <form className={styles.formCard} onSubmit={submitAddress} key={`address-${editingAddress?.id ?? "new"}-${editingAddress?.version ?? 0}`}>
          <h2>{copy.addressForm}</h2>
          <label>{copy.type}<select name="addressType" defaultValue={addressDefaults.addressType} disabled={busy}>
            {ADDRESS_TYPES.map((value) => <option key={value} value={value}>{value}</option>)}
          </select></label>
          <label>{copy.label}<input name="label" defaultValue={addressDefaults.label} disabled={busy} /></label>
          <label>{copy.rawAddress}<input name="rawFormattedAddress" defaultValue={addressDefaults.rawFormattedAddress} disabled={busy} /></label>
          <label>{copy.line1}<input name="line1" required defaultValue={addressDefaults.line1} disabled={busy} /></label>
          <label>{copy.line2}<input name="line2" defaultValue={addressDefaults.line2} disabled={busy} /></label>
          <label>{copy.district}<input name="district" defaultValue={addressDefaults.district} disabled={busy} /></label>
          <label>{copy.city}<input name="city" required defaultValue={addressDefaults.city} disabled={busy} /></label>
          <label>{copy.region}<input name="stateRegion" defaultValue={addressDefaults.stateRegion} disabled={busy} /></label>
          <label>{copy.postal}<input name="postalCode" defaultValue={addressDefaults.postalCode} disabled={busy} /></label>
          <label>{copy.country}<input name="countryCode" required maxLength={2} defaultValue={addressDefaults.countryCode} disabled={busy} /></label>
          <label>{copy.building}<input name="buildingNumber" defaultValue={addressDefaults.buildingNumber} disabled={busy} /></label>
          <label>{copy.shortAddress}<input name="shortAddress" defaultValue={addressDefaults.shortAddress} disabled={busy} /></label>
          <label>{copy.verificationSource}<input name="verificationSource" defaultValue={editingAddress?.verificationSource ?? ""} disabled={busy} /></label>
          <label><input type="checkbox" name="primaryAddress" defaultChecked={editingAddress?.primaryAddress ?? false} disabled={busy} /> {copy.primary}</label>
          <label><input type="checkbox" name="verified" defaultChecked={editingAddress?.verified ?? false} disabled={busy} /> {copy.verified}</label>
          <button type="submit" disabled={busy}>{editingAddress ? copy.save : copy.add}</button>
          {editingAddress ? <button type="button" onClick={() => setEditingAddress(null)} disabled={busy}>{copy.cancel}</button> : null}
        </form>

        <form className={styles.formCard} onSubmit={submitCommunication} key={`method-${editingMethod?.id ?? "new"}-${editingMethod?.version ?? 0}`}>
          <h2>{copy.communicationForm}</h2>
          <label>{copy.type}<select name="methodType" defaultValue={editingMethod?.methodType ?? "EMAIL"} disabled={busy || Boolean(editingMethod)}>
            {METHOD_TYPES.map((value) => <option key={value} value={value}>{value}</option>)}
          </select></label>
          <label>{copy.value}<input name="rawValue" required defaultValue={editingMethod?.rawValue ?? editingMethod?.displayValue ?? ""} disabled={busy} /></label>
          <label>{copy.displayValue}<input name="displayValue" defaultValue={editingMethod?.displayValue ?? ""} disabled={busy} /></label>
          <label>{copy.label}<input name="label" defaultValue={editingMethod?.label ?? ""} disabled={busy} /></label>
          <label>{copy.privacy}<select name="privacyClassification" defaultValue={editingMethod?.privacyClassification ?? "INTERNAL"} disabled={busy}>
            {PRIVACY.map((value) => <option key={value} value={value}>{value}</option>)}
          </select></label>
          <label>{copy.countryHint}<input name="countryHint" maxLength={2} defaultValue="SA" disabled={busy} /></label>
          <label>{copy.consent}<input name="consentStateReference" defaultValue={editingMethod?.consentStateReference ?? ""} disabled={busy} /></label>
          <label>{copy.purpose}<input name="usagePurpose" defaultValue={editingMethod?.usagePurpose ?? "GENERAL"} disabled={busy} /></label>
          <label><input type="checkbox" name="preferred" defaultChecked={editingMethod?.preferred ?? false} disabled={busy || Boolean(editingMethod)} /> {copy.preferred}</label>
          <button type="submit" disabled={busy}>{editingMethod ? copy.save : copy.add}</button>
          {editingMethod ? <button type="button" onClick={() => setEditingMethod(null)} disabled={busy}>{copy.cancel}</button> : null}
        </form>

        <AddressTable
          rows={addresses} copy={copy} busy={busy}
          onEdit={setEditingAddress}
          onCommand={(address, command) => void mutate(
            () => addressCommunicationApi.addressCommand(address, command), copy.lifecycleUpdated)}
        />
        <CommunicationTable
          rows={methods} copy={copy} busy={busy}
          onEdit={setEditingMethod}
          onCommand={(method, command) => void mutate(
            () => addressCommunicationApi.communicationCommand(method, command), copy.lifecycleUpdated)}
          onVerify={(method, status) => void mutate(
            () => addressCommunicationApi.verify(method, status), copy.lifecycleUpdated)}
        />
      </div>
    </section>
  );
}

function AddressTable({ rows, copy, busy, onEdit, onCommand }: {
  rows: CrmAddress[]; copy: Copy; busy: boolean;
  onEdit: (row: CrmAddress) => void;
  onCommand: (row: CrmAddress, command: "primary" | "archive" | "reactivate") => void;
}) {
  return <section className={styles.listCard} aria-labelledby="crm-address-list">
    <h2 id="crm-address-list">{copy.addresses}</h2>
    {rows.length === 0 ? <CrmEmpty title={copy.noAddresses} hint="" /> : <div className={styles.tableWrap}><table>
      <thead><tr><th>{copy.type}</th><th>{copy.label}</th><th>{copy.city}</th><th>{copy.country}</th><th>{copy.primary}</th><th>{copy.verified}</th><th>{copy.status}</th><th>{copy.actions}</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={`${row.id}-${row.version}`}>
        <td>{row.addressType}</td><td>{row.label ?? "—"}</td><td>{row.city}</td><td>{row.countryCode}</td>
        <td>{row.primaryAddress ? "✓" : "—"}</td><td>{row.verified ? "✓" : "—"}</td><td>{row.status}</td>
        <td><div className={styles.rowActions}>
          <button type="button" onClick={() => onEdit(row)} disabled={busy || row.status === "ARCHIVED"}>{copy.edit}</button>
          {!row.primaryAddress && row.status === "ACTIVE" ? <button type="button" onClick={() => onCommand(row, "primary")} disabled={busy}>{copy.makePrimary}</button> : null}
          {row.status !== "ARCHIVED" ? <button type="button" onClick={() => window.confirm(copy.confirmArchive) && onCommand(row, "archive")} disabled={busy}>{copy.archive}</button>
            : <button type="button" onClick={() => onCommand(row, "reactivate")} disabled={busy}>{copy.reactivate}</button>}
        </div></td>
      </tr>)}</tbody>
    </table></div>}
  </section>;
}

function CommunicationTable({ rows, copy, busy, onEdit, onCommand, onVerify }: {
  rows: CommunicationMethod[]; copy: Copy; busy: boolean;
  onEdit: (row: CommunicationMethod) => void;
  onCommand: (row: CommunicationMethod, command: "preferred" | "archive" | "reactivate") => void;
  onVerify: (row: CommunicationMethod, status: VerificationStatus) => void;
}) {
  return <section className={styles.listCard} aria-labelledby="crm-communication-list">
    <h2 id="crm-communication-list">{copy.communications}</h2>
    {rows.length === 0 ? <CrmEmpty title={copy.noCommunications} hint="" /> : <div className={styles.tableWrap}><table>
      <thead><tr><th>{copy.type}</th><th>{copy.value}</th><th>{copy.label}</th><th>{copy.privacy}</th><th>{copy.preferred}</th><th>{copy.verification}</th><th>{copy.status}</th><th>{copy.actions}</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={`${row.id}-${row.version}`}>
        <td>{row.methodType}</td><td dir="ltr">{row.displayValue}</td><td>{row.label ?? "—"}</td><td>{row.privacyClassification}</td>
        <td>{row.preferred ? "✓" : "—"}</td><td>{row.verificationStatus}</td><td>{row.status}</td>
        <td><div className={styles.rowActions}>
          <button type="button" onClick={() => onEdit(row)} disabled={busy || row.status === "ARCHIVED"}>{copy.edit}</button>
          {!row.preferred && row.status === "ACTIVE" ? <button type="button" onClick={() => onCommand(row, "preferred")} disabled={busy}>{copy.makePreferred}</button> : null}
          {row.verificationStatus === "UNVERIFIED" ? <button type="button" onClick={() => onVerify(row, "PENDING")} disabled={busy}>{copy.requestVerification}</button> : null}
          {row.verificationStatus === "PENDING" ? <button type="button" onClick={() => onVerify(row, "VERIFIED")} disabled={busy}>{copy.verify}</button> : null}
          {row.status !== "ARCHIVED" ? <button type="button" onClick={() => window.confirm(copy.confirmArchive) && onCommand(row, "archive")} disabled={busy}>{copy.archive}</button>
            : <button type="button" onClick={() => onCommand(row, "reactivate")} disabled={busy}>{copy.reactivate}</button>}
        </div></td>
      </tr>)}</tbody>
    </table></div>}
  </section>;
}

function extensionValue(raw: string | null | undefined, key: string): string {
  if (!raw) return "";
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    return typeof value[key] === "string" ? value[key] : value[key] == null ? "" : String(value[key]);
  } catch {
    return "";
  }
}
