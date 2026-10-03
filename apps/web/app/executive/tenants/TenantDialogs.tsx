"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import { Modal } from "@/components/sds/Modal";
import { ScpError } from "../_components/ScpStates";
import styles from "../scp.module.css";
import type { TenantDialog } from "./tenant-dialog-validation";

/**
 * Mutation dialogs for the executive tenants surface (create / edit / status).
 *
 * This component is loaded via `next/dynamic` only after an operator opens a
 * dialog, keeping the initial /executive/tenants route payload within the
 * fail-closed performance budget. It is purely presentational: form state,
 * capability gating and API calls remain owned by the page component, so the
 * authorization surface and fail-closed behavior are unchanged.
 */

const COUNTRY_CODES = (
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW"
).split(" ");

const LOCALES = [
  "ar-SA", "ar-AE", "ar-EG", "ar-BH", "ar-KW", "ar-OM", "ar-QA", "ar-JO", "ar-MA",
  "en-US", "en-GB", "en-AU", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR",
  "tr-TR", "id-ID", "ms-MY", "hi-IN", "ur-PK", "zh-CN", "ja-JP", "ko-KR",
];

const FALLBACK_TIMEZONES = [
  "UTC", "Asia/Riyadh", "Asia/Dubai", "Asia/Kuwait", "Asia/Qatar", "Asia/Bahrain",
  "Asia/Muscat", "Asia/Amman", "Asia/Baghdad", "Asia/Beirut", "Africa/Cairo",
  "Africa/Casablanca", "Europe/London", "Europe/Paris", "Europe/Berlin",
  "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "Asia/Kolkata", "Asia/Singapore", "Asia/Tokyo", "Australia/Sydney",
];

const FALLBACK_CURRENCIES = [
  "SAR", "AED", "BHD", "KWD", "OMR", "QAR", "USD", "EUR", "GBP", "EGP", "JOD",
  "MAD", "TRY", "INR", "PKR", "CNY", "JPY", "AUD", "CAD", "SGD",
];

function supportedIntlValues(kind: "currency" | "timeZone", fallback: string[]): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: "currency" | "timeZone") => string[] };
  try {
    const values = intl.supportedValuesOf?.(kind);
    return values && values.length > 0 ? values : fallback;
  } catch {
    return fallback;
  }
}

const TIMEZONES = supportedIntlValues("timeZone", FALLBACK_TIMEZONES);
const CURRENCY_CODES = supportedIntlValues("currency", FALLBACK_CURRENCIES);

function withCurrentOption(options: readonly string[], current: string): string[] {
  const value = current.trim();
  return value && !options.includes(value) ? [value, ...options] : [...options];
}

export type TenantDialogsProps = {
  dialog: NonNullable<TenantDialog>;
  busy: boolean;
  dialogError: string;
  createForm: {
    name: string; subdomain: string; adminEmail: string; adminDisplayName: string;
    countryCode: string; locale: string; timezone: string; currencyCode: string;
  };
  editForm: {
    name: string; legalName: string; billingEmail: string;
    countryCode: string; locale: string; timezone: string; currencyCode: string;
  };
  reason: string;
  setDialogError: (message: string) => void;
  setCreateForm: (form: TenantDialogsProps["createForm"]) => void;
  setEditForm: (form: TenantDialogsProps["editForm"]) => void;
  setReason: (reason: string) => void;
  closeDialog: () => void;
  onCreate: () => void;
  onUpdate: (tenantId: string) => void;
  onApplyStatus: (tenantId: string, targetStatus: string) => void;
};

