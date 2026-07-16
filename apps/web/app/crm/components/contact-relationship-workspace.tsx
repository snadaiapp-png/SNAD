"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  contactRelationshipApi,
  type ContactProfile,
  type ContactRelationship,
  type RelationshipHistory,
  type RelationshipRole,
  type RelationshipRoleCode,
  type OwnershipHistory,
} from "@/lib/api/contact-relationships";
import type { CrmAccount } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { CrmEmpty } from "./crm-empty";
import { CrmLoading } from "./crm-loading";
import styles from "../crm.module.css";

interface ContactRelationshipWorkspaceProps {
  contactId?: string;
  accountId?: string;
  accounts?: CrmAccount[];
}

type Copy = typeof COPY.en;

const COPY = {
  en: {
    title: "Account relationships",
    profile: "Person profile",
    editProfile: "Edit profile",
    legalName: "Legal name",
    preferredName: "Preferred name",
    givenName: "First name",
    middleName: "Middle name",
    familyName: "Last name",
    email: "Email",
    phone: "Phone",
    locale: "Preferred locale",
    timeZone: "Timezone",
    pronouns: "Pronouns",
    owner: "Owner user ID",
    source: "Source",
    ownerReason: "Owner change reason",
    saveProfile: "Save profile",
    createTitle: "Add account relationship",
    account: "Account",
    role: "Role",
    primary: "Primary relationship",
    validFrom: "Valid from",
    validTo: "Valid to",
    jobTitle: "Job title in account",
    department: "Department in account",
    authority: "Decision authority",
    add: "Add relationship",
    listTitle: "Relationships",
    person: "Person",
    status: "Status",
    dates: "Validity",
    actions: "Actions",
    setPrimary: "Set primary",
    deactivate: "Deactivate",
    activate: "Activate",
    archive: "Archive",
    reactivate: "Reactivate",
    edit: "Edit",
    save: "Save",
    cancel: "Cancel",
    history: "History",
    hideHistory: "Hide history",
    empty: "No account relationships exist.",
    ownershipHistory: "Ownership history",
    noOwnership: "No ownership changes recorded.",
    changedAt: "Changed at",
    changedBy: "Changed by",
    previousOwner: "Previous owner",
    newOwner: "New owner",
    reason: "Reason",
    created: "Relationship created.",
    updated: "Relationship updated.",
    profileUpdated: "Person profile updated.",
    commandDone: "Relationship lifecycle updated.",
    confirmDeactivate: "Deactivate this relationship?",
    confirmArchive: "Archive this relationship? This action preserves history.",
    requiredAccount: "Select an account.",
    requiredRole: "Select a relationship role.",
    customRole: "Custom role",
    standardRole: "Standard role",
    loading: "Loading relationships",
    relationshipHistory: "Relationship history",
    event: "Event",
    version: "Version",
    noHistory: "No relationship history recorded.",
  },
  ar: {
    title: "علاقات الحسابات",
    profile: "ملف الشخص",
    editProfile: "تعديل ملف الشخص",
    legalName: "الاسم القانوني أو الكامل",
    preferredName: "الاسم المفضل",
    givenName: "الاسم الأول",
    middleName: "الاسم الأوسط",
    familyName: "اسم العائلة",
    email: "البريد الإلكتروني",
    phone: "الهاتف",
    locale: "اللغة المفضلة",
    timeZone: "المنطقة الزمنية",
    pronouns: "الضمائر",
    owner: "معرف المالك",
    source: "المصدر",
    ownerReason: "سبب تغيير المالك",
    saveProfile: "حفظ ملف الشخص",
    createTitle: "إضافة علاقة بحساب",
    account: "الحساب",
    role: "الدور",
    primary: "العلاقة الرئيسية",
    validFrom: "صالحة من",
    validTo: "صالحة حتى",
    jobTitle: "المسمى الوظيفي في الحساب",
    department: "القسم في الحساب",
    authority: "صلاحية القرار",
    add: "إضافة العلاقة",
    listTitle: "العلاقات",
    person: "الشخص",
    status: "الحالة",
    dates: "مدة الصلاحية",
    actions: "الإجراءات",
    setPrimary: "تعيين رئيسية",
    deactivate: "تعطيل",
    activate: "تفعيل",
    archive: "أرشفة",
    reactivate: "إعادة تفعيل",
    edit: "تعديل",
    save: "حفظ",
    cancel: "إلغاء",
    history: "السجل",
    hideHistory: "إخفاء السجل",
    empty: "لا توجد علاقات حسابات.",
    ownershipHistory: "سجل الملكية",
    noOwnership: "لا توجد تغييرات ملكية مسجلة.",
    changedAt: "وقت التغيير",
    changedBy: "غيّرها",
    previousOwner: "المالك السابق",
    newOwner: "المالك الجديد",
    reason: "السبب",
    created: "تم إنشاء العلاقة.",
    updated: "تم تحديث العلاقة.",
    profileUpdated: "تم تحديث ملف الشخص.",
    commandDone: "تم تحديث دورة حياة العلاقة.",
    confirmDeactivate: "هل تريد تعطيل هذه العلاقة؟",
    confirmArchive: "هل تريد أرشفة هذه العلاقة؟ سيظل السجل محفوظًا.",
    requiredAccount: "اختر حسابًا.",
    requiredRole: "اختر دور العلاقة.",
    customRole: "دور مخصص",
    standardRole: "دور أساسي",
    loading: "جارٍ تحميل العلاقات",
    relationshipHistory: "سجل العلاقة",
    event: "الحدث",
    version: "الإصدار",
    noHistory: "لا يوجد سجل لهذه العلاقة.",
  },
} as const;

