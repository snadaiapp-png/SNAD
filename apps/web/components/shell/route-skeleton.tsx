import styles from "./route-skeleton.module.css";

export function RouteSkeleton() {
  return (
    <main className={styles.root} aria-busy="true" aria-label="Loading">
      <header className={styles.header} aria-hidden="true">
        <div className={styles.logo} />
        <div className={styles.headerAction} />
      </header>
      <div className={styles.layout} aria-hidden="true">
        <aside className={styles.sidebar}>
          {Array.from({ length: 6 }, (_, index) => <div className={styles.navItem} key={index} />)}
        </aside>
        <section className={styles.content}>
          <div className={styles.title} />
          <div className={styles.grid}>
            {Array.from({ length: 4 }, (_, index) => <div className={styles.card} key={index} />)}
          </div>
        </section>
      </div>
    </main>
  );
}
