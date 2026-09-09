"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import { ScpEmpty } from "../../_components/ScpStates";
import styles from "../../scp.module.css";

/**
 * R0C-12 G6-R4 — entitlements section of the subscription detail page
 * (design §8/§9 detail contract). Renders the plan-derived ∪ item-derived
 * entitlement rows served by the backend detail read model.
 */
export interface ScpEntitlementRow {
  source: string;
  moduleCode: string | null;
  moduleName: string | null;
  capabilityCode: string | null;
  moduleEnabled: boolean | null;
  booleanValue: boolean | null;
  capabilityValue?: string | null;
  limitValue: number | null;
  quotaValue: number | null;
  quotaPeriod: string | null;
}

export function SubscriptionEntitlementsSection({
  entitlements,
}: {
  entitlements: ScpEntitlementRow[];
}) {
  const { t } = useI18n();
  return (
    <section className={styles.panel} aria-labelledby="scp-entitlements-heading">
      <h2 id="scp-entitlements-heading" className={styles.pageSubtitle}>
        {t("scp.detail.entitlements.title")}
      </h2>
      {entitlements.length === 0 ? (
        <ScpEmpty message={t("scp.detail.entitlements.none")} />
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">{t("scp.detail.entitlements.module")}</th>
                <th scope="col">{t("scp.detail.entitlements.capability")}</th>
                <th scope="col">{t("scp.detail.entitlements.source")}</th>
                <th scope="col">{t("scp.detail.entitlements.value")}</th>
              </tr>
            </thead>
            <tbody>
              {entitlements.map((row, index) => (
                <tr key={`${row.source}-${row.capabilityCode ?? index}-${index}`}>
                  <td data-label={t("scp.detail.entitlements.module")}>
                    {row.moduleName ?? row.moduleCode ?? "—"}
                  </td>
                  <td data-label={t("scp.detail.entitlements.capability")}>
                    {row.capabilityCode ?? "—"}
                  </td>
                  <td data-label={t("scp.detail.entitlements.source")}>
                    {row.source === "PLAN"
                      ? t("scp.detail.entitlements.source.plan")
                      : t("scp.detail.entitlements.source.product")}
                  </td>
                  <td data-label={t("scp.detail.entitlements.value")}>
                    {row.limitValue !== null && row.limitValue !== undefined
                      ? t("scp.detail.entitlements.limit", { count: row.limitValue })
                      : row.booleanValue === true
                        ? t("scp.detail.entitlements.enabled")
                        : row.booleanValue === false
                          ? t("scp.detail.entitlements.disabled")
                          : row.quotaValue !== null && row.quotaValue !== undefined
                            ? `${row.quotaValue} / ${row.quotaPeriod ?? ""}`
                            : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
