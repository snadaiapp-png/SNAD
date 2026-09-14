"use client";

import { useState, type ReactNode } from "react";
import { ScpNav } from "./ScpNav";
import { ScpAuthGate } from "./ScpStates";
import { ScpAccessProvider } from "./ScpAccess";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../scp.module.css";

/**
 * Control-plane page frame: session gate, sidebar navigation and the
 * responsive two-column layout.
 *
 * R0C-12 G6-R3 (design §9): sidebar on desktop, collapsible on tablet,
 * drawer on mobile. The toggle button is visually hidden on desktop and
 * controls the nav slot via `data-open`; CSS collapses the slot on tablet
 * and renders the slot as a fixed overlay drawer (with an explicit backdrop
 * control) on mobile. Navigation through a link closes the drawer.
 */
export function ScpLayout({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const [navOpen, setNavOpen] = useState(false);

  const close = () => setNavOpen(false);

  return (
    <ScpAuthGate>
      {/* R0C-12 Blocker C: ONE access-check per console session — the nav and
          every page's mutation gates consume this shared capability state. */}
      <ScpAccessProvider>
        <div className={styles.scpLayout}>
        <button
          type="button"
          className={styles.navToggle}
          aria-expanded={navOpen}
          aria-controls="scp-sidebar-nav"
          onClick={() => setNavOpen((open) => !open)}
        >
          <span aria-hidden="true">☰</span>
          {t("scp.nav.openMenu")}
        </button>
        <div
          id="scp-sidebar-nav"
          className={styles.navSlot}
          data-open={navOpen ? "true" : "false"}
        >
          <ScpNav />
        </div>
        {children}
          {navOpen && (
            <button
              type="button"
              className={styles.navBackdrop}
              aria-label={t("scp.nav.closeMenu")}
              onClick={close}
            />
          )}
        </div>
      </ScpAccessProvider>
    </ScpAuthGate>
  );
}
