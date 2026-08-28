"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type CSSProperties,
} from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  systemHealthApi,
  type PlatformHealth,
  type SystemService,
} from "@/lib/api/system-health-api";
import styles from "./system-health.module.css";

const number = new Intl.NumberFormat("ar-SA");
const dateTime = new Intl.DateTimeFormat("ar-SA", {
  dateStyle: "medium",
  timeStyle: "short",
});

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    HEALTHY: "سليم",
    DEGRADED: "متدهور",
    CRITICAL: "حرج",
    UNKNOWN: "غير معروف",
    UNHEALTHY: "غير سليم",
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

function percent(value: number) {
  const normalized = Math.max(0, Math.min(100, Math.round(Number.isFinite(value) ? value : 0)));
  return `${normalized}%`;
}

function formatDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : dateTime.format(parsed);
}

function formatUptime(seconds: number) {
  const safeSeconds = Math.max(0, seconds || 0);
  const days = Math.floor(safeSeconds / 86_400);
  const hours = Math.floor((safeSeconds % 86_400) / 3_600);
  const minutes = Math.floor((safeSeconds % 3_600) / 60);
  if (days > 0) return `${number.format(days)} يوم ${number.format(hours)} ساعة`;
  if (hours > 0) return `${number.format(hours)} ساعة ${number.format(minutes)} دقيقة`;
  return `${number.format(minutes)} دقيقة`;
}

function errorMessage(reason: unknown) {
  return reason instanceof Error ? reason.message : String(reason);
}

function Indicator({ value }: { value: string }) {
  return <span className={styles.indicator} data-state={value}>{statusLabel(value)}</span>;
}

function Meter({ label, value }: { label: string; value: number }) {
  const normalized = Math.max(0, Math.min(100, Number.isFinite(value) ? value : 0));
  return (
    <div className={styles.meter}>
      <div className={styles.meterHeading}>
        <span>{label}</span>
        <strong>{percent(normalized)}</strong>
      </div>
      <div className={styles.track} aria-label={`${label}: ${percent(normalized)}`}>
        <span style={{ width: `${normalized}%` }} />
      </div>
    </div>
  );
}

