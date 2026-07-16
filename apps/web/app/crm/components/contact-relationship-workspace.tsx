"use client";

import Link from "next/link";
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  contactRelationshipApi,
  type ContactProfile,
  type ContactRelationship,
  type OwnershipHistory,
  type RelationshipCommand,
  type RelationshipHistory,
  type RelationshipRole,
  type RelationshipRoleCode,
} from "@/lib/api/contact-relationships";
import type { CrmAccount } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { CrmEmpty } from "./crm-empty";
import { CrmLoading } from "./crm-loading";
import styles from "../crm.module.css";

interface Props {
  contactId?: string;
  accountId?: string;
  accounts?: CrmAccount[];
}

const COPY = {
  en: {
    profile: "Person profile",
    legalName: "Legal name",
    preferredName: "Preferred name",
    firstName: "First name",
    middleName: "Middle name",
    lastName: "Last name",
    email: "Email",
    phone: "Phone",
    locale: "Preferred locale",
    timeZone: "Timezone",
    pronouns: "Pronouns",
    owner: "Owner user ID",
    source: "Source",
    ownerReason: "Owner change reason",
    saveProfile: "Save profile",
    addTitle: "Add account relationship",
    relationships: "Relationships",
    account: "Account",
    person: "Person",
    role: "Role",
    primary: "Primary",
    status: "Status",
    validFrom: "Valid from",
    validTo: "Valid to",
    title: "Job title",
    department: "Department",
    authority: "Decision authority",
    add: "Add relationship",
    edit: "Edit",
    save: "Save",
    cancel: "Cancel",
    setPrimary: "Set primary",
    activate: "Activate",
    deactivate: "Deactivate",
    archive: "Archive",
    reactivate: "Reactivate",
    history: "History",
    hideHistory: "Hide history",
    noRelationships: "No account relationships exist.",
    noHistory: "No relationship history exists.",
    relationshipHistory: "Relationship history",
    ownershipHistory: "Ownership history",
    noOwnership: "No ownership changes exist.",
    changedAt: "Changed at",
    changedBy: "Changed by",
    previousOwner: "Previous owner",
    newOwner: "New owner",
    reason: "Reason",
    actions: "Actions",
    created: "Relationship created.",
    updated: "Relationship updated.",
    profileUpdated: "Person profile updated.",
    commandUpdated: "Relationship lifecycle updated.",
    confirmDeactivate: "Deactivate this relationship?",
    confirmArchive: "Archive this relationship while preserving its history?",
    selectAccount: "Select an account.",
    selectRole: "Select a role.",
    standardRoles: "Standard roles",
    customRoles: "Custom roles",
    event: "Event",
    version: "Version",
  },
  ar: {
    profile: "ملف الشخص",
    legalName: "الاسم القانوني أو الكامل",
    preferredName: "الاسم المفضل",
    firstName: "الاسم الأول",
    middleName: "الاسم الأوسط",
    lastName: "اسم العائلة",
    email: "البريد الإلكتروني",
    phone: "الهاتف",
    locale: "اللغة المفضلة",
    timeZone: "المنطقة الزمنية",
    pronouns: "الضمائر",
    owner: "معرف المالك",
    source: "المصدر",
    ownerReason: "سبب تغيير المالك",
    saveProfile: "حفظ ملف الشخص",
    addTitle: "إضافة علاقة بحساب",
    relationships: "العلاقات",
    account: "الحساب",
    person: "الشخص",
    role: "الدور",
    primary: "رئيسية",
    status: "الحالة",
    validFrom: "صالحة من",
    validTo: "صالحة حتى",
    title: "المسمى الوظيفي",
    department: "القسم",
    authority: "صلاحية القرار",
    add: "إضافة العلاقة",
    edit: "تعديل",
    save: "حفظ",
    cancel: "إلغاء",
    setPrimary: "تعيين رئيسية",
    activate: "تفعيل",
    deactivate: "تعطيل",
    archive: "أرشفة",
    reactivate: "إعادة تفعيل",
    history: "السجل",
    hideHistory: "إخفاء السجل",
    noRelationships: "لا توجد علاقات حسابات.",
    noHistory: "لا يوجد سجل للعلاقة.",
    relationshipHistory: "سجل العلاقة",
    ownershipHistory: "سجل الملكية",
    noOwnership: "لا توجد تغييرات ملكية.",
    changedAt: "وقت التغيير",
    changedBy: "غيّرها",
    previousOwner: "المالك السابق",
    newOwner: "المالك الجديد",
    reason: "السبب",
    actions: "الإجراءات",
    created: "تم إنشاء العلاقة.",
    updated: "تم تحديث العلاقة.",
    profileUpdated: "تم تحديث ملف الشخص.",
    commandUpdated: "تم تحديث دورة حياة العلاقة.",
    confirmDeactivate: "هل تريد تعطيل هذه العلاقة؟",
    confirmArchive: "هل تريد أرشفة العلاقة مع الاحتفاظ بسجلها؟",
    selectAccount: "اختر حسابًا.",
    selectRole: "اختر دورًا.",
    standardRoles: "الأدوار الأساسية",
    customRoles: "الأدوار المخصصة",
    event: "الحدث",
    version: "الإصدار",
  },
} as const;

