"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpFormat } from "../_components/format";

/**
 * R0C-12 G6-R6 — usage gauges expose the metering period (design §9:
 * current/limit/%/period/reset/warning). Renders the MONTHLY aggregate
 * period served by the backend snapshot; renders nothing when absent so
 * metrics without usage history stay silent.
 */
export function UsagePeriodLabel({ periodStart }: { periodStart: string | null }) {
  const { t } = useI18n();
  const { day } = useScpFormat();
  if (!periodStart) return null;
  return <span>{t("scp.usage.period", { period: day(periodStart) })}</span>;
}
