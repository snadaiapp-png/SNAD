"use client";

import { useEffect, useState } from "react";
import styles from "./auth.module.css";

interface AuthLoadingStateProps {
  title?: string;
  subtitle?: string;
}

export function AuthLoadingState({
  title = "جارٍ تجهيز مساحة العمل",
  subtitle = "يتم التحقق من الجلسة الآمنة وتحميل بيانات الحساب.",
}: AuthLoadingStateProps) {
  const [progressText, setProgressText] = useState(subtitle);

  useEffect(() => {
    const timer = setTimeout(() => {
      setProgressText("يستغرق هذا عادةً لحظات قليلة...");
    }, 3000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div className={styles.authLoadingRoot} role="status" aria-live="polite">
      <div className={styles.authLoadingCard}>
        <div className={styles.authSpinner} aria-hidden="true" />
        <p className={styles.authLoadingTitle}>{title}</p>
        <p className={styles.authLoadingSubtitle}>{progressText}</p>
      </div>
    </div>
  );
}
