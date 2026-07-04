"use client";

import { useCallback, useEffect, useMemo, useState, type CSSProperties } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  healthIntelligenceApi,
  type HealthActionInput,
  type PlatformHealth,
  type RiskForecastPoint,
  type ServiceHealth,
  type TenantHealth,
} from "@/lib/api/health-intelligence";
import styles from "./executive-health-panel.module.css";

const number = new Intl.NumberFormat("ar-SA");

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    HEALTHY: "سليم",
    DEGRADED: "متدهور",
    CRITICAL: "حرج",
    NORMAL: "طبيعي",
    ELEVATED: "مرتفع",
    HIGH: "عالٍ",
    LOW: "منخفض",
    MEDIUM: "متوسط",
    OPERATIONAL: "تشغيلي",
    MAINTENANCE: "صيانة",
    INCIDENT: "حادث",
    DISABLED: "متوقف",
    STABLE: "مستقر",
    DEGRADATION_RISK: "خطر تدهور",
    INCIDENT_RISK: "خطر حادث",
    ACTIVE: "نشط",
    TRIAL: "تجريبي",
    PENDING: "معلّق",
    PAST_DUE: "متأخر",
    SUSPENDED: "موقوف",
    CANCELLED: "ملغي",
    ARCHIVED: "مؤرشف",
  };
  return labels[value] ?? value;
}

function Indicator({ value }: { value: string }) {
  return <span className={styles.indicator} data-state={value}>{statusLabel(value)}</span>;
}

function Meter({ value, label }: { value: number; label: string }) {
  return <div className={styles.meter} aria-label={`${label}: ${value}%`}>
    <div className={styles.meterHeading}><span>{label}</span><strong>{number.format(value)}٪</strong></div>
    <div className={styles.track}><span style={{ width: `${Math.max(0, Math.min(100, value))}%` }} /></div>
  </div>;
}

function ForecastChart({ points }: { points: RiskForecastPoint[] }) {
  const path = points.map((point, index) => {
    const x = points.length === 1 ? 50 : 8 + (index * 84) / (points.length - 1);
    const y = 92 - point.riskScore * 0.78;
    return `${x},${Math.max(10, Math.min(92, y))}`;
  }).join(" ");

  return <div className={styles.chart}>
    <svg viewBox="0 0 100 100" role="img" aria-label="توقع مستوى المخاطر خلال الساعة القادمة">
      <line x1="8" y1="92" x2="92" y2="92" />
      <line x1="8" y1="14" x2="8" y2="92" />
      <polyline points={path} />
      {points.map((point, index) => {
        const x = points.length === 1 ? 50 : 8 + (index * 84) / (points.length - 1);
        const y = Math.max(10, Math.min(92, 92 - point.riskScore * 0.78));
        return <circle key={point.horizonMinutes} cx={x} cy={y} r="2.2" />;
      })}
    </svg>
    <div className={styles.chartLegend}>{points.map((point) =>
      <span key={point.horizonMinutes}><small>{point.label}</small><strong>{number.format(point.riskScore)}٪</strong></span>)}</div>
  </div>;
}

