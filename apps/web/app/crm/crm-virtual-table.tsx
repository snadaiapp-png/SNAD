"use client";

import { type ReactNode, useMemo, useState } from "react";
import styles from "./crm.module.css";

export interface VirtualColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
}

interface VirtualTableProps<T extends { id: string }> {
  rows: T[];
  columns: Array<VirtualColumn<T>>;
  label: string;
  emptyMessage?: string;
  height?: number;
  rowHeight?: number;
}

export function visibleWindow(
  total: number,
  scrollTop: number,
  rowHeight: number,
  viewportHeight: number,
  overscan = 5,
) {
  const first = Math.max(0, Math.floor(scrollTop / rowHeight) - overscan);
  const visible = Math.ceil(viewportHeight / rowHeight) + overscan * 2;
  const last = Math.min(total, first + visible);
  return {
    first,
    last,
    top: first * rowHeight,
    bottom: Math.max(0, (total - last) * rowHeight),
  };
}

export function CrmVirtualTable<T extends { id: string }>({
  rows,
  columns,
  label,
  emptyMessage = "لا توجد سجلات.",
  height = 420,
  rowHeight = 58,
}: VirtualTableProps<T>) {
  const [scrollTop, setScrollTop] = useState(0);
  const window = useMemo(
    () => visibleWindow(rows.length, scrollTop, rowHeight, height),
    [height, rowHeight, rows.length, scrollTop],
  );
  const visibleRows = rows.slice(window.first, window.last);

  if (rows.length === 0) return <p className={styles.emptyState}>{emptyMessage}</p>;

  return (
    <div
      className={styles.virtualTableViewport}
      style={{ height }}
      onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
      role="region"
      aria-label={label}
      tabIndex={0}
    >
      <table className={styles.virtualTable} aria-rowcount={rows.length + 1}>
        <thead>
          <tr>{columns.map((column) => <th key={column.key}>{column.header}</th>)}</tr>
        </thead>
        <tbody>
          {window.top > 0 ? (
            <tr aria-hidden="true"><td colSpan={columns.length} style={{ height: window.top, padding: 0 }} /></tr>
          ) : null}
          {visibleRows.map((row, index) => (
            <tr key={row.id} aria-rowindex={window.first + index + 2} style={{ height: rowHeight }}>
              {columns.map((column) => <td key={column.key}>{column.render(row)}</td>)}
            </tr>
          ))}
          {window.bottom > 0 ? (
            <tr aria-hidden="true"><td colSpan={columns.length} style={{ height: window.bottom, padding: 0 }} /></tr>
          ) : null}
        </tbody>
      </table>
    </div>
  );
}
