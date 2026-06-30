import styles from "./auth.module.css";

export function AuthIntelligenceVisual() {
  return (
    <div className={styles.intelligencePanel} aria-hidden="true">
      <div className={styles.intelligenceGrid} />
      <div className={styles.intelligenceCore} />

      <div className={styles.intelligenceContent}>
        <div className={styles.intelligenceBrand}>
          SNAD <span className={styles.intelligenceGoldAccent}>•</span> سند
        </div>

        <div>
          <h2 className={styles.intelligenceHeadline}>
            نظام تشغيل أعمال ذكي
          </h2>
          <p className={styles.intelligenceSubtext}>
            وحّد عملياتك، تدفقات العمل، بياناتك وقراراتك
            في منصة مؤسسية واحدة مدعومة بالذكاء.
          </p>
        </div>

        <div className={styles.intelligenceDomains}>
          <span className={styles.intelligenceDomainTag}>AI</span>
          <span className={styles.intelligenceDomainTag}>Workflow</span>
          <span className={styles.intelligenceDomainTag}>ERP</span>
          <span className={styles.intelligenceDomainTag}>CRM</span>
          <span className={styles.intelligenceDomainTag}>Finance</span>
          <span className={styles.intelligenceDomainTag}>Analytics</span>
        </div>

        <div className={styles.intelligenceSignals}>
          <div className={styles.intelligenceSignal}>
            <span className={styles.intelligenceSignalDot} />
            الأنظمة مترابطة
          </div>
          <div className={styles.intelligenceSignal}>
            <span className={styles.intelligenceSignalDot} />
            القرارات مدعومة بالبيانات
          </div>
          <div className={styles.intelligenceSignal}>
            <span className={styles.intelligenceSignalDot} />
            الوصول محمي
          </div>
        </div>
      </div>
    </div>
  );
}
