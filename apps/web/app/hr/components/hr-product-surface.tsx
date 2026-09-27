import type { ReactNode } from "react";
import styles from "./hr-g2-visual.module.css";

export function HrProductHeader({ title, subtitle, eyebrow, trailing }: { title: string; subtitle: string; eyebrow?: string; trailing?: ReactNode }) {
  return (
    <header className={styles.productSurface} data-testid="g2-product-header">
      <div className={styles.productHeaderCopy}>
        {eyebrow ? <span className={styles.productEyebrow}>{eyebrow}</span> : null}
        <h1 data-testid="g2-page-title">{title}</h1>
        <p className={styles.productSubtitle}>{subtitle}</p>
      </div>
      {trailing ? <div className={styles.productHeaderTrailing}>{trailing}</div> : null}
    </header>
  );
}

export function HrKpiGrid({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className={styles.productKpiGrid} aria-label={label} data-testid="g2-kpi-grid">
      {children}
    </section>
  );
}

export function HrKpiCard({ label, value, hint, tone = "neutral" }: { label: string; value: ReactNode; hint?: string; tone?: "neutral" | "positive" | "attention" }) {
  return (
    <article className={styles.productKpiCard} data-tone={tone}>
      <span className={styles.productKpiValue}>{value}</span>
      <span className={styles.productKpiLabel}>{label}</span>
      {hint ? <span className={styles.productKpiHint}>{hint}</span> : null}
    </article>
  );
}

export function HrActionBar({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className={styles.productActionBar} aria-label={label} data-testid="g2-action-bar">
      {children}
    </section>
  );
}

export function HrOperationalPanel({ label, title, description, children }: { label: string; title: string; description?: string; children: ReactNode }) {
  return (
    <section className={styles.productOperationalPanel} aria-label={label} data-testid="g2-operational-panel">
      <header className={styles.productPanelHeader}>
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </header>
      <div className={styles.productPanelBody}>{children}</div>
    </section>
  );
}

export function HrMobileRecordList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className={styles.productMobileRecords} aria-label={label} data-testid="g2-mobile-record-list">
      {children}
    </section>
  );
}