const DECISION_AUTHORITIES = [
  "NONE",
  "INFLUENCER",
  "RECOMMENDER",
  "DECIDER",
  "FINAL_APPROVER",
] as const;

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function optional(form: FormData, name: string): string | null {
  const value = text(form, name);
  return value || null;
}

function displayRole(role: RelationshipRole, locale: "ar" | "en"): string {
  return locale === "ar" ? role.nameAr : role.nameEn;
}

function relationshipRoleLabel(
  relationship: ContactRelationship,
  roles: RelationshipRole[],
  locale: "ar" | "en",
): string {
  if (relationship.roleCode !== "OTHER") {
    const standard = roles.find((role) => role.standard && role.code === relationship.roleCode);
    return standard ? displayRole(standard, locale) : relationship.roleCode;
  }
  return locale === "ar"
    ? relationship.customRoleNameAr ?? relationship.roleCode
    : relationship.customRoleNameEn ?? relationship.roleCode;
}

function dateRange(relationship: ContactRelationship): string {
  if (!relationship.validFrom && !relationship.validTo) return "—";
  return `${relationship.validFrom ?? "…"} — ${relationship.validTo ?? "…"}`;
}

export function ContactRelationshipWorkspace({
  contactId,
  accountId,
  accounts = [],
}: ContactRelationshipWorkspaceProps) {
  const { locale } = useI18n();
  const copy: Copy = COPY[locale];
  const [profile, setProfile] = useState<ContactProfile | null>(null);
  const [relationships, setRelationships] = useState<ContactRelationship[]>([]);
  const [roles, setRoles] = useState<RelationshipRole[]>([]);
  const [ownership, setOwnership] = useState<OwnershipHistory[]>([]);
  const [history, setHistory] = useState<Record<string, RelationshipHistory[]>>({});
  const [expandedHistory, setExpandedHistory] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    if (!contactId && !accountId) return;
    setLoading(true);
    setError("");
    try {
      const relationshipPromise = contactId
        ? contactRelationshipApi.byContact(contactId)
        : contactRelationshipApi.byAccount(accountId as string);
      const [relationshipPage, nextRoles, nextProfile, nextOwnership] = await Promise.all([
        relationshipPromise,
        contactRelationshipApi.roles(),
        contactId ? contactRelationshipApi.profile(contactId) : Promise.resolve(null),
        contactId
          ? contactRelationshipApi.ownershipHistory(contactId).catch(() => [] as OwnershipHistory[])
          : Promise.resolve([] as OwnershipHistory[]),
      ]);
      setRelationships(relationshipPage.data);
      setRoles(nextRoles);
      setProfile(nextProfile);
      setOwnership(nextOwnership);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setRelationships([]);
    } finally {
      setLoading(false);
    }
  }, [accountId, contactId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.lifecycle_status !== "ARCHIVED"),
    [accounts],
  );

  async function mutate(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(success);
      setEditingId(null);
      await load();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!contactId) return;
    const element = event.currentTarget;
    const form = new FormData(element);
    const selectedAccount = text(form, "accountId");
    const selectedRole = text(form, "role");
    if (!selectedAccount) {
      setError(copy.requiredAccount);
      return;
    }
    if (!selectedRole) {
      setError(copy.requiredRole);
      return;
    }
    const [kind, roleValue] = selectedRole.split(":", 2);
    const roleCode: RelationshipRoleCode = kind === "custom" ? "OTHER" : roleValue as RelationshipRoleCode;
    await mutate(
      () => contactRelationshipApi.create(contactId, {
        accountId: selectedAccount,
        roleCode,
        customRoleId: kind === "custom" ? roleValue : null,
        primaryRelationship: form.get("primaryRelationship") === "on",
        validFrom: optional(form, "validFrom"),
        validTo: optional(form, "validTo"),
        jobTitle: optional(form, "jobTitle"),
        department: optional(form, "department"),
        decisionAuthority: text(form, "decisionAuthority") || "NONE",
      }),
      copy.created,
    );
    element.reset();
  }

  async function handleProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!contactId || !profile) return;
    const form = new FormData(event.currentTarget);
    await mutate(
      () => contactRelationshipApi.updateProfile(contactId, {
        expectedVersion: profile.version,
        legalName: optional(form, "legalName"),
        preferredName: optional(form, "preferredName"),
        givenName: optional(form, "givenName"),
        middleName: optional(form, "middleName"),
        familyName: optional(form, "familyName"),
        primaryEmail: optional(form, "primaryEmail"),
        primaryPhone: optional(form, "primaryPhone"),
        preferredLocale: optional(form, "preferredLocale"),
        timeZone: optional(form, "timeZone"),
        pronouns: optional(form, "pronouns"),
        ownerUserId: optional(form, "ownerUserId"),
        source: optional(form, "source"),
        ownerChangeReason: optional(form, "ownerChangeReason"),
      }),
      copy.profileUpdated,
    );
  }

  async function handleRelationshipEdit(
    event: FormEvent<HTMLFormElement>,
    relationship: ContactRelationship,
  ) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const selectedRole = text(form, "role");
    const [kind, roleValue] = selectedRole.split(":", 2);
    await mutate(
      () => contactRelationshipApi.update(relationship.id, {
        expectedVersion: relationship.version,
        roleCode: kind === "custom" ? "OTHER" : roleValue as RelationshipRoleCode,
        customRoleId: kind === "custom" ? roleValue : null,
        validFrom: optional(form, "validFrom"),
        validTo: optional(form, "validTo"),
        jobTitle: optional(form, "jobTitle"),
        department: optional(form, "department"),
        decisionAuthority: text(form, "decisionAuthority") || "NONE",
      }),
      copy.updated,
    );
  }

  async function runCommand(
    relationship: ContactRelationship,
    action: "SET_PRIMARY" | "ACTIVATE" | "DEACTIVATE" | "ARCHIVE" | "REACTIVATE",
  ) {
    if (action === "DEACTIVATE" && !window.confirm(copy.confirmDeactivate)) return;
    if (action === "ARCHIVE" && !window.confirm(copy.confirmArchive)) return;
    await mutate(
      () => contactRelationshipApi.command(relationship.id, relationship.version, action),
      copy.commandDone,
    );
  }

  async function toggleHistory(relationshipId: string) {
    if (expandedHistory === relationshipId) {
      setExpandedHistory(null);
      return;
    }
    setExpandedHistory(relationshipId);
    if (history[relationshipId]) return;
    setBusy(true);
    try {
      const events = await contactRelationshipApi.history(relationshipId);
      setHistory((current) => ({ ...current, [relationshipId]: events }));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <section className={styles.overviewSection} aria-label={copy.loading}>
        <CrmLoading rows={3} />
      </section>
    );
  }

  return (
    <>
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      {contactId && profile ? (
        <section className={styles.overviewSection} aria-labelledby="crm-person-profile-title">
          <h2 id="crm-person-profile-title" className={styles.overviewSectionTitle}>{copy.profile}</h2>
          <form className={styles.formCard} onSubmit={handleProfile} key={`${profile.id}-${profile.version}`}>
            <h3 className={styles.sectionHeading}>{copy.editProfile}</h3>
            <label>{copy.legalName}<input name="legalName" defaultValue={profile.legalName ?? ""} disabled={busy} /></label>
            <label>{copy.preferredName}<input name="preferredName" defaultValue={profile.preferredName ?? ""} disabled={busy} /></label>
            <label>{copy.givenName}<input name="givenName" defaultValue={profile.givenName ?? ""} disabled={busy} /></label>
            <label>{copy.middleName}<input name="middleName" defaultValue={profile.middleName ?? ""} disabled={busy} /></label>
            <label>{copy.familyName}<input name="familyName" defaultValue={profile.familyName ?? ""} disabled={busy} /></label>
            <label>{copy.email}<input type="email" name="primaryEmail" defaultValue={profile.primaryEmail ?? ""} disabled={busy} /></label>
            <label>{copy.phone}<input name="primaryPhone" defaultValue={profile.primaryPhone ?? ""} disabled={busy} /></label>
            <label>{copy.locale}<input name="preferredLocale" defaultValue={profile.preferredLocale ?? "ar-SA"} disabled={busy} /></label>
            <label>{copy.timeZone}<input name="timeZone" defaultValue={profile.timeZone ?? "Asia/Riyadh"} disabled={busy} /></label>
            <label>{copy.pronouns}<input name="pronouns" defaultValue={profile.pronouns ?? ""} disabled={busy} /></label>
            <label>{copy.owner}<input name="ownerUserId" defaultValue={profile.ownerUserId ?? ""} disabled={busy} /></label>
            <label>{copy.source}<input name="source" defaultValue={profile.source ?? ""} disabled={busy} /></label>
            <label>{copy.ownerReason}<input name="ownerChangeReason" disabled={busy} /></label>
            <button type="submit" disabled={busy}>{copy.saveProfile}</button>
          </form>
        </section>
      ) : null}

      {contactId ? (
        <section className={styles.overviewSection} aria-labelledby="crm-add-relationship-title">
          <h2 id="crm-add-relationship-title" className={styles.overviewSectionTitle}>{copy.createTitle}</h2>
          <form className={styles.formCard} onSubmit={handleCreate}>
            <label>
              {copy.account}
              <select name="accountId" defaultValue="" required disabled={busy}>
                <option value="">—</option>
                {activeAccounts.map((account) => (
                  <option key={account.id} value={account.id}>{account.display_name}</option>
                ))}
              </select>
            </label>
            <RoleSelect roles={roles} locale={locale} copy={copy} disabled={busy} />
            <label>{copy.validFrom}<input type="date" name="validFrom" disabled={busy} /></label>
            <label>{copy.validTo}<input type="date" name="validTo" disabled={busy} /></label>
            <label>{copy.jobTitle}<input name="jobTitle" disabled={busy} /></label>
            <label>{copy.department}<input name="department" disabled={busy} /></label>
            <label>
              {copy.authority}
              <select name="decisionAuthority" defaultValue="NONE" disabled={busy}>
                {DECISION_AUTHORITIES.map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </label>
            <label><input type="checkbox" name="primaryRelationship" disabled={busy} /> {copy.primary}</label>
            <button type="submit" disabled={busy}>{copy.add}</button>
          </form>
        </section>
      ) : null}

      <section className={styles.overviewSection} aria-labelledby="crm-relationship-list-title">
        <h2 id="crm-relationship-list-title" className={styles.overviewSectionTitle}>{copy.listTitle}</h2>
        {relationships.length === 0 ? (
          <CrmEmpty title={copy.empty} hint="" />
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{contactId ? copy.account : copy.person}</th>
                  <th>{copy.role}</th>
                  <th>{copy.status}</th>
                  <th>{copy.primary}</th>
                  <th>{copy.dates}</th>
                  <th>{copy.jobTitle}</th>
                  <th>{copy.department}</th>
                  <th>{copy.actions}</th>
                </tr>
              </thead>
              <tbody>
                {relationships.map((relationship) => (
                  <RelationshipRow
                    key={`${relationship.id}-${relationship.version}`}
                    relationship={relationship}
                    contactMode={Boolean(contactId)}
                    roles={roles}
                    locale={locale}
                    copy={copy}
                    busy={busy}
                    editing={editingId === relationship.id}
                    historyExpanded={expandedHistory === relationship.id}
                    history={history[relationship.id] ?? []}
                    onEdit={() => setEditingId(relationship.id)}
                    onCancel={() => setEditingId(null)}
                    onSubmit={(event) => void handleRelationshipEdit(event, relationship)}
                    onCommand={(action) => void runCommand(relationship, action)}
                    onHistory={() => void toggleHistory(relationship.id)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {contactId ? (
        <section className={styles.overviewSection} aria-labelledby="crm-ownership-history-title">
          <h2 id="crm-ownership-history-title" className={styles.overviewSectionTitle}>{copy.ownershipHistory}</h2>
          {ownership.length === 0 ? (
            <p className={styles.notice}>{copy.noOwnership}</p>
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead><tr><th>{copy.previousOwner}</th><th>{copy.newOwner}</th><th>{copy.changedBy}</th><th>{copy.changedAt}</th><th>{copy.reason}</th></tr></thead>
                <tbody>{ownership.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.previousOwnerUserId ?? "—"}</td>
                    <td>{entry.newOwnerUserId ?? "—"}</td>
                    <td>{entry.changedBy}</td>
                    <td>{new Date(entry.changedAt).toLocaleString(locale)}</td>
                    <td>{entry.reason ?? "—"}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </>
  );
}

function RoleSelect({
  roles,
  locale,
  copy,
  disabled,
  defaultRelationship,
}: {
  roles: RelationshipRole[];
  locale: "ar" | "en";
  copy: Copy;
  disabled: boolean;
  defaultRelationship?: ContactRelationship;
}) {
  const defaultValue = defaultRelationship
    ? defaultRelationship.roleCode === "OTHER" && defaultRelationship.customRoleId
      ? `custom:${defaultRelationship.customRoleId}`
      : `standard:${defaultRelationship.roleCode}`
    : "";
  return (
    <label>
      {copy.role}
      <select name="role" defaultValue={defaultValue} required disabled={disabled}>
        <option value="">—</option>
        <optgroup label={copy.standardRole}>
          {roles.filter((role) => role.standard).map((role) => (
            <option key={`standard-${role.code}`} value={`standard:${role.code}`}>
              {displayRole(role, locale)}
            </option>
          ))}
        </optgroup>
        <optgroup label={copy.customRole}>
          {roles.filter((role) => !role.standard && role.id).map((role) => (
            <option key={role.id as string} value={`custom:${role.id}`}>
              {displayRole(role, locale)}
            </option>
          ))}
        </optgroup>
      </select>
    </label>
  );
}

function RelationshipRow({
  relationship,
  contactMode,
  roles,
  locale,
  copy,
  busy,
  editing,
  historyExpanded,
  history,
  onEdit,
  onCancel,
  onSubmit,
  onCommand,
  onHistory,
}: {
  relationship: ContactRelationship;
  contactMode: boolean;
  roles: RelationshipRole[];
  locale: "ar" | "en";
  copy: Copy;
  busy: boolean;
  editing: boolean;
  historyExpanded: boolean;
  history: RelationshipHistory[];
  onEdit: () => void;
  onCancel: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCommand: (action: "SET_PRIMARY" | "ACTIVATE" | "DEACTIVATE" | "ARCHIVE" | "REACTIVATE") => void;
  onHistory: () => void;
}) {
  const subject = contactMode ? relationship.accountDisplayName : relationship.contactDisplayName;
  const href = contactMode
    ? `/crm/accounts/${relationship.accountId}`
    : `/crm/contacts/${relationship.contactId}`;
  if (editing) {
    return (
      <tr>
        <td colSpan={8}>
          <form className={styles.formCard} onSubmit={onSubmit}>
            <strong>{subject}</strong>
            <RoleSelect roles={roles} locale={locale} copy={copy} disabled={busy} defaultRelationship={relationship} />
            <label>{copy.validFrom}<input type="date" name="validFrom" defaultValue={relationship.validFrom ?? ""} disabled={busy} /></label>
            <label>{copy.validTo}<input type="date" name="validTo" defaultValue={relationship.validTo ?? ""} disabled={busy} /></label>
            <label>{copy.jobTitle}<input name="jobTitle" defaultValue={relationship.jobTitle ?? ""} disabled={busy} /></label>
            <label>{copy.department}<input name="department" defaultValue={relationship.department ?? ""} disabled={busy} /></label>
            <label>{copy.authority}<select name="decisionAuthority" defaultValue={relationship.decisionAuthority} disabled={busy}>{DECISION_AUTHORITIES.map((value) => <option key={value}>{value}</option>)}</select></label>
            <div className={styles.rowActions}>
              <button type="submit" disabled={busy}>{copy.save}</button>
              <button type="button" onClick={onCancel} disabled={busy}>{copy.cancel}</button>
            </div>
          </form>
        </td>
      </tr>
    );
  }
  return (
    <>
      <tr>
        <td><Link href={href}>{subject}</Link></td>
        <td>{relationshipRoleLabel(relationship, roles, locale)}</td>
        <td><span className={styles.badge}>{relationship.status}</span></td>
        <td>{relationship.primaryRelationship ? "✓" : "—"}</td>
        <td>{dateRange(relationship)}</td>
        <td>{relationship.jobTitle ?? "—"}</td>
        <td>{relationship.department ?? "—"}</td>
        <td>
          <div className={styles.rowActions}>
            <button type="button" onClick={onEdit} disabled={busy || relationship.status === "ARCHIVED"}>{copy.edit}</button>
            {!relationship.primaryRelationship && relationship.status === "ACTIVE" ? <button type="button" onClick={() => onCommand("SET_PRIMARY")} disabled={busy}>{copy.setPrimary}</button> : null}
            {relationship.status === "ACTIVE" ? <button type="button" onClick={() => onCommand("DEACTIVATE")} disabled={busy}>{copy.deactivate}</button> : null}
            {relationship.status === "INACTIVE" ? <button type="button" onClick={() => onCommand("ACTIVATE")} disabled={busy}>{copy.activate}</button> : null}
            {relationship.status !== "ARCHIVED" ? <button type="button" onClick={() => onCommand("ARCHIVE")} disabled={busy}>{copy.archive}</button> : null}
            {relationship.status === "ARCHIVED" ? <button type="button" onClick={() => onCommand("REACTIVATE")} disabled={busy}>{copy.reactivate}</button> : null}
            <button type="button" onClick={onHistory} disabled={busy}>{historyExpanded ? copy.hideHistory : copy.history}</button>
          </div>
        </td>
      </tr>
      {historyExpanded ? (
        <tr>
          <td colSpan={8}>
            <section aria-label={copy.relationshipHistory}>
              <h3 className={styles.sectionHeading}>{copy.relationshipHistory}</h3>
              {history.length === 0 ? <p className={styles.notice}>{copy.noHistory}</p> : (
                <div className={styles.tableWrap}>
                  <table>
                    <thead><tr><th>{copy.event}</th><th>{copy.version}</th><th>{copy.changedBy}</th><th>{copy.changedAt}</th></tr></thead>
                    <tbody>{history.map((event) => (
                      <tr key={event.id}><td>{event.eventType}</td><td>{event.newVersion}</td><td>{event.changedBy}</td><td>{new Date(event.changedAt).toLocaleString(locale)}</td></tr>
                    ))}</tbody>
                  </table>
                </div>
              )}
            </section>
          </td>
        </tr>
      ) : null}
    </>
  );
}
