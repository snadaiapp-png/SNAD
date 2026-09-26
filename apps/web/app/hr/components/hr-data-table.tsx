"use client";

/** Shared semantic HR table with a mobile card representation for dense data. */

import type { ReactNode } from "react";
import styles from "../hr.module.css";
import visualStyles from "./hr-g2-visual.module.css";
import { HrEmptyState } from "./hr-feedback";

export interface HrColumn<T> {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  align?: "start" | "end" | "center";
}

interface HrDataTableProps<T> {
  caption: string;
  columns: HrColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  emptyTitle: string;
  emptyDescription?: string;
  emptyAction?: ReactNode;
  captionSide?: "top" | "bottom";
}

function cellValue<T>(row: T, col: HrColumn<T>): ReactNode {
  return col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? "");
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
    <>
      <div className={`${styles.hrTableWrap} ${visualStyles.desktopTable}`}>
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
                <tr key={rowKey(row)} data-testid="hr-data-row">
                  {columns.map((col) => (
                    <td key={col.key} data-align={col.align ?? "start"}>
                      {cellValue(row, col)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <section className={visualStyles.mobileRecordList} data-testid="hr-mobile-records" aria-label={caption}>
        {rows.length === 0 ? (
          <HrEmptyState title={emptyTitle} description={emptyDescription} action={emptyAction} />
        ) : (
          rows.map((row) => (
            <article className={visualStyles.mobileRecord} data-testid="hr-mobile-record" key={rowKey(row)}>
              {columns.map((col) => (
                <div className={visualStyles.mobileRecordField} key={col.key}>
                  <span className={visualStyles.mobileRecordLabel}>{col.header}</span>
                  <div className={visualStyles.mobileRecordValue}>{cellValue(row, col)}</div>
                </div>
              ))}
            </article>
          ))
        )}
      </section>
    </>
  );
}