type Copy = { [Key in keyof typeof COPY.en]: string };
type Locale = "ar" | "en";

const AUTHORITIES = [
  "NONE",
  "INFLUENCER",
  "RECOMMENDER",
  "DECIDER",
  "FINAL_APPROVER",
] as const;

function stringValue(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function optionalValue(form: FormData, name: string): string | null {
  return stringValue(form, name) || null;
}

function roleName(role: RelationshipRole, locale: Locale): string {
  return locale === "ar" ? role.nameAr : role.nameEn;
}

function selectedRole(
  relationship: ContactRelationship,
): string {
  if (relationship.roleCode === "OTHER" && relationship.customRoleId) {
    return `custom:${relationship.customRoleId}`;
  }
  return `standard:${relationship.roleCode}`;
}

function relationshipRoleName(
  relationship: ContactRelationship,
  roles: RelationshipRole[],
  locale: Locale,
): string {
  if (relationship.roleCode === "OTHER") {
    return locale === "ar"
      ? relationship.customRoleNameAr ?? "OTHER"
      : relationship.customRoleNameEn ?? "OTHER";
  }
  const role = roles.find((candidate) =>
    candidate.standard && candidate.code === relationship.roleCode);
  return role ? roleName(role, locale) : relationship.roleCode;
}

function parseRole(value: string): {
  roleCode: RelationshipRoleCode;
  customRoleId: string | null;
} {
  const [kind, selected] = value.split(":", 2);
  if (!selected) throw new Error("Relationship role is required.");
  return kind === "custom"
    ? { roleCode: "OTHER", customRoleId: selected }
    : { roleCode: selected as RelationshipRoleCode, customRoleId: null };
}

export function ContactRelationshipWorkspace({
  contactId,
  accountId,
  accounts = [],
}: Props) {
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
      const pagePromise = contactId
        ? contactRelationshipApi.byContact(contactId)
        : contactRelationshipApi.byAccount(accountId as string);
      const [page, availableRoles, sensitiveProfile, ownershipRows] = await Promise.all([
        pagePromise,
        contactRelationshipApi.roles(),
        contactId
          ? contactRelationshipApi.profile(contactId).catch(() => null)
          : Promise.resolve(null),
        contactId
          ? contactRelationshipApi.ownershipHistory(contactId).catch(() => [] as OwnershipHistory[])
          : Promise.resolve([] as OwnershipHistory[]),
      ]);
      setRelationships(page.data);
      setRoles(availableRoles);
      setProfile(sensitiveProfile);
      setOwnership(ownershipRows);
    } catch (reason) {
      setRelationships([]);
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }, [accountId, contactId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const availableAccounts = useMemo(
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

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!contactId || !profile) return;
    const form = new FormData(event.currentTarget);
    await mutate(
      () => contactRelationshipApi.updateProfile(contactId, {
        expectedVersion: profile.version,
        legalName: optionalValue(form, "legalName"),
        preferredName: optionalValue(form, "preferredName"),
        givenName: optionalValue(form, "givenName"),
        middleName: optionalValue(form, "middleName"),
        familyName: optionalValue(form, "familyName"),
        primaryEmail: optionalValue(form, "primaryEmail"),
        primaryPhone: optionalValue(form, "primaryPhone"),
        preferredLocale: optionalValue(form, "preferredLocale"),
        timeZone: optionalValue(form, "timeZone"),
        pronouns: optionalValue(form, "pronouns"),
        ownerUserId: optionalValue(form, "ownerUserId"),
        source: optionalValue(form, "source"),
        ownerChangeReason: optionalValue(form, "ownerChangeReason"),
      }),
      copy.profileUpdated,
    );
  }

  async function createRelationship(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!contactId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const selectedAccount = stringValue(form, "accountId");
    const selectedRoleValue = stringValue(form, "role");
    if (!selectedAccount) {
      setError(copy.selectAccount);
      return;
    }
    if (!selectedRoleValue) {
      setError(copy.selectRole);
      return;
    }
    const role = parseRole(selectedRoleValue);
    await mutate(
      () => contactRelationshipApi.create(contactId, {
        accountId: selectedAccount,
        roleCode: role.roleCode,
        customRoleId: role.customRoleId,
        primaryRelationship: form.get("primaryRelationship") === "on",
        validFrom: optionalValue(form, "validFrom"),
        validTo: optionalValue(form, "validTo"),
        jobTitle: optionalValue(form, "jobTitle"),
        department: optionalValue(form, "department"),
        decisionAuthority: stringValue(form, "decisionAuthority") || "NONE",
      }),
      copy.created,
    );
    formElement.reset();
  }

  async function updateRelationship(
    event: FormEvent<HTMLFormElement>,
    relationship: ContactRelationship,
  ) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const role = parseRole(stringValue(form, "role"));
    await mutate(
      () => contactRelationshipApi.update(relationship.id, {
        expectedVersion: relationship.version,
        roleCode: role.roleCode,
        customRoleId: role.customRoleId,
        validFrom: optionalValue(form, "validFrom"),
        validTo: optionalValue(form, "validTo"),
        jobTitle: optionalValue(form, "jobTitle"),
        department: optionalValue(form, "department"),
        decisionAuthority: stringValue(form, "decisionAuthority") || "NONE",
      }),
      copy.updated,
    );
  }

  async function runCommand(
    relationship: ContactRelationship,
    command: RelationshipCommand,
  ) {
    if (command === "DEACTIVATE" && !window.confirm(copy.confirmDeactivate)) return;
    if (command === "ARCHIVE" && !window.confirm(copy.confirmArchive)) return;
    await mutate(
      () => contactRelationshipApi.command(
        relationship.id,
        relationship.version,
        command,
      ),
      copy.commandUpdated,
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

  if (loading) return <CrmLoading rows={3} />;

  return (
    <>
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      {contactId && profile ? (
        <section className={styles.overviewSection} aria-labelledby="crm-person-profile">
          <h2 id="crm-person-profile" className={styles.overviewSectionTitle}>{copy.profile}</h2>
          <form className={styles.formCard} onSubmit={updateProfile} key={profile.version}>
            <label>{copy.legalName}<input name="legalName" defaultValue={profile.legalName ?? ""} disabled={busy} /></label>
            <label>{copy.preferredName}<input name="preferredName" defaultValue={profile.preferredName ?? ""} disabled={busy} /></label>
            <label>{copy.firstName}<input name="givenName" defaultValue={profile.givenName ?? ""} disabled={busy} /></label>
            <label>{copy.middleName}<input name="middleName" defaultValue={profile.middleName ?? ""} disabled={busy} /></label>
            <label>{copy.lastName}<input name="familyName" defaultValue={profile.familyName ?? ""} disabled={busy} /></label>
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
        <section className={styles.overviewSection} aria-labelledby="crm-add-relationship">
          <h2 id="crm-add-relationship" className={styles.overviewSectionTitle}>{copy.addTitle}</h2>
          <form className={styles.formCard} onSubmit={createRelationship}>
            <label>{copy.account}
              <select name="accountId" defaultValue="" required disabled={busy}>
                <option value="">—</option>
                {availableAccounts.map((account) => (
                  <option key={account.id} value={account.id}>{account.display_name}</option>
                ))}
              </select>
            </label>
            <RoleSelect copy={copy} locale={locale} roles={roles} disabled={busy} />
            <label>{copy.validFrom}<input type="date" name="validFrom" disabled={busy} /></label>
            <label>{copy.validTo}<input type="date" name="validTo" disabled={busy} /></label>
            <label>{copy.title}<input name="jobTitle" disabled={busy} /></label>
            <label>{copy.department}<input name="department" disabled={busy} /></label>
            <AuthoritySelect copy={copy} disabled={busy} />
            <label><input type="checkbox" name="primaryRelationship" disabled={busy} /> {copy.primary}</label>
            <button type="submit" disabled={busy}>{copy.add}</button>
          </form>
        </section>
      ) : null}

      <section className={styles.overviewSection} aria-labelledby="crm-relationships">
        <h2 id="crm-relationships" className={styles.overviewSectionTitle}>{copy.relationships}</h2>
        {relationships.length === 0 ? (
          <CrmEmpty title={copy.noRelationships} hint="" />
        ) : (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>{contactId ? copy.account : copy.person}</th>
                  <th>{copy.role}</th>
                  <th>{copy.status}</th>
                  <th>{copy.primary}</th>
                  <th>{copy.validFrom} / {copy.validTo}</th>
                  <th>{copy.title}</th>
                  <th>{copy.department}</th>
                  <th>{copy.actions}</th>
                </tr>
              </thead>
              <tbody>
                {relationships.map((relationship) => (
                  <RelationshipRows
                    key={`${relationship.id}-${relationship.version}`}
                    relationship={relationship}
                    contactMode={Boolean(contactId)}
                    roles={roles}
                    locale={locale}
                    copy={copy}
                    busy={busy}
                    editing={editingId === relationship.id}
                    expanded={expandedHistory === relationship.id}
                    history={history[relationship.id] ?? []}
                    onEdit={() => setEditingId(relationship.id)}
                    onCancel={() => setEditingId(null)}
                    onSubmit={(event) => void updateRelationship(event, relationship)}
                    onCommand={(command) => void runCommand(relationship, command)}
                    onHistory={() => void toggleHistory(relationship.id)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {contactId ? (
        <OwnershipSection ownership={ownership} locale={locale} copy={copy} />
      ) : null}
    </>
  );
}

function RoleSelect({
  copy,
  locale,
  roles,
  disabled,
  defaultValue = "",
}: {
  copy: Copy;
  locale: Locale;
  roles: RelationshipRole[];
  disabled: boolean;
  defaultValue?: string;
}) {
  return (
    <label>{copy.role}
      <select name="role" required defaultValue={defaultValue} disabled={disabled}>
        <option value="">—</option>
        <optgroup label={copy.standardRoles}>
          {roles.filter((role) => role.standard).map((role) => (
            <option key={`standard-${role.code}`} value={`standard:${role.code}`}>
              {roleName(role, locale)}
            </option>
          ))}
        </optgroup>
        <optgroup label={copy.customRoles}>
          {roles.filter((role) => !role.standard && role.id).map((role) => (
            <option key={role.id as string} value={`custom:${role.id}`}>
              {roleName(role, locale)}
            </option>
          ))}
        </optgroup>
      </select>
    </label>
  );
}

function AuthoritySelect({
  copy,
  disabled,
  defaultValue = "NONE",
}: {
  copy: Copy;
  disabled: boolean;
  defaultValue?: string;
}) {
  return (
    <label>{copy.authority}
      <select name="decisionAuthority" defaultValue={defaultValue} disabled={disabled}>
        {AUTHORITIES.map((authority) => (
          <option key={authority} value={authority}>{authority}</option>
        ))}
      </select>
    </label>
  );
}

function RelationshipRows({
  relationship,
  contactMode,
  roles,
  locale,
  copy,
  busy,
  editing,
  expanded,
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
  locale: Locale;
  copy: Copy;
  busy: boolean;
  editing: boolean;
  expanded: boolean;
  history: RelationshipHistory[];
  onEdit: () => void;
  onCancel: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCommand: (command: RelationshipCommand) => void;
  onHistory: () => void;
}) {
  const subject = contactMode
    ? relationship.accountDisplayName
    : relationship.contactDisplayName;
  const href = contactMode
    ? `/crm/accounts/${relationship.accountId}`
    : `/crm/contacts/${relationship.contactId}`;

  if (editing) {
    return (
      <tr>
        <td colSpan={8}>
          <form className={styles.formCard} onSubmit={onSubmit}>
            <strong>{subject}</strong>
            <RoleSelect
              copy={copy}
              locale={locale}
              roles={roles}
              disabled={busy}
              defaultValue={selectedRole(relationship)}
            />
            <label>{copy.validFrom}<input type="date" name="validFrom" defaultValue={relationship.validFrom ?? ""} disabled={busy} /></label>
            <label>{copy.validTo}<input type="date" name="validTo" defaultValue={relationship.validTo ?? ""} disabled={busy} /></label>
            <label>{copy.title}<input name="jobTitle" defaultValue={relationship.jobTitle ?? ""} disabled={busy} /></label>
            <label>{copy.department}<input name="department" defaultValue={relationship.department ?? ""} disabled={busy} /></label>
            <AuthoritySelect copy={copy} disabled={busy} defaultValue={relationship.decisionAuthority} />
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
        <td>{relationshipRoleName(relationship, roles, locale)}</td>
        <td><span className={styles.badge}>{relationship.status}</span></td>
        <td>{relationship.primaryRelationship ? "✓" : "—"}</td>
        <td>{relationship.validFrom ?? "…"} — {relationship.validTo ?? "…"}</td>
        <td>{relationship.jobTitle ?? "—"}</td>
        <td>{relationship.department ?? "—"}</td>
        <td>
          <div className={styles.rowActions}>
            <button type="button" onClick={onEdit} disabled={busy || relationship.status === "ARCHIVED"}>{copy.edit}</button>
            {!relationship.primaryRelationship && relationship.status === "ACTIVE" ? (
              <button type="button" onClick={() => onCommand("SET_PRIMARY")} disabled={busy}>{copy.setPrimary}</button>
            ) : null}
            {relationship.status === "ACTIVE" ? (
              <button type="button" onClick={() => onCommand("DEACTIVATE")} disabled={busy}>{copy.deactivate}</button>
            ) : null}
            {relationship.status === "INACTIVE" ? (
              <button type="button" onClick={() => onCommand("ACTIVATE")} disabled={busy}>{copy.activate}</button>
            ) : null}
            {relationship.status !== "ARCHIVED" ? (
              <button type="button" onClick={() => onCommand("ARCHIVE")} disabled={busy}>{copy.archive}</button>
            ) : (
              <button type="button" onClick={() => onCommand("REACTIVATE")} disabled={busy}>{copy.reactivate}</button>
            )}
            <button type="button" onClick={onHistory} disabled={busy}>
              {expanded ? copy.hideHistory : copy.history}
            </button>
          </div>
        </td>
      </tr>
      {expanded ? (
        <tr>
          <td colSpan={8}>
            <h3 className={styles.sectionHeading}>{copy.relationshipHistory}</h3>
            {history.length === 0 ? (
              <p className={styles.notice}>{copy.noHistory}</p>
            ) : (
              <div className={styles.tableWrap}>
                <table>
                  <thead><tr><th>{copy.event}</th><th>{copy.version}</th><th>{copy.changedBy}</th><th>{copy.changedAt}</th></tr></thead>
                  <tbody>{history.map((event) => (
                    <tr key={event.id}>
                      <td>{event.eventType}</td>
                      <td>{event.newVersion}</td>
                      <td>{event.changedBy}</td>
                      <td>{new Date(event.changedAt).toLocaleString(locale)}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
          </td>
        </tr>
      ) : null}
    </>
  );
}

function OwnershipSection({
  ownership,
  locale,
  copy,
}: {
  ownership: OwnershipHistory[];
  locale: Locale;
  copy: Copy;
}) {
  return (
    <section className={styles.overviewSection} aria-labelledby="crm-ownership-history">
      <h2 id="crm-ownership-history" className={styles.overviewSectionTitle}>{copy.ownershipHistory}</h2>
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
  );
}
