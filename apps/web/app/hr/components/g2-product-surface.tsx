import type { ReactNode } from "react";
import styles from "./hr-g2-visual.module.css";

export function G2ProductHeader({
  eyebrow,
  title,
  subtitle,
  actions,
  status,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  actions?: ReactNode;
  status?: ReactNode;
}) {
  return (
    <header className={styles.productHeader}>
      <div className={styles.productHeaderCopy}>
        <span className={styles.productEyebrow}>{eyebrow}</span>
        <div className={styles.productTitleRow}>
          <h1 data-testid="g2-page-title" className={styles.productTitle}>{title}</h1>
          {status ? <div className={styles.productStatus}>{status}</div> : null}
        </div>
        <p className={styles.productSubtitle}>{subtitle}</p>
      </div>
      {actions ? <div className={styles.productActions}>{actions}</div> : null}
    </header>
  );
}

export function G2MetricGrid({ children, testId }: { children: ReactNode; testId?: string }) {
  return <div className={styles.metricGrid} data-testid={testId}>{children}</div>;
}

export function G2MetricCard({
  label,
  value,
  hint,
  testId,
  tone = "neutral",
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  testId?: string;
  tone?: "neutral" | "positive" | "attention";
}) {
  return (
    <article className={styles.metricCard} data-tone={tone} data-testid={testId}>
      <span className={styles.metricLabel}>{label}</span>
      <strong className={styles.metricValue}>{value}</strong>
      {hint ? <span className={styles.metricHint}>{hint}</span> : null}
    </article>
  );
}

export function G2SurfaceSection({
  title,
  description,
  action,
  children,
  testId,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  testId?: string;
}) {
  return (
    <section className={styles.surfaceSection} data-testid={testId}>
      <div className={styles.surfaceSectionHeader}>
        <div>
          <h2 className={styles.surfaceSectionTitle}>{title}</h2>
          {description ? <p className={styles.surfaceSectionDescription}>{description}</p> : null}
        </div>
        {action ? <div className={styles.surfaceSectionAction}>{action}</div> : null}
      </div>
      <div className={styles.surfaceSectionBody}>{children}</div>
    </section>
  );
}
