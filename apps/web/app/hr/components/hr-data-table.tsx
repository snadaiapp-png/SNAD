"use client";

/**
 * HR data table — shared table component (G1-T11 refactor).
 *
 * Design:
 *   - Semantic <table> with <caption> for screen readers (G0 a11y standard).
 *   - Logical CSS only (no physical left/right). RTL is handled by the dir
 *     attribute on the wrapping div + logical CSS properties in hr.module.css.
 *   - Column definitions: { key, header, render?, align? }. The render
 *     function receives the row and returns a ReactNode — caller controls
 *     cell content (badge, action buttons, masked value, etc.).
 *   - Empty state: caller passes `emptyTitle` + `emptyAction`; rendered
 *     inside the table caption + tbody cell to keep the table semantics
 *     while being actionable (spec §14: "empty CTA").
 *   - Row actions are keyboard-accessible: each action is a real <button>
 *     with type="button"; no div-as-button anti-pattern.
 *   - No pagination internals here — caller passes already-paged data.
 *     This keeps the component dumb and testable.
 */

import type { ReactNode } from "react";
import styles from "../hr.module.css";
import { HrEmptyState } from "./hr-feedback";

export interface HrColumn<T> {
  key: string;
  /** Already i18n-resolved header label. */
  header: string;
  /** Cell renderer. Defaults to `String(row[key])`. */
  render?: (row: T) => ReactNode;
  /** Logical alignment: "start" | "end" | "center" (CSS handles RTL flip). */
  align?: "start" | "end" | "center";
}

interface HrDataTableProps<T> {
  /** Visible title for screen readers (rendered in <caption>). */
  caption: string;
  columns: HrColumn<T>[];
  rows: T[];
  /** Stable key extractor (typically the entity ID). */
  rowKey: (row: T) => string;
  /** Empty state — when rows.length === 0. */
  emptyTitle: string;
  emptyDescription?: string;
  emptyAction?: ReactNode;
  /** Optional caption-side override (default: top). */
  captionSide?: "top" | "bottom";
}

export function HrDataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  emptyTitle,
  emptyDescription,
  emptyAction,
  captionSide = "top",
}: HrDataTableProps<T>) {
  return (
    <div className={styles.hrTableWrap} dir="rtl">
      <table className={styles.hrTable} data-caption-side={captionSide}>
        <caption>{caption}</caption>
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} scope="col" data-align={col.align ?? "start"}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>
                <HrEmptyState title={emptyTitle} description={emptyDescription} action={emptyAction} />
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((col) => (
                  <td key={col.key} data-align={col.align ?? "start"}>
                    {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? "")}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
