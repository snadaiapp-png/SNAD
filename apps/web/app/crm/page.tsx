"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { listCrmAccounts, type CrmAccount } from "@/lib/crm/accounts";
import styles from "./crm.module.css";

export default function CrmPage() {
  const router = useRouter();
  const { state } = useAuth();
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [message, setMessage] = useState("جارٍ تحميل الحسابات…");

  useEffect(() => {
    if (state === "ANONYMOUS" || state === "ERROR" || state === "EXPIRED") {
      router.replace("/");
      return;
    }
    if (state !== "AUTHENTICATED") return;
    listCrmAccounts()
      .then((items) => {
        setAccounts(items);
        setMessage(items.length ? "" : "لا توجد حسابات بعد.");
      })
      .catch(() => setMessage("تعذر تحميل حسابات CRM."));
  }, [state, router]);

  if (state !== "AUTHENTICATED") return <AuthLoadingState />;

  return (
    <main className={styles.page}>
      <div className={styles.shell}>
        <header className={styles.header}>
          <div><p>SNAD Global CRM</p><h1>حسابات العملاء</h1></div>
          <span className={styles.badge}>Preview غير إنتاجي</span>
        </header>
        <section className={styles.panel}>
          {message ? <p>{message}</p> : null}
          <div className={styles.list}>
            {accounts.map((account) => (
              <article className={styles.card} key={account.id}>
                <div><h3>{account.displayName}</h3><div className={styles.meta}><span>{account.accountType}</span><span>{account.primaryCurrencyCode ?? "—"}</span></div></div>
                <span className={styles.status}>{account.lifecycleStatus}</span>
              </article>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
