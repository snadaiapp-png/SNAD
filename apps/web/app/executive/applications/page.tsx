"use client";

import { useCallback, useEffect, useState } from "react";
import {
  scpApi,
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
import styles from "../scp.module.css";

/**
 * Application catalog — rendered entirely from catalog data. Adding a new
 * application never requires navigation or UI code changes.
 */
export default function ApplicationsPage() {
  const { t } = useI18n();
  const { day } = useScpFormat();
  const [applications, setApplications] = useState<ScpApplication[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ code: "", name: "", localizedName: "", category: "" });
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

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError("");
    try {
      await scpApi.createApplication({
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        localizedName: form.localizedName.trim() || undefined,
        category: form.category.trim() || undefined,
      });
      setForm({ code: "", name: "", localizedName: "", category: "" });
      setCreating(false);
      await load();
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : String(reason));
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

      <div>
        <Button variant="primary" size="sm" onClick={() => setCreating((value) => !value)}>
          {creating ? t("scp.applications.cancelCreate") : t("scp.applications.create")}
        </Button>
      </div>

      {creating ? (
        <form className={styles.panel} onSubmit={(event) => void submit(event)}>
          <label>
            <span>{t("scp.applications.code")}</span>
            <Input
              value={form.code}
              onChange={(event) => setForm((f) => ({ ...f, code: event.target.value }))}
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
          <Button type="submit" variant="primary" size="sm">
            {t("scp.applications.submit")}
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
            </article>
          ))}
        </div>
      ) : null}
    </ScpPage>
  );
}
