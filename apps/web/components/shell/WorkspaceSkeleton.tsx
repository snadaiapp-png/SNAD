"use client";

/**
 * SNAD | سند — Workspace Skeleton
 *
 * Per PM Directive §1.5: Skeleton UI for workspace bootstrap.
 * Shows a branded loading state while the workspace is initializing.
 *
 * Phase 1 (Critical Bootstrap):
 *   - SnadLogo visible
 *   - Navigation skeleton
 *   - Header skeleton
 *   - Content area skeleton
 *
 * Phase 2 (Progressive Loading):
 *   - Dashboard widgets load independently
 *   - Charts load lazily
 *   - Secondary data loads in background
 */
import { SnadLogo } from "@/components/sds/SnadLogo";
import styles from "./WorkspaceSkeleton.module.css";

export function WorkspaceSkeleton() {
  return (
    <div className={styles.root} role="status" aria-label="جاري تحميل مساحة العمل">
      {/* Header with logo — always visible during bootstrap */}
      <header className={styles.header}>
        <SnadLogo variant="compact" size="sm" href="/" alt="شعار سند — SNAD Business Operating System" />
        <div className={styles.headerSkeleton} aria-hidden="true" />
      </header>

      {/* Navigation skeleton */}
      <nav className={styles.nav} aria-hidden="true">
        <div className={styles.navItem} />
        <div className={styles.navItem} />
        <div className={styles.navItem} />
        <div className={styles.navItem} />
        <div className={styles.navItem} />
      </nav>

      {/* Content area skeleton */}
      <main className={styles.content}>
        {/* KPI cards skeleton */}
        <div className={styles.kpiRow}>
          <div className={styles.kpiCard} />
          <div className={styles.kpiCard} />
          <div className={styles.kpiCard} />
          <div className={styles.kpiCard} />
        </div>

        {/* Chart skeleton */}
        <div className={styles.chartCard} />

        {/* Table skeleton */}
        <div className={styles.tableCard}>
          <div className={styles.tableRow} />
          <div className={styles.tableRow} />
          <div className={styles.tableRow} />
          <div className={styles.tableRow} />
          <div className={styles.tableRow} />
        </div>
      </main>

      {/* Screen reader announcement */}
      <span className={styles.srOnly}>
        جاري تحميل مساحة العمل. يرجى الانتظار.
      </span>
    </div>
  );
}