export function ExecutiveHealthPanel() {
  const { state } = useAuth();
  const [health, setHealth] = useState<PlatformHealth | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const snapshot = await healthIntelligenceApi.snapshot();
      setHealth(snapshot);
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "تعذر تحميل صحة النظام.");
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    void load();
    const timer = window.setInterval(() => void load(), 30_000);
    return () => window.clearInterval(timer);
  }, [load, state]);

  const execute = useCallback(async (input: Omit<HealthActionInput, "reason">) => {
    const reason = window.prompt(
      "سبب تنفيذ الإجراء التشغيلي (سيتم تسجيله في سجل التدقيق)",
      "إجراء من لوحة صحة النظام",
    )?.trim();
    if (!reason) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const result = await healthIntelligenceApi.execute({ ...input, reason });
      setHealth(result.snapshot);
      setMessage(result.message);
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : "تعذر تنفيذ الإجراء.");
    } finally {
      setBusy(false);
    }
  }, []);

  const topTenants = useMemo(() =>
    [...(health?.tenants ?? [])].sort((left, right) => right.pressureScore - left.pressureScore),
  [health]);

  if (state !== "AUTHENTICATED") return null;

  return <section className={styles.shell} aria-labelledby="system-health-title">
    <div className={styles.heading}>
      <div>
        <p className={styles.eyebrow}>EXECUTIVE HEALTH INTELLIGENCE</p>
        <h2 id="system-health-title">صحة النظام والتصحيح الذاتي</h2>
        <p>مراقبة المنصة وكل مستأجر، ضغط البيانات، التنبؤ بالمخاطر، وتنفيذ إجراءات تشغيلية آمنة ومدققة.</p>
      </div>
      <div className={styles.actions}>
        <button type="button" disabled={busy || !health} onClick={() => void load()}>تحديث الآن</button>
        <button type="button" disabled={busy || !health} onClick={() => void execute({ scope: "PLATFORM", action: "RUN_DIAGNOSTICS" })}>تشخيص شامل</button>
        <button type="button" disabled={busy || !health} data-variant="heal" onClick={() => void execute({ scope: "PLATFORM", action: "AUTO_HEAL" })}>تشغيل التصحيح الذاتي</button>
      </div>
    </div>

    {message ? <div className={styles.notice}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}
    {!health ? <div className={styles.loading}>جارٍ جمع مؤشرات الصحة والضغط...</div> : <>
      <div className={styles.summaryGrid}>
        <article className={styles.scoreCard} data-state={health.overallStatus}>
          <div className={styles.scoreRing} style={{ "--score": `${health.healthScore}%` } as CSSProperties}>
            <strong>{number.format(health.healthScore)}</strong><span>من 100</span>
          </div>
          <div><Indicator value={health.overallStatus} /><h3>الصحة العامة للمنصة</h3><p>{health.predictionSummary}</p></div>
        </article>
        <article><span>خطر الساعة القادمة</span><strong>{statusLabel(health.riskLevel)}</strong><Indicator value={health.riskLevel} /></article>
        <article><span>ضغط البيانات</span><strong>{number.format(health.dataPressure.pressureScore)}٪</strong><Indicator value={health.dataPressure.status} /></article>
        <article><span>الخدمات غير السليمة</span><strong>{number.format(health.services.filter((item) => item.status !== "OPERATIONAL").length)}</strong><small>من أصل {number.format(health.services.length)} خدمات</small></article>
      </div>

      <div className={styles.twoColumns}>
        <article className={styles.panel}>
          <div className={styles.panelTitle}><div><span>مؤشرات فورية</span><h3>ضغط موارد التشغيل</h3></div><time>{new Date(health.generatedAt).toLocaleTimeString("ar-SA")}</time></div>
          <Meter value={Math.round(health.runtime.cpuLoadPercent)} label="المعالج" />
          <Meter value={Math.round(health.runtime.memoryUsagePercent)} label="الذاكرة" />
          <Meter value={health.dataPressure.pressureScore} label="ضغط البيانات والأحداث" />
          <dl className={styles.statList}>
            <div><dt>الذاكرة المستخدمة</dt><dd>{number.format(health.runtime.memoryUsedMb)} / {number.format(health.runtime.memoryMaxMb)} MB</dd></div>
            <div><dt>السجلات المراقبة</dt><dd>{number.format(health.dataPressure.trackedRows)}</dd></div>
            <div><dt>أحداث آخر ساعة</dt><dd>{number.format(health.dataPressure.auditEventsLastHour)}</dd></div>
            <div><dt>الأحداث الفاشلة</dt><dd>{number.format(health.dataPressure.failedAuditEventsLastHour)}</dd></div>
          </dl>
          <p className={styles.explanation}>{health.dataPressure.message}</p>
        </article>
        <article className={styles.panel}>
          <div className={styles.panelTitle}><div><span>تنبؤ استباقي</span><h3>مسار المخاطر المتوقع</h3></div><Indicator value={health.riskLevel} /></div>
          <ForecastChart points={health.forecast} />
          <p className={styles.explanation}>التوقع تقديري مبني على الصحة الحالية، ضغط الموارد، معدل الأحداث وحالة الخدمات؛ ويُحدّث كل 30 ثانية.</p>
        </article>
      </div>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><span>تحكم تشغيلي</span><h3>صحة مكونات النظام</h3></div><small>الإجراءات محصورة في قائمة آمنة وتُسجّل بالكامل</small></div>
        <div className={styles.serviceGrid}>{health.services.map((service: ServiceHealth) =>
          <section key={service.id} className={styles.serviceCard} data-state={service.status}>
            <div className={styles.cardHeading}><div><Indicator value={service.status} /><small>{service.criticality}</small></div><strong>{number.format(service.healthScore)}٪</strong></div>
            <h4>{service.name}</h4><p>{service.code} · {service.environment}</p>
            <Meter value={service.pressureScore} label="الضغط" />
            <div className={styles.miniStats}><span>الاستجابة: {service.latencyMs === null ? "—" : `${number.format(service.latencyMs)} ms`}</span><span>التوقع: {statusLabel(service.predictedStatus)}</span></div>
            <div className={styles.cardActions}>
              <button type="button" disabled={busy} onClick={() => void execute({ scope: "SERVICE", targetId: service.id, action: "RUN_DIAGNOSTICS" })}>تشخيص</button>
              {service.code === "API" || service.code === "DATABASE" ? <button type="button" disabled={busy} data-variant="heal" onClick={() => void execute({ scope: "SERVICE", targetId: service.id, action: "AUTO_HEAL" })}>تصحيح ذاتي</button> : null}
              <button type="button" disabled={busy} onClick={() => void execute({ scope: "SERVICE", targetId: service.id, action: "MARK_MAINTENANCE" })}>صيانة</button>
            </div>
          </section>)}</div>
      </article>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><span>عزل متعدد المستأجرين</span><h3>صحة وضغط كل مستأجر</h3></div><small>{number.format(topTenants.length)} مستأجر</small></div>
        <div className={styles.tenantTable} role="table">
          <div className={styles.tenantHeader} role="row"><span>المستأجر</span><span>الصحة</span><span>الضغط</span><span>السعة</span><span>البيانات</span><span>التوقع والإجراء</span></div>
          {topTenants.map((tenant: TenantHealth) => <div className={styles.tenantRow} role="row" key={tenant.tenantId}>
            <div><strong>{tenant.tenantName}</strong><Indicator value={tenant.tenantStatus} /></div>
            <div><strong>{number.format(tenant.healthScore)}٪</strong><Indicator value={tenant.riskLevel} /></div>
            <div><Meter value={tenant.pressureScore} label="الضغط" /></div>
            <div><strong>{number.format(tenant.seatUtilizationPercent)}٪</strong><small>{number.format(tenant.users)} / {number.format(tenant.seatCapacity)} مستخدم</small></div>
            <div><strong>{number.format(tenant.trackedRecords)}</strong><small>{number.format(tenant.organizations)} شركات · {number.format(tenant.openInvoices)} فواتير مفتوحة</small></div>
            <div><p>{tenant.prediction}</p><button type="button" disabled={busy} onClick={() => void execute({ scope: "TENANT", targetId: tenant.tenantId, action: "REFRESH_TENANT_HEALTH" })}>إعادة تقييم</button></div>
          </div>)}
        </div>
      </article>
    </>}
  </section>;
}
