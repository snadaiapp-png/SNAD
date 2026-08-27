"use client";

import type { ReactNode } from "react";
import { useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import { useAuth } from "@/lib/auth/auth-provider";
import styles from "../erp.module.css";

export const ERP_NAV = [
  { href: "/erp", label: "الرئيسية" },
  { href: "/erp/items", label: "الأصناف" },
  { href: "/erp/suppliers", label: "الموردون" },
  { href: "/erp/warehouses", label: "المستودعات" },
  { href: "/erp/inventory", label: "المخزون" },
  { href: "/erp/requisitions", label: "طلبات الشراء" },
  { href: "/erp/purchase-orders", label: "أوامر الشراء" },
  { href: "/erp/goods-receipts", label: "استلام البضاعة" },
] as const;

interface ErpWorkspaceProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
}

export function ErpWorkspace({ title, description, actions, children }: ErpWorkspaceProps) {
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (state === "UNAUTHENTICATED") {
      router.replace(`/?returnUrl=${encodeURIComponent(pathname || "/erp")}`);
    }
  }, [pathname, router, state]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }
  if (state !== "AUTHENTICATED") return <AuthLoadingState phase="workspace" />;

  return (
    <ExecutiveShell>
      <main className={styles.content} dir="rtl">
        <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>SANAD ERP</p>
            <h1 className={styles.pageTitle}>{title}</h1>
            {description ? <p className={styles.pageDescription}>{description}</p> : null}
          </div>
          {actions ? <div className={styles.headerActions}>{actions}</div> : null}
        </header>

        <nav className={styles.nav} aria-label="التنقل داخل ERP">
          {ERP_NAV.map((item) => {
            const active = item.href === "/erp" ? pathname === item.href : pathname?.startsWith(item.href);
            return (
              <Link key={item.href} href={item.href} className={`${styles.navLink} ${active ? styles.navLinkActive : ""}`}>
                {item.label}
              </Link>
            );
          })}
        </nav>

        {children}
      </main>
    </ExecutiveShell>
  );
}
