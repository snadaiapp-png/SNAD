"use client";

import { CrmExecutionBoard } from "../../crm-execution-board";
import styles from "../../crm.module.css";

/**
 * CRM Execution Board Page
 * ------------------------
 * Displays the full G0-G10 execution plan with parallel wave visualization,
 * group progress cards, and task-level details.
 *
 * This page was previously accessible via the CRM Command Center's
 * "Execution Board" tab. It was restored as a standalone route during
 * the G3 production release recovery phase.
 */
export default function CrmExecutionPage() {
  return (
    <div className={styles.contentInner}>
      <CrmExecutionBoard />
    </div>
  );
}
