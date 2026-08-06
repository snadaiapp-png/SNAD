"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  healthIntelligenceApi,
  type PlatformHealth,
  type ServiceHealth,
  type TenantHealth,
  type RiskForecastPoint,
} from "@/lib/api/health-intelligence";
import {
  platformOperationsApi,
  type SystemService,
} from "@/lib/api/platform-operations";
import styles from "./system-health.module.css";

const number = new Intl.NumberFormat("ar-SA");

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    HEALTHY: "سليم", DEGRADED: "متدهور", CRITICAL: "حرج",
    NORMAL: "طبيعي", ELEVATED: "مرتفع", HIGH: "عالٍ", LOW: "منخفض", MEDIUM: "متوسط",
    OPERATIONAL: "تشغيلي", MAINTENANCE: "صيانة", INCIDENT: "حادث", DISABLED: "متوقف",
    STABLE: "مستقر", DEGRADATION_RISK: "خطر تدهور", INCIDENT_RISK: "خطر حادث",
    ACTIVE: "نشط", TRIAL: "تجريبي", PENDING: "معلّق", PAST_DUE: "متأخر",
    SUSPENDED: "موقوف", CANCELLED: "ملغي", ARCHIVED: "مؤرشف",
  };
  return labels[value] ?? value;
}

function Indicator({ value }: { value: string }) {
  return <span className={styles.indicator} data-state={value}>{statusLabel(value)}</span>;
}

export function SystemHealthDashboard() {
  const { state } = useAuth();
  const [health, setHealth] = useState<PlatformHealth | null>(null);
  const [services, setServices] = useState<SystemService[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const [platformHealth, systemServices] = await Promise.all([
        healthIntelligenceApi.snapshot(),
        platformOperationsApi.systems(),
      ]);
      setHealth(platformHealth);
      setServices(systemServices);
    } catch (reason) {
      setError(String(reason));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (state === "AUTHENTICATED") void refresh();
  }, [state, refresh]);

  if (state !== "AUTHENTICATED") return <AuthLoadingState phase="session" />;

  return (
    <div className={styles.shell}>
      <div className={styles.heading}>
        <div>
          <p className={styles.eyebrow}>SYSTEM HEALTH</p>
          <h2>صحة النظام والمراقبة</h2>
          <p>مراقبة البنية التحتية والأداء والتشخيصات</p>
        </div>
        <div className={styles.actions}>
          <button onClick={() => void refresh()} disabled={busy}>تحديث</button>
        </div>
      </div>

      {error ? <div className={styles.error}>{error}</div> : null}
      {busy && !health ? <div className={styles.loading}>جارٍ التحميل…</div> : null}

      {health ? (
        <>
          <div className={styles.summaryGrid}>
            <article className={styles.scoreCard} data-state={health.overallStatus}>
              <div className={styles.scoreRing} style={{ ["--score" as string]: `${health.healthScore}%` } as React.CSSProperties}>
                <strong>{health.healthScore}</strong>
                <span>درجة الصحة</span>
              </div>
              <div>
                <h3>الحالة العامة</h3>
                <p><Indicator value={health.overallStatus} /></p>
              </div>
            </article>
            <article>
              <span>الخدمات السليمة</span>
              <strong>{health.services?.filter(s => s.status === "OPERATIONAL").length ?? 0}</strong>
              <small>من {health.services?.length ?? 0}</small>
            </article>
            <article>
              <span>المستأجرون النشطون</span>
              <strong>{health.tenants?.filter(t => t.tenantStatus === "HEALTHY").length ?? 0}</strong>
              <small>من {health.tenants?.length ?? 0}</small>
            </article>
            <article>
              <span>التنبيهات</span>
              <strong>{health.forecast?.length ?? 0}</strong>
              <small>توقعات المخاطر</small>
            </article>
          </div>

          {health.services && health.services.length > 0 ? (
            <div className={styles.panel}>
              <h3>حالة الخدمات</h3>
              <div className={styles.tableWrap}>
                <table>
                  <thead><tr><th>الخدمة</th><th>الحالة</th><th>درجة الصحة</th></tr></thead>
                  <tbody>
                    {health.services.map(s => (
                      <tr key={s.name}>
                        <td>{s.name}</td>
                        <td><Indicator value={s.status} /></td>
                        <td>{s.healthScore ? `${(s.healthScore * 100).toFixed(2)}%` : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}

          {services.length > 0 ? (
            <div className={styles.panel}>
              <h3>خدمات النظام</h3>
              <div className={styles.tableWrap}>
                <table>
                  <thead><tr><th>الاسم</th><th>الحالة</th><th>النوع</th></tr></thead>
                  <tbody>
                    {services.map(s => (
                      <tr key={s.id}>
                        <td>{s.name}</td>
                        <td><Indicator value={s.status} /></td>
                        <td>{s.criticality}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
