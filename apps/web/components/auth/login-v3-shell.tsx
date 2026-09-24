import type { ReactNode } from "react";

import { LoginV3BrandPanel } from "./login-v3-brand-panel";
import styles from "./login-v3.module.css";

export function LoginV3Shell({ children }: { children: ReactNode }) {
  return (
    <main className={styles.shell} data-auth-version="v3">
      <div className={styles.frame}>
        <LoginV3BrandPanel />
        <section className={styles.formRegion}>{children}</section>
      </div>
    </main>
  );
}
