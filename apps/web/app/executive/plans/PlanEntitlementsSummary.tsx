"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import { ScpEmpty } from "../_components/ScpStates";
import styles from "../scp.module.css";
import type { PlanModuleEntitlementResponse } from "@/lib/api/executive-api";

/**
 * R0C-12 G6-R5 — "Plans & Pricing": per-plan module entitlement summary so
 * Entitlement is represented explicitly next to Plan/Version/Price (design
 * §9). Consumes the existing `/plans/{id}/modules` contract.
 */
export function PlanEntitlementsSummary({
  modules,
}: {
  modules: PlanModuleEntitlementResponse[];
}) {
  const { t } = useI18n();
  return (
    <div>
      <h3 className={styles.pageSubtitle}>{t("scp.plans.entitlements.title")}</h3>
      {modules.length === 0 ? (
        <ScpEmpty message={t("scp.plans.entitlements.none")} />
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">{t("scp.plans.entitlements.module")}</th>
                <th scope="col">{t("scp.plans.entitlements.capability")}</th>
                <th scope="col">{t("scp.plans.entitlements.effect")}</th>
              </tr>
            </thead>
            <tbody>
              {modules.map((module) => (
                <tr key={module.id}>
                  <td data-label={t("scp.plans.entitlements.module")}>{module.moduleCode}</td>
                  <td data-label={t("scp.plans.entitlements.capability")}>
                    {module.capabilityCode ?? "—"}
                  </td>
                  <td data-label={t("scp.plans.entitlements.effect")}>
                    {module.limitValue !== null && module.limitValue !== undefined
                      ? t("scp.plans.entitlements.limit", { count: module.limitValue })
                      : module.quotaValue !== null && module.quotaValue !== undefined
                        ? `${module.quotaValue} / ${module.quotaPeriod ?? ""}`
                        : module.capabilityValue ?? (module.moduleEnabled
                          ? t("scp.plans.entitlements.enabled")
                          : t("scp.plans.entitlements.disabled"))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
