"use client";

import type { ReactNode } from "react";
import { ScpNav } from "./ScpNav";
import { ScpAuthGate } from "./ScpStates";
import styles from "../scp.module.css";

/**
 * Control-plane page frame: session gate, sidebar navigation and the
 * responsive two-column layout (stacked on tablet/mobile via CSS).
 */
export function ScpLayout({ children }: { children: ReactNode }) {
  return (
    <ScpAuthGate>
      <div className={styles.scpLayout}>
        <ScpNav />
        {children}
      </div>
    </ScpAuthGate>
  );
}
