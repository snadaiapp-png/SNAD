"use client";

import { useCallback, useEffect, useState } from "react";
import {
  crmIntelligenceApi,
  type IntelligenceScore,
  type IntelligenceNba,
  type IntelligenceSegmentMembership,
  type IntelligenceInsight,
} from "@/lib/api/crm-intelligence";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-shared-styles.module.css";

/* ── Score band color mapping ────────────────────────────────────────────── */

const SCORE_BAND_COLORS: Record<string, string> = {
  // Health
  CRITICAL: "#dc2626",
  AT_RISK: "#ea580c",
  HEALTHY: "#16a34a",
  THRIVING: "#2563eb",
  // Risk
  LOW_RISK: "#16a34a",
  MEDIUM_RISK: "#ea580c",
  HIGH_RISK: "#dc2626",
  // Loyalty
  NEW: "#6b7280",
  GROWING: "#2563eb",
  LOYAL: "#16a34a",
  CHAMPION: "#7c3aed",
  // Engagement
  DORMANT: "#6b7280",
  LOW: "#ea580c",
  MODERATE: "#2563eb",
  HIGH: "#16a34a",
  // CLV
  LOW_VALUE: "#6b7280",
  MID_VALUE: "#2563eb",
  HIGH_VALUE: "#16a34a",
};

const SCORE_TYPE_LABELS: Record<string, { en: string; ar: string }> = {
  HEALTH: { en: "Health Score", ar: "نقاط الصحة" },
  CLV: { en: "Customer Lifetime Value", ar: "قيمة حياة العميل" },
  ENGAGEMENT: { en: "Engagement Score", ar: "نقاط التفاعل" },
  RISK: { en: "Churn Risk", ar: "خطر فقدان العميل" },
  LOYALTY: { en: "Loyalty Score", ar: "نقاط الولاء" },
};

function getBandColor(band: string): string {
  return SCORE_BAND_COLORS[band] ?? "#6b7280";
}

/* ── Component ───────────────────────────────────────────────────────────── */

interface IntelligenceTabProps {
  accountId: string;
}

