"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { crmApi, type CrmTag, type CrmAccount, type CrmTagAssignment } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formValue, optionalValue } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

const TAG_COLOR_NAMES = ["", "red", "amber", "green", "blue", "purple", "pink", "gray"] as const;
type TagColorName = (typeof TAG_COLOR_NAMES)[number];

/**
 * CRM Tags route — /crm/tags
 *
 * Two-panel layout:
 *   - Left: tag catalog management (create/delete tags)
 *   - Right: when an account is selected, shows tag assignments
 *
 * Uses CSS classes instead of hardcoded hex colors (SDS compliant).
 *
 * Branch: feature/crm-tags
 */
export default function CrmTagsPage() {
  const { t } = useI18n();
  const [tags, setTags] = useState<CrmTag[]>([]);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [assignments, setAssignments] = useState<CrmTagAssignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedAccountId, setSelectedAccountId] = useState("");

  const reload = useCallback(async (accountId?: string) => {
    setLoading(true);
    setError("");
    try {
      const [nextTags, nextAccounts] = await Promise.all([
        crmApi.tags(),
        crmApi.accounts(),
      ]);
      setTags(nextTags);
      setAccounts(nextAccounts);
      if (accountId) {
        const nextAssignments = await crmApi.tagAssignmentsBySubject("ACCOUNT", accountId);
        setAssignments(nextAssignments);
      } else {
        setAssignments([]);
      }
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setTags([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(selectedAccountId), 0);
    return () => window.clearTimeout(timer);
  }, [reload, selectedAccountId]);

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload(selectedAccountId);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await mutate(
      () => crmApi.createTag({ name: formValue(form, "name"), color: optionalValue(form, "color") }),
      t("crm.tags.created"),
    );
    formElement.reset();
  }

  async function handleDelete(tagId: string) {
    if (!window.confirm(t("crm.tags.confirmDelete"))) return;
    await mutate(() => crmApi.deleteTag(tagId), t("crm.tags.deleted"));
  }

  async function handleAssign(tagId: string) {
    if (!selectedAccountId) return;
    await mutate(
      () => crmApi.assignTag(tagId, { subjectType: "ACCOUNT", subjectId: selectedAccountId }),
      t("crm.tags.assigned"),
    );
  }

  async function handleUnassign(tagId: string) {
    if (!selectedAccountId) return;
    await mutate(
      () => crmApi.unassignTag(tagId, "ACCOUNT", selectedAccountId),
      t("crm.tags.unassigned"),
    );
  }

  const assignedTagIds = new Set(assignments.map((a) => a.tag_id));

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.tags.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.tags.description")}</p>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}

      <section className={styles.workspace}>
        <div className={styles.formCard}>
          <h2 className={styles.sectionHeading}>{t("crm.tags.create.title")}</h2>
          <form onSubmit={handleCreate}>
            <label>
              {t("crm.tags.create.name")}
              <input name="name" required maxLength={80} disabled={busy} />
            </label>
            <label>
              {t("crm.tags.create.color")}
              <select name="color" defaultValue="" disabled={busy}>
                {TAG_COLOR_NAMES.map((c) => (
                  <option key={c} value={c}>{c === "" ? t("crm.tags.create.colorNone") : c}</option>
                ))}
              </select>
            </label>
            <button type="submit" disabled={busy}>{t("crm.tags.create.submit")}</button>
          </form>

          <div style={{ marginTop: "1.5rem" }}>
            <h3 className={styles.sectionHeading}>{t("crm.tags.list.title")}</h3>
            {loading ? (
              <CrmLoading rows={3} />
            ) : tags.length === 0 ? (
              <CrmEmpty title={t("crm.tags.empty")} hint={t("crm.state.emptyHint")} />
            ) : (
              <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
                {tags.map((tag) => (
                  <span key={tag.id} className={styles.badge} style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
                    {tag.name}
                    <button
                      type="button"
                      onClick={() => void handleDelete(tag.id)}
                      disabled={busy}
                      style={{ background: "none", border: "none", cursor: "pointer", padding: 0, fontSize: "1rem", lineHeight: 1, opacity: 0.6 }}
                      title={t("crm.tags.delete")}
                      aria-label={t("crm.tags.delete")}
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className={styles.listCard}>
          <div className={styles.rowHeader}>
            <h2 className={styles.sectionHeading}>{t("crm.tags.assignments.title")}</h2>
            <label>
              {t("crm.tags.assignments.account")}
              <select
                value={selectedAccountId}
                onChange={(e) => setSelectedAccountId(e.target.value)}
                disabled={busy}
              >
                <option value="">{t("crm.tags.assignments.accountNone")}</option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>{account.display_name}</option>
                ))}
              </select>
            </label>
          </div>

          {!selectedAccountId ? (
            <CrmEmpty
              title={t("crm.tags.assignments.noAccount")}
              hint={t("crm.tags.assignments.noAccountHint")}
            />
          ) : (
            <div>
              <h3 className={styles.sectionHeading}>{t("crm.tags.assignments.assigned")}</h3>
              {assignments.length === 0 ? (
                <p style={{ opacity: 0.6, fontSize: "0.875rem" }}>{t("crm.tags.assignments.noneAssigned")}</p>
              ) : (
                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginBottom: "1rem" }}>
                  {assignments.map((a) => (
                    <span key={a.id} className={styles.badge} style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
                      {a.tag_name}
                      <button
                        type="button"
                        onClick={() => void handleUnassign(a.tag_id)}
                        disabled={busy}
                        style={{ background: "none", border: "none", cursor: "pointer", padding: 0, fontSize: "1rem", lineHeight: 1, opacity: 0.6 }}
                        title={t("crm.tags.unassign")}
                        aria-label={t("crm.tags.unassign")}
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              )}

              <h3 className={styles.sectionHeading}>{t("crm.tags.assignments.available")}</h3>
              {tags.filter((tag) => !assignedTagIds.has(tag.id)).length === 0 ? (
                <p style={{ opacity: 0.6, fontSize: "0.875rem" }}>{t("crm.tags.assignments.allAssigned")}</p>
              ) : (
                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
                  {tags.filter((tag) => !assignedTagIds.has(tag.id)).map((tag) => (
                    <button
                      key={tag.id}
                      type="button"
                      onClick={() => void handleAssign(tag.id)}
                      disabled={busy}
                      className={styles.badge}
                      style={{ cursor: "pointer", border: "1px dashed currentColor", opacity: 0.7 }}
                    >
                      + {tag.name}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
