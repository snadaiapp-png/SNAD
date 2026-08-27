import styles from "../erp.module.css";

export function ErpFeedback({ error, notice }: { error?: string; notice?: string }) {
  return (
    <>
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      {notice ? <div className={styles.success} role="status">{notice}</div> : null}
    </>
  );
}

export function ErpEmpty({ children }: { children: React.ReactNode }) {
  return <div className={styles.empty}>{children}</div>;
}

export function ErpLoading() {
  return <div className={styles.empty} role="status">جارٍ تحميل البيانات…</div>;
}
