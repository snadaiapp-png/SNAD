"use client";

import { useCallback, useEffect, useState } from "react";
import {
  scpApi,
  type ApplicationInput,
  type ScpApplication,
} from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import { useScpAccess } from "../_components/ScpAccess";
import styles from "../scp.module.css";

const EMPTY_FORM = { code: "", name: "", localizedName: "", category: "" };

function applicationInput(
  application: ScpApplication,
  overrides: Partial<ApplicationInput> = {},
): ApplicationInput {
  const status =
    application.status === "ACTIVE" ||
    application.status === "INACTIVE" ||
    application.status === "DEPRECATED"
      ? application.status
      : "ACTIVE";
  return {
    code: application.code,
    name: application.name,
    localizedName: application.localizedName ?? undefined,
    description: application.description ?? undefined,
    category: application.category,
    status,
    iconKey: application.iconKey ?? undefined,
    provisioningMode: application.provisioningMode,
    supportedCountries: application.supportedCountries ?? undefined,
    dependencies: application.dependencies ?? undefined,
    displayOrder: application.displayOrder,
    ...overrides,
  };
}

/**
 * Application catalog — rendered entirely from catalog data. Adding a new
 * application never requires navigation or UI code changes.
 */
export default function ApplicationsPage() {
  const { t } = useI18n();
  const { day } = useScpFormat();
  const { has } = useScpAccess();
  const canManage = has("EXECUTIVE_MANAGE");
  const [applications, setApplications] = useState<ScpApplication[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setApplications(await scpApi.applications());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function closeForm() {
    setCreating(false);
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError("");
  }

  function beginCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError("");
    setCreating(true);
  }

  function beginEdit(application: ScpApplication) {
    setCreating(false);
    setEditingId(application.id);
    setForm({
      code: application.code,
      name: application.name,
      localizedName: application.localizedName ?? "",
      category: application.category ?? "",
    });
    setFormError("");
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError("");
    setBusy(true);
    try {
      if (editingId) {
        const existing = applications?.find((application) => application.id === editingId);
        if (!existing) throw new Error("Application no longer exists in the loaded catalog.");
        await scpApi.updateApplication(
          editingId,
          applicationInput(existing, {
            name: form.name.trim(),
            localizedName: form.localizedName.trim() || undefined,
            category: form.category.trim() || undefined,
          }),
        );
      } else {
        await scpApi.createApplication({
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          localizedName: form.localizedName.trim() || undefined,
          category: form.category.trim() || undefined,
        });
      }
      closeForm();
      await load();
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function retire(application: ScpApplication) {
    setError("");
    setBusy(true);
    try {
      await scpApi.updateApplication(
        application.id,
        applicationInput(application, { status: "DEPRECATED" }),
      );
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <ScpPage title={t("scp.applications.title")}>
        <ScpSkeleton lines={6} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.applications.title")} subtitle={t("scp.applications.subtitle")}>
      {error ? <ScpError message={error} onRetry={load} /> : null}

      {canManage ? (
        <div>
          <Button
            variant="primary"
            size="sm"
            onClick={() => (creating || editingId ? closeForm() : beginCreate())}
          >
            {creating || editingId ? t("scp.applications.cancelCreate") : t("scp.applications.create")}
          </Button>
        </div>
      ) : null}

      {creating || editingId ? (
        <form className={styles.panel} onSubmit={(event) => void submit(event)}>
          <label>
            <span>{t("scp.applications.code")}</span>
            <Input
              value={form.code}
              onChange={(event) => setForm((f) => ({ ...f, code: event.target.value }))}
              disabled={editingId !== null}
              required
              maxLength={50}
            />
          </label>
          <label>
            <span>{t("scp.applications.name")}</span>
            <Input
              value={form.name}
              onChange={(event) => setForm((f) => ({ ...f, name: event.target.value }))}
              required
              maxLength={200}
            />
          </label>
          <label>
            <span>{t("scp.applications.localizedName")}</span>
            <Input
              value={form.localizedName}
              onChange={(event) => setForm((f) => ({ ...f, localizedName: event.target.value }))}
              maxLength={200}
            />
          </label>
          <label>
            <span>{t("scp.applications.category")}</span>
            <Input
              value={form.category}
              onChange={(event) => setForm((f) => ({ ...f, category: event.target.value }))}
              maxLength={50}
            />
          </label>
          {formError ? <ScpError message={formError} /> : null}
          <Button type="submit" variant="primary" size="sm" loading={busy} disabled={busy}>
            {editingId ? t("scp.applications.update") : t("scp.applications.submit")}
          </Button>
        </form>
      ) : null}

      {applications && applications.length === 0 ? (
        <ScpEmpty message={t("scp.state.empty")} />
      ) : applications ? (
        <div className={styles.cards}>
          {applications.map((application) => (
            <article key={application.id} className={styles.appCard}>
              <h2 className={styles.appCardTitle}>
                {application.localizedName || application.name}
              </h2>
              <span className={styles.appCardMeta}>{application.code}</span>
              {application.description ? (
                <p className={styles.pageSubtitle}>{application.description}</p>
              ) : null}
              <div className={styles.filters}>
                <ScpStatusPill value={application.status} />
                {application.version ? (
                  <span className={styles.appCardMeta}>v{application.version}</span>
                ) : null}
                <span className={styles.appCardMeta}>{application.category}</span>
              </div>
              <span className={styles.appCardMeta}>
                {t("scp.applications.provisioning")}: {application.provisioningMode}
              </span>
              <span className={styles.appCardMeta}>
                {t("scp.applications.updatedAt")}: {day(application.updatedAt)}
              </span>
              {canManage ? (
                <div className={styles.filters}>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    disabled={busy}
                    onClick={() => beginEdit(application)}
                  >
                    {t("scp.applications.edit")}
                  </Button>
                  {application.status !== "DEPRECATED" ? (
                    <Button
                      type="button"
                      variant="danger"
                      size="sm"
                      disabled={busy}
                      onClick={() => void retire(application)}
                    >
                      {t("scp.applications.archive")}
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
    </ScpPage>
  );
}