export function SystemHealthDashboard() {
  const { state } = useAuth();
  const [health, setHealth] = useState<PlatformHealth | null>(null);
  const [systems, setSystems] = useState<SystemService[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [diagnosticsBusy, setDiagnosticsBusy] = useState(false);

  const refresh = useCallback(async () => {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const [snapshotResult, systemsResult] = await Promise.allSettled([
        systemHealthApi.snapshot(),
        systemHealthApi.systems(),
      ]);

      if (snapshotResult.status === "rejected") {
        throw snapshotResult.reason;
      }

      setHealth(snapshotResult.value);
      if (systemsResult.status === "fulfilled") {
        setSystems(systemsResult.value);
      } else {
        setSystems([]);
        setNotice("تم تحميل المؤشرات الصحية، لكن سجل الخدمات التفصيلي غير متاح حالياً.");
      }
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    void refresh();
    const interval = window.setInterval(() => void refresh(), 30_000);
    return () => window.clearInterval(interval);
  }, [state, refresh]);

  const runDiagnostics = useCallback(async () => {
    if (!health) return;
    setDiagnosticsBusy(true);
    setError("");
    setNotice("");
    try {
      const result = await systemHealthApi.execute({
        scope: "PLATFORM",
        action: "RUN_DIAGNOSTICS",
        reason: "Manual diagnostics initiated from System Health console",
      });
      setHealth(result.snapshot);
      setNotice(result.message || "اكتمل الفحص التشخيصي بنجاح.");
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setDiagnosticsBusy(false);
    }
  }, [health]);

  const systemsById = useMemo(
    () => new Map(systems.map(system => [system.id, system])),
    [systems],
  );

  if (state !== "AUTHENTICATED") return <AuthLoadingState phase="session" />;

  const canRunDiagnostics = health?.availableActions?.some(
    action => action.scope === "PLATFORM" && action.code === "RUN_DIAGNOSTICS" && !action.requiresTarget,
  ) ?? false;
  const operationalServices = health?.services?.filter(service => service.status === "OPERATIONAL").length ?? 0;
  const healthyTenants = health?.tenants?.filter(tenant => tenant.tenantStatus === "HEALTHY").length ?? 0;

  return (
    <div className={styles.shell}>
      <div className={styles.heading}>
        <div>
          <p className={styles.eyebrow}>SYSTEM HEALTH</p>
          <h2>صحة النظام والمراقبة</h2>
          <p>مراقبة البنية التحتية والأداء وجودة القياس والتنبؤ بالمخاطر من مصدر تشغيلي واحد.</p>
          {health ? (
            <div className={styles.headingMeta}>
              <span>آخر قياس: {formatDate(health.generatedAt)}</span>
              <span>المخاطر: <Indicator value={health.riskLevel} /></span>
            </div>
          ) : null}
        </div>
        <div className={styles.actions}>
          {canRunDiagnostics ? (
            <button
              type="button"
              data-variant="heal"
              onClick={() => void runDiagnostics()}
              disabled={busy || diagnosticsBusy}
            >
              {diagnosticsBusy ? "جارٍ التشخيص…" : "تشغيل التشخيصات"}
            </button>
          ) : null}
          <button type="button" onClick={() => void refresh()} disabled={busy || diagnosticsBusy}>
            {busy ? "جارٍ التحديث…" : "تحديث الآن"}
          </button>
        </div>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.notice}>{notice}</div> : null}
      {busy && !health ? <div className={styles.loading}>جارٍ تحميل المؤشرات التشغيلية…</div> : null}

      {health ? (
        <>
          {health.partial ? (
            <section className={styles.degradedNotice} aria-label="جودة بيانات صحة النظام">
              <div>
                <strong>اللقطة الصحية جزئية</strong>
                <p>بعض مجمّعات القياس لم تستجب. لا يتم اعتبار البيانات المفقودة سليمة تلقائياً.</p>
              </div>
              <div>
                <span>جاهزية البيانات</span>
                <strong>{percent(health.dataCompletenessScore)}</strong>
              </div>
            </section>
          ) : null}

          <div className={styles.summaryGrid}>
            <article className={styles.scoreCard} data-state={health.overallStatus}>
              <div
                className={styles.scoreRing}
                style={{ ["--score" as string]: `${health.healthScore}%` } as CSSProperties}
              >
                <strong>{health.healthScore}</strong>
                <span>درجة الصحة</span>
              </div>
              <div>
                <h3>الحالة العامة</h3>
                <p><Indicator value={health.overallStatus} /></p>
                <small>{health.predictionSummary}</small>
              </div>
            </article>
            <article>
              <span>جاهزية البيانات</span>
              <strong>{percent(health.dataCompletenessScore)}</strong>
              <small>{health.partial ? "بيانات جزئية" : "جميع المجمعات متاحة"}</small>
            </article>
            <article>
              <span>الخدمات التشغيلية</span>
              <strong>{operationalServices}</strong>
              <small>من {health.services.length}</small>
            </article>
            <article>
              <span>المستأجرون السليمون</span>
              <strong>{healthyTenants}</strong>
              <small>من {health.tenants.length}</small>
            </article>
          </div>

          <div className={styles.twoColumns}>
            <section className={styles.panel} aria-labelledby="runtime-title">
              <div className={styles.panelTitle}>
                <div>
                  <span>RUNTIME</span>
                  <h3 id="runtime-title">المؤشرات التشغيلية</h3>
                </div>
                <small>{number.format(health.runtime.availableProcessors)} معالجات منطقية</small>
              </div>
              <Meter label="استخدام المعالج" value={health.runtime.cpuLoadPercent} />
              <Meter label="استخدام الذاكرة" value={health.runtime.memoryUsagePercent} />
              <dl className={styles.statList}>
                <div><dt>زمن التشغيل</dt><dd>{formatUptime(health.runtime.uptimeSeconds)}</dd></div>
                <div><dt>الذاكرة المستخدمة</dt><dd>{number.format(health.runtime.memoryUsedMb)} MB</dd></div>
                <div><dt>الذاكرة القصوى</dt><dd>{number.format(health.runtime.memoryMaxMb)} MB</dd></div>
                <div><dt>عدد المعالجات</dt><dd>{number.format(health.runtime.availableProcessors)}</dd></div>
              </dl>
            </section>

            <section className={styles.panel} aria-labelledby="pressure-title">
              <div className={styles.panelTitle}>
                <div>
                  <span>DATA PRESSURE</span>
                  <h3 id="pressure-title">ضغط البيانات والعمليات</h3>
                </div>
                <Indicator value={health.dataPressure.status} />
              </div>
              <Meter label="مؤشر الضغط" value={health.dataPressure.pressureScore} />
              <dl className={styles.statList}>
                <div><dt>السجلات المتتبعة</dt><dd>{number.format(health.dataPressure.trackedRows)}</dd></div>
                <div><dt>أحداث التدقيق / ساعة</dt><dd>{number.format(health.dataPressure.auditEventsLastHour)}</dd></div>
                <div><dt>أحداث التدقيق الفاشلة</dt><dd>{number.format(health.dataPressure.failedAuditEventsLastHour)}</dd></div>
                <div><dt>المستخدمون النشطون</dt><dd>{number.format(health.dataPressure.activeUsers)}</dd></div>
                <div><dt>الفواتير المفتوحة</dt><dd>{number.format(health.dataPressure.openInvoices)}</dd></div>
              </dl>
              <p className={styles.explanation}>{health.dataPressure.message}</p>
            </section>
          </div>

          {health.collectionErrors.length > 0 ? (
            <section className={styles.panel} aria-labelledby="collection-errors-title">
              <div className={styles.panelTitle}>
                <div>
                  <span>DATA QUALITY</span>
                  <h3 id="collection-errors-title">أخطاء جمع المؤشرات</h3>
                </div>
                <Indicator value="DEGRADED" />
              </div>
              <div className={styles.diagnosticsList}>
                {health.collectionErrors.map(item => (
                  <article key={`${item.code}-${item.correlationId}`} className={styles.diagnosticItem}>
                    <div>
                      <strong>{item.code}</strong>
                      <span>{item.component}</span>
                    </div>
                    <p>{item.message}</p>
                    <small>Correlation: {item.correlationId} · {formatDate(item.timestamp)}</small>
                  </article>
                ))}
              </div>
            </section>
          ) : null}

          {health.forecast.length > 0 ? (
            <section className={styles.panel} aria-labelledby="forecast-title">
              <div className={styles.panelTitle}>
                <div>
                  <span>RISK FORECAST</span>
                  <h3 id="forecast-title">التنبؤ بالمخاطر</h3>
                </div>
                <small>قراءة استباقية وليست بديلاً عن القياس الفعلي</small>
              </div>
              <div className={styles.forecastGrid}>
                {health.forecast.map(point => (
                  <article key={`${point.horizonMinutes}-${point.label}`} className={styles.forecastCard}>
                    <div className={styles.cardHeading}>
                      <strong>{point.label}</strong>
                      <Indicator value={point.riskLevel} />
                    </div>
                    <Meter label="درجة المخاطر" value={point.riskScore} />
                  </article>
                ))}
              </div>
            </section>
          ) : null}

          <section className={styles.panel} aria-labelledby="services-title">
            <div className={styles.panelTitle}>
              <div>
                <span>SERVICES</span>
                <h3 id="services-title">صحة الخدمات</h3>
              </div>
              <small>{number.format(health.services.length)} خدمة مراقبة</small>
            </div>
            {health.services.length > 0 ? (
              <div className={styles.serviceGrid}>
                {health.services.map(service => {
                  const registry = systemsById.get(service.id);
                  return (
                    <article key={service.id} className={styles.serviceCard} data-state={service.status}>
                      <div className={styles.cardHeading}>
                        <div>
                          <strong>{service.code}</strong>
                          <small>{service.environment}</small>
                        </div>
                        <Indicator value={service.status} />
                      </div>
                      <h4>{service.name}</h4>
                      <p>{registry?.ownerName ? `المالك: ${registry.ownerName}` : service.lastMessage || "لا توجد رسالة تشغيلية"}</p>
                      <Meter label="درجة الصحة" value={service.healthScore} />
                      <Meter label="ضغط الخدمة" value={service.pressureScore} />
                      <div className={styles.miniStats}>
                        <span>الحرجية: {statusLabel(service.criticality)}</span>
                        <span>المخاطر: {statusLabel(service.riskLevel)}</span>
                        <span>المتوقع: {statusLabel(service.predictedStatus)}</span>
                        <span>زمن الاستجابة: {service.latencyMs == null ? "—" : `${number.format(service.latencyMs)} ms`}</span>
                        <span>آخر فحص: {formatDate(service.lastCheckedAt)}</span>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : (
              <p className={styles.emptyState}>لا توجد خدمات في اللقطة الصحية الحالية.</p>
            )}
          </section>

          <section className={styles.panel} aria-labelledby="tenants-title">
            <div className={styles.panelTitle}>
              <div>
                <span>TENANTS</span>
                <h3 id="tenants-title">صحة المستأجرين</h3>
              </div>
              <small>{number.format(health.tenants.length)} مستأجر</small>
            </div>
            {health.tenants.length > 0 ? (
              <div className={styles.tenantTable}>
                <div className={styles.tenantHeader} aria-hidden="true">
                  <span>المستأجر</span><span>الحالة</span><span>الصحة</span><span>المقاعد</span><span>السجلات</span><span>المخاطر والتوقع</span>
                </div>
                {health.tenants.map(tenant => (
                  <div className={styles.tenantRow} key={tenant.tenantId}>
                    <div><strong>{tenant.tenantName}</strong><small>{tenant.tenantId}</small></div>
                    <div><Indicator value={tenant.tenantStatus} /></div>
                    <div><Meter label="الصحة" value={tenant.healthScore} /></div>
                    <div>
                      <strong>{percent(tenant.seatUtilizationPercent)}</strong>
                      <small>{number.format(tenant.users)} مستخدم / {number.format(tenant.seatCapacity)} مقعد</small>
                    </div>
                    <div>
                      <strong>{number.format(tenant.trackedRecords)}</strong>
                      <small>{number.format(tenant.openInvoices)} فواتير مفتوحة</small>
                    </div>
                    <div>
                      <p><Indicator value={tenant.riskLevel} /></p>
                      <small>{statusLabel(tenant.prediction)}</small>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className={styles.emptyState}>لا توجد بيانات مستأجرين في اللقطة الصحية الحالية.</p>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
