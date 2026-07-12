"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../crm.module.css";

interface CrmLoadingProps {
  /** Optional label override. Defaults to the i18n loading state. */
  label?: string;
  /** Number of skeleton rows to render in the placeholder table. */
  rows?: number;
}

/**
 * CrmLoading — Skeleton + spinner shown while a CRM page is fetching data.
 *
 * Renders:
 *   - A metric skeleton row (3 cards) for dashboard-like layouts.
 *   - A table skeleton with the requested number of rows for list pages.
 *
 * The component is intentionally pure — it does not own any state and relies
 * only on the i18n system for the loading label.
 */
export function CrmLoading({ label, rows = 5 }: CrmLoadingProps) {
  const { t } = useI18n();
  const text = label ?? t("crm.state.loading");
  return (
    <div className={styles.root} role="status" aria-live="polite">
      <p className={styles.srOnly}>{text}</p>
      <section className={styles.metrics} aria-hidden="true">
        {Array.from({ length: 3 }).map((_, idx) => (
          <article key={idx} className={styles.skeletonCard}>
            <span className={styles.skeletonLine} />
            <span className={styles.skeletonLineWide} />
          </article>
        ))}
      </section>
      <div className={styles.listCard} aria-hidden="true">
        <span className={styles.skeletonLineWide} />
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                {Array.from({ length: 4 }).map((_, idx) => (
                  <th key={idx}>
                    <span className={styles.skeletonLine} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: rows }).map((_, rowIdx) => (
                <tr key={rowIdx}>
                  {Array.from({ length: 4 }).map((_, colIdx) => (
                    <td key={colIdx}>
                      <span className={styles.skeletonLine} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