export default function TenantDialogs({
  dialog, busy, dialogError, createForm, editForm, reason,
  setDialogError, setCreateForm, setEditForm, setReason,
  closeDialog, onCreate, onUpdate, onApplyStatus,
}: TenantDialogsProps) {
  const { t } = useI18n();

  if (dialog.kind === "create") {
    return (
      <Modal
        isOpen
        onClose={() => !busy && closeDialog()}
        title={t("scp.tenants.createDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={<>
          <Button variant="secondary" disabled={busy} onClick={closeDialog}>{t("form.action.cancel")}</Button>
          <Button variant="primary" loading={busy} disabled={!createForm.name || !createForm.subdomain || !createForm.adminEmail || !createForm.adminDisplayName} onClick={onCreate}>{t("form.action.create")}</Button>
        </>}
      >
        <div className={styles.filters}>
          <Input label={t("scp.tenants.form.name")} aria-label={t("scp.tenants.form.name")} required placeholder={t("scp.tenants.form.namePlaceholder")} value={createForm.name} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, name: e.target.value }); }} />
          <Input label={t("scp.tenants.form.subdomain")} aria-label={t("scp.tenants.form.subdomain")} required placeholder="acme" hint={t("scp.tenants.form.subdomainHint")} value={createForm.subdomain} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, subdomain: e.target.value.toLowerCase().replace(/\s+/g, "") }); }} />
          <Input type="email" label={t("scp.tenants.form.adminEmail")} aria-label={t("scp.tenants.form.adminEmail")} required placeholder="admin@example.com" value={createForm.adminEmail} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, adminEmail: e.target.value }); }} />
          <Input label={t("scp.tenants.form.adminDisplayName")} aria-label={t("scp.tenants.form.adminDisplayName")} required placeholder={t("scp.tenants.form.adminDisplayNamePlaceholder")} value={createForm.adminDisplayName} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, adminDisplayName: e.target.value }); }} />
          <label>
            <span>{t("scp.tenants.form.countryCode")}</span>
            <select
              aria-label={t("scp.tenants.form.countryCode")}
              value={createForm.countryCode}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, countryCode: e.target.value }); }}
            >
              <option value="">—</option>
              {COUNTRY_CODES.map((code) => <option key={code} value={code}>{code}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.countryHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.locale")}</span>
            <select
              aria-label={t("scp.tenants.form.locale")}
              value={createForm.locale}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, locale: e.target.value }); }}
            >
              <option value="">—</option>
              {LOCALES.map((locale) => <option key={locale} value={locale}>{locale}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.localeHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.timezone")}</span>
            <select
              aria-label={t("scp.tenants.form.timezone")}
              value={createForm.timezone}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, timezone: e.target.value }); }}
            >
              <option value="">—</option>
              {TIMEZONES.map((timezone) => <option key={timezone} value={timezone}>{timezone}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.timezoneHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.currencyCode")}</span>
            <select
              aria-label={t("scp.tenants.form.currencyCode")}
              value={createForm.currencyCode}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, currencyCode: e.target.value }); }}
            >
              <option value="">—</option>
              {CURRENCY_CODES.map((currency) => <option key={currency} value={currency}>{currency}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.currencyHint")}</span>
          </label>
          {dialogError ? <ScpError message={dialogError} /> : null}
        </div>
      </Modal>
    );
  }

  if (dialog.kind === "edit") {
    return (
      <Modal
        isOpen
        onClose={() => !busy && closeDialog()}
        title={t("scp.tenants.editDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={<>
          <Button variant="secondary" disabled={busy} onClick={closeDialog}>{t("form.action.cancel")}</Button>
          <Button variant="primary" loading={busy} disabled={!editForm.name} onClick={() => onUpdate(dialog.tenantId)}>{t("form.action.save")}</Button>
        </>}
      >
        <div className={styles.filters}>
          <Input label={t("scp.tenants.form.name")} aria-label={t("scp.tenants.form.name")} required value={editForm.name} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, name: e.target.value }); }} />
          <Input label={t("scp.tenants.form.legalName")} aria-label={t("scp.tenants.form.legalName")} value={editForm.legalName} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, legalName: e.target.value }); }} />
          <Input type="email" label={t("scp.tenants.form.billingEmail")} aria-label={t("scp.tenants.form.billingEmail")} value={editForm.billingEmail} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, billingEmail: e.target.value }); }} />
          <label>
            <span>{t("scp.tenants.form.countryCode")}</span>
            <select
              aria-label={t("scp.tenants.form.countryCode")}
              value={editForm.countryCode}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, countryCode: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(COUNTRY_CODES, editForm.countryCode).map((code) => (
                <option key={code} value={code}>{code}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.countryHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.locale")}</span>
            <select
              aria-label={t("scp.tenants.form.locale")}
              value={editForm.locale}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, locale: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(LOCALES, editForm.locale).map((locale) => (
                <option key={locale} value={locale}>{locale}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.localeHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.timezone")}</span>
            <select
              aria-label={t("scp.tenants.form.timezone")}
              value={editForm.timezone}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, timezone: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(TIMEZONES, editForm.timezone).map((timezone) => (
                <option key={timezone} value={timezone}>{timezone}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.timezoneHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.currencyCode")}</span>
            <select
              aria-label={t("scp.tenants.form.currencyCode")}
              value={editForm.currencyCode}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, currencyCode: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(CURRENCY_CODES, editForm.currencyCode).map((currency) => (
                <option key={currency} value={currency}>{currency}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.currencyHint")}</span>
          </label>
          <p className={styles.appCardMeta}>{t("scp.tenants.form.subdomainImmutableNote")}</p>
          {dialogError ? <ScpError message={dialogError} /> : null}
        </div>
      </Modal>
    );
  }

  return (
    <Modal
      isOpen
      onClose={() => !busy && closeDialog()}
      title={dialog.targetStatus === "ARCHIVED"
        ? t("scp.tenants.archiveDialogTitle")
        : dialog.targetStatus === "SUSPENDED"
          ? t("scp.tenants.freezeDialogTitle")
          : dialog.sourceStatus === "PENDING" || dialog.sourceStatus === "TRIAL"
            ? t("scp.tenants.activateDialogTitle")
            : t("scp.tenants.reactivateDialogTitle")}
      closeButtonLabel={t("common.close")}
      footer={<>
        <Button variant="secondary" disabled={busy} onClick={closeDialog}>{t("form.action.cancel")}</Button>
        <Button variant={dialog.targetStatus === "ARCHIVED" ? "danger" : "primary"} loading={busy} disabled={!reason.trim()} onClick={() => onApplyStatus(dialog.tenantId, dialog.targetStatus)}>{t("form.action.confirm")}</Button>
      </>}
    >
      {dialog.targetStatus === "ARCHIVED" ? <p>{t("scp.tenants.archiveWarning")}</p> : null}
      <Input label={t("scp.tenants.form.reason")} aria-label={t("scp.tenants.form.reason")} required placeholder={t("scp.tenants.form.reasonPlaceholder")} value={reason} maxLength={500} onChange={(e) => { setDialogError(""); setReason(e.target.value); }} />
      {dialogError ? <ScpError message={dialogError} /> : null}
    </Modal>
  );
}