export function IntelligenceTab({ accountId }: IntelligenceTabProps) {
  const { t, lang } = useCrmI18n();
  const [scores, setScores] = useState<IntelligenceScore[]>([]);
  const [insight, setInsight] = useState<IntelligenceInsight | null>(null);
  const [nbas, setNbas] = useState<IntelligenceNba[]>([]);
  const [segments, setSegments] = useState<IntelligenceSegmentMembership[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [calculating, setCalculating] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [scoresData, insightData, nbaData, segData] = await Promise.allSettled([
        crmIntelligenceApi.getScores(accountId),
        crmIntelligenceApi.getInsights(accountId),
        crmIntelligenceApi.getNba(accountId),
        crmIntelligenceApi.getAccountSegments(accountId),
      ]);
      if (scoresData.status === "fulfilled") setScores(scoresData.value as unknown as IntelligenceScore[]);
      if (insightData.status === "fulfilled") setInsight(insightData.value as unknown as IntelligenceInsight);
      if (nbaData.status === "fulfilled") setNbas(nbaData.value as unknown as IntelligenceNba[]);
      if (segData.status === "fulfilled") setSegments(segData.value as unknown as IntelligenceSegmentMembership[]);

      // If all failed, show error
      const allFailed = [scoresData, insightData, nbaData, segData].every((r) => r.status === "rejected");
      if (allFailed) {
        const reason = (scoresData as PromiseRejectedResult).reason;
        setError(toUserFacingError(reason).message);
      }
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadData(), 0);
    return () => window.clearTimeout(timer);
  }, [loadData]);

  async function handleCalculateHealth() {
    setCalculating(true);
    try {
      const result = await crmIntelligenceApi.calculateHealth(accountId, {});
      setScores((prev) => {
        const score = result as unknown as IntelligenceScore;
        const idx = prev.findIndex((s) => s.scoreType === score.scoreType);
        if (idx >= 0) {
          const next = [...prev];
          next[idx] = score;
          return next;
        }
        return [...prev, score];
      });
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setCalculating(false);
    }
  }

  async function handleAcceptNba(nba: IntelligenceNba) {
    setActionLoading(nba.id);
    try {
      const updated = await crmIntelligenceApi.acceptNba(nba.id, nba.version);
      setNbas((prev) => prev.map((n) => (n.id === nba.id ? (updated as unknown as IntelligenceNba) : n)));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setActionLoading(null);
    }
  }

  async function handleRejectNba(nba: IntelligenceNba) {
    setActionLoading(nba.id);
    try {
      const updated = await crmIntelligenceApi.rejectNba(nba.id, nba.version);
      setNbas((prev) => prev.map((n) => (n.id === nba.id ? (updated as unknown as IntelligenceNba) : n)));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setActionLoading(null);
    }
  }

  if (loading) {
    return <div className={styles.loading}>{t("crm.intelligence.loading")}</div>;
  }

  if (error && scores.length === 0 && nbas.length === 0) {
    return (
      <div className={styles.contentInner}>
        <div className={styles.errorBanner} role="alert">
          <span>{error}</span>
          <button type="button" className={styles.dismissButton} onClick={() => void loadData()}>
            {t("crm.shell.refresh")}
          </button>
        </div>
      </div>
    );
  }

  const scoreTypes = ["HEALTH", "CLV", "ENGAGEMENT", "RISK", "LOYALTY"];

  return (
    <div className={styles.contentInner}>
      {error ? (
        <div className={styles.errorBanner} role="alert">
          <span>{error}</span>
          <button type="button" className={styles.dismissButton} onClick={() => setError("")}>
            &times;
          </button>
        </div>
      ) : null}

      {/* ── Score Cards ──────────────────────────────────────────────── */}
      <section aria-label={t("crm.intelligence.scores")}>
        <div className={styles.rowHeader}>
          <h2 className={styles.overviewSectionTitle}>{t("crm.intelligence.scores")}</h2>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() => void handleCalculateHealth()}
            disabled={calculating}
          >
            {calculating ? t("crm.intelligence.calculating") : t("crm.intelligence.calculateHealth")}
          </button>
        </div>
        <div className={styles.kpiGrid}>
          {scoreTypes.map((scoreType) => {
            const score = scores.find((s) => s.scoreType === scoreType);
            const label = SCORE_TYPE_LABELS[scoreType]?.[lang] ?? scoreType;
            const band = score?.scoreBand ?? "N/A";
            const bandColor = getBandColor(band);
            const labelInfo = SCORE_TYPE_LABELS[scoreType];

            return (
              <article
                key={scoreType}
                className={styles.kpiCard}
                style={{ borderInlineStartColor: bandColor, borderInlineStartWidth: 4 }}
              >
                <span className={styles.kpiLabel}>{label}</span>
                {score ? (
                  <>
                    <span className={styles.kpiValue} style={{ color: bandColor }}>
                      {score.scoreValue.toFixed(1)}
                    </span>
                    <span
                      style={{
                        display: "inline-block",
                        padding: "2px 10px",
                        borderRadius: 999,
                        fontSize: "0.75rem",
                        fontWeight: 700,
                        color: "#fff",
                        background: bandColor,
                        alignSelf: "flex-start",
                      }}
                    >
                      {band}
                    </span>
                    <span className={styles.kpiHint}>
                      {t("crm.intelligence.confidence")}: {(score.confidence * 100).toFixed(0)}%
                    </span>
                  </>
                ) : (
                  <span className={styles.kpiHint}>{t("crm.intelligence.noScore")}</span>
                )}
              </article>
            );
          })}
        </div>
      </section>

      {/* ── Insight Summary ──────────────────────────────────────────── */}
      {insight?.summary ? (
        <section className={styles.overviewSection} aria-label={t("crm.intelligence.insights")}>
          <h2 className={styles.overviewSectionTitle}>{t("crm.intelligence.insights")}</h2>
          <div className={styles.overviewStats}>
            {insight.summary.healthBand ? (
              <div className={styles.overviewStat}>
                <span className={styles.overviewStatLabel}>{t("crm.intelligence.healthBand")}</span>
                <span className={styles.overviewStatValue} style={{ color: getBandColor(insight.summary.healthBand) }}>
                  {insight.summary.healthBand}
                </span>
              </div>
            ) : null}
            {insight.summary.clvTier ? (
              <div className={styles.overviewStat}>
                <span className={styles.overviewStatLabel}>{t("crm.intelligence.clvTier")}</span>
                <span className={styles.overviewStatValue} style={{ color: getBandColor(insight.summary.clvTier) }}>
                  {insight.summary.clvTier}
                </span>
              </div>
            ) : null}
            {insight.summary.riskBand ? (
              <div className={styles.overviewStat}>
                <span className={styles.overviewStatLabel}>{t("crm.intelligence.riskBand")}</span>
                <span className={styles.overviewStatValue} style={{ color: getBandColor(insight.summary.riskBand) }}>
                  {insight.summary.riskBand}
                </span>
              </div>
            ) : null}
          </div>
        </section>
      ) : null}

      {/* ── Next Best Actions ────────────────────────────────────────── */}
      <section className={styles.overviewSection} aria-label={t("crm.intelligence.nba")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.intelligence.nba")}</h2>
        {nbas.length === 0 ? (
          <p className={styles.kpiHint}>{t("crm.intelligence.noNba")}</p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {nbas.map((nba) => (
              <div
                key={nba.id}
                style={{
                  display: "flex",
                  alignItems: "flex-start",
                  justifyContent: "space-between",
                  gap: 12,
                  padding: "12px 16px",
                  background: "var(--snad-surface-secondary)",
                  borderRadius: 10,
                  border: "1px solid var(--snad-border-subtle)",
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, color: "var(--snad-text-primary)", marginBottom: 4 }}>
                    {nba.description}
                  </div>
                  <div style={{ fontSize: "0.82rem", color: "var(--snad-text-secondary)", lineHeight: 1.5 }}>
                    {nba.reasoning}
                  </div>
                  <div style={{ display: "flex", gap: 8, marginTop: 6, flexWrap: "wrap" }}>
                    <span
                      style={{
                        fontSize: "0.75rem",
                        fontWeight: 700,
                        padding: "2px 8px",
                        borderRadius: 999,
                        background: "var(--snad-brand-primary)",
                        color: "var(--snad-text-inverse)",
                      }}
                    >
                      {nba.actionCode}
                    </span>
                    <span className={styles.kpiHint}>
                      {t("crm.intelligence.confidence")}: {(nba.confidence * 100).toFixed(0)}%
                    </span>
                    <span
                      style={{
                        fontSize: "0.75rem",
                        fontWeight: 700,
                        padding: "2px 8px",
                        borderRadius: 999,
                        background: nba.status === "PENDING" ? "var(--snad-warning-soft)" : nba.status === "ACCEPTED" ? "var(--snad-success-soft)" : "var(--snad-surface-secondary)",
                        color: nba.status === "PENDING" ? "var(--snad-warning)" : nba.status === "ACCEPTED" ? "var(--snad-success)" : "var(--snad-text-muted)",
                      }}
                    >
                      {nba.status}
                    </span>
                  </div>
                </div>
                {nba.status === "PENDING" ? (
                  <div className={styles.actionGroup} style={{ flexShrink: 0 }}>
                    <button
                      type="button"
                      className={styles.primaryButton}
                      style={{ fontSize: "0.8rem", padding: "4px 12px" }}
                      onClick={() => void handleAcceptNba(nba)}
                      disabled={actionLoading === nba.id}
                    >
                      {t("crm.intelligence.accept")}
                    </button>
                    <button
                      type="button"
                      className={styles.cancelButton}
                      style={{ fontSize: "0.8rem", padding: "4px 12px" }}
                      onClick={() => void handleRejectNba(nba)}
                      disabled={actionLoading === nba.id}
                    >
                      {t("crm.intelligence.reject")}
                    </button>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* ── Segment Memberships ──────────────────────────────────────── */}
      <section className={styles.overviewSection} aria-label={t("crm.intelligence.segments")}>
        <h2 className={styles.overviewSectionTitle}>{t("crm.intelligence.segments")}</h2>
        {segments.length === 0 ? (
          <p className={styles.kpiHint}>{t("crm.intelligence.noSegments")}</p>
        ) : (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            {segments.map((seg) => (
              <span
                key={seg.id}
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 6,
                  padding: "6px 14px",
                  borderRadius: 999,
                  background: seg.active ? "var(--snad-brand-primary)" : "var(--snad-surface-secondary)",
                  color: seg.active ? "var(--snad-text-inverse)" : "var(--snad-text-muted)",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                  border: seg.active ? "none" : "1px solid var(--snad-border-default)",
                }}
              >
                {seg.segmentId}
                <span style={{ fontSize: "0.7rem", opacity: 0.8 }}>{seg.membershipType}</span>
              </span>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
