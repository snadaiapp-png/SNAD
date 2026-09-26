"use client";

import { createContext, type ReactNode } from "react";
import visualStyles from "./hr-g2-visual.module.css";

export const HrG2ProductSurfaceContext = createContext(false);

export interface HrG2Metric {
  label: string;
  value: ReactNode;
  hint?: string;
}

interface HrG2ProductSurfaceProps {
  eyebrow: string;
  title: string;
  subtitle: string;
  metrics: readonly HrG2Metric[];
  primaryAction?: ReactNode;
  toolbar?: ReactNode;
  children: ReactNode;
}

/**
 * Shared product-grade G2 presentation frame.
 * Authorization stays in each route/backend; this component is presentation only.
 */
export function HrG2ProductSurface({
  eyebrow,
  title,
  subtitle,
  metrics,
  primaryAction,
  toolbar,
  children,
}: HrG2ProductSurfaceProps) {
  return (
    <HrG2ProductSurfaceContext.Provider value={true}>
      <section className={visualStyles.productSurface} data-testid="g2-product-surface">
        <header className={visualStyles.productHero}>
          <div className={visualStyles.productHeroCopy}>
            <span className={visualStyles.productEyebrow}>{eyebrow}</span>
            <h1 className={visualStyles.productTitle} data-testid="g2-page-title">{title}</h1>
            <p className={visualStyles.productSubtitle}>{subtitle}</p>
          </div>
          <div className={visualStyles.productActions} data-testid="g2-primary-action-region">
            {primaryAction ?? <span className={visualStyles.productActionPlaceholder} aria-hidden="true" />}
          </div>
        </header>

        <div className={visualStyles.metricGrid} data-testid="g2-kpi-grid">
          {metrics.map((metric, index) => (
            <article className={visualStyles.metricCard} key={`${metric.label}-${index}`}>
              <span className={visualStyles.metricLabel}>{metric.label}</span>
              <strong className={visualStyles.metricValue}>{metric.value}</strong>
              {metric.hint ? <span className={visualStyles.metricHint}>{metric.hint}</span> : null}
            </article>
          ))}
        </div>

        {toolbar ? <div className={visualStyles.productToolbar}>{toolbar}</div> : null}

        <div className={visualStyles.productContent} data-testid="g2-product-content">
          {children}
        </div>
      </section>
    </HrG2ProductSurfaceContext.Provider>
  );
}
