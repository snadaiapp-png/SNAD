"use client";

import { useEffect, useState } from "react";
import { emitAuthMetric } from "@/lib/observability/auth-performance";
import styles from "./workspace.module.css";

export function WorkspaceSecondaryPanels() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setReady(true), 0);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (ready) {
      emitAuthMetric({ event: "workspace_secondary_loaded", outcome: "success" });
    }
  }, [ready]);

  if (!ready) {
    return (
      <section className={styles.secondarySkeleton} aria-label="جارٍ تحميل الوحدات الثانوية">
        <div /><div /><div />
      </section>
    );
  }

  return (
    <section className={styles.secondaryGrid} aria-label="الوحدات والخدمات">
      <article><h2>الوحدات التشغيلية</h2><p>ERP · CRM · HR · POS · PAY</p></article>
      <article><h2>الذكاء والأتمتة</h2><p>Workflow · AI · Analytics</p></article>
      <article><h2>حالة التجهيز</h2><p>اكتمل التحميل الثانوي دون تعطيل الواجهة.</p></article>
    </section>
  );
}
