import type { ReactNode } from "react";
import sdsStyles from "@/components/sds/module/snad-module.module.css";

/**
 * HR product surface primitives — thin module wrappers over the SHARED SNAD
 * module primitives (components/sds/module) so every HR page inherits the
 * same visual anatomy as CRM (SNAD Module Visual Contract, G3 Task 6).
 * Historical g2-* data-testids are preserved for the visual gates.
 */

export function HrProductHeader({ title, subtitle, eyebrow, trailing }: { title: string; subtitle: string; eyebrow?: string; trailing?: ReactNode }) {
  return (
    <header className={sdsStyles.pageHeader} data-testid="g2-product-header">
      <div className={sdsStyles.pageHeaderCopy}>
        {eyebrow ? <span className={sdsStyles.pageEyebrow}>{eyebrow}</span> : null}
        <h1 className={sdsStyles.pageTitle} data-testid="g2-page-title">{title}</h1>
        {subtitle ? <p className={sdsStyles.pageDescription}>{subtitle}</p> : null}
      </div>
      {trailing ? <div className={sdsStyles.pageHeaderTrailing}>{trailing}</div> : null}
    </header>
  );
}

export function HrKpiGrid({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className={sdsStyles.kpiGrid} aria-label={label} data-testid="g2-kpi-grid">
      {children}
    </section>
  );
}

export function HrKpiCard({ label, value, hint, tone = "neutral" }: { label: string; value: ReactNode; hint?: string; tone?: "neutral" | "positive" | "attention" }) {
  const toneClass =
    tone === "positive" ? sdsStyles.kpiCardTonePositive : tone === "attention" ? sdsStyles.kpiCardToneAttention : "";
  return (
    <article className={`${sdsStyles.kpiCard} ${toneClass}`} data-tone={tone}>
      <span className={sdsStyles.kpiLabel}>{label}</span>
      <span className={sdsStyles.kpiValue}>{value}</span>
      {hint ? <span className={sdsStyles.kpiHint}>{hint}</span> : null}
    </article>
  );
}

export function HrActionBar({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className={sdsStyles.actionBar} aria-label={label} data-testid="g2-action-bar">
      {children}
    </section>
  );
}

export function HrOperationalPanel({ label, title, description, children }: { label: string; title: string; description?: string; children: ReactNode }) {
  return (
    <section className={sdsStyles.sectionCard} aria-label={label} data-testid="g2-operational-panel">
      <header>
        <h2 className={sdsStyles.sectionCardTitle}>{title}</h2>
        {description ? <p className={sdsStyles.sectionCardDescription}>{description}</p> : null}
      </header>
      <div>{children}</div>
    </section>
  );
}

export function HrMobileRecordList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className={sdsStyles.mobileRecords} aria-label={label} data-testid="g2-mobile-record-list">
      {children}
    </section>
  );
}
