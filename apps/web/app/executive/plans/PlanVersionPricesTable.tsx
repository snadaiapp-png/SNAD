"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import { useScpFormat } from "../_components/format";
import { ScpEmpty } from "../_components/ScpStates";
import styles from "../scp.module.css";
import type { Price } from "@/lib/api/scp-api";

/**
 * R0C-12 G6-R5 — "Plans & Pricing": per-version price rows (country,
 * currency, model, interval, amounts, effective window) so Price is
 * represented explicitly, not only as create-form fields (design §9).
 * Money renders through the shared minor-units formatter — no float math.
 */
export function PlanVersionPricesTable({ prices }: { prices: Price[] }) {
  const { t } = useI18n();
  const { money, day } = useScpFormat();
  return (
    <div>
      <h3 className={styles.pageSubtitle}>{t("scp.plans.prices.title")}</h3>
      {prices.length === 0 ? (
        <ScpEmpty message={t("scp.plans.prices.none")} />
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">{t("scp.plans.prices.country")}</th>
                <th scope="col">{t("scp.plans.prices.model")}</th>
                <th scope="col">{t("scp.plans.prices.interval")}</th>
                <th scope="col">{t("scp.plans.prices.base")}</th>
                <th scope="col">{t("scp.plans.prices.unit")}</th>
                <th scope="col">{t("scp.plans.prices.effective")}</th>
              </tr>
            </thead>
            <tbody>
              {prices.map((price) => (
                <tr key={price.id}>
                  <td data-label={t("scp.plans.prices.country")}>
                    {price.countryCode} · {price.currencyCode}
                  </td>
                  <td data-label={t("scp.plans.prices.model")}>{price.priceModel}</td>
                  <td data-label={t("scp.plans.prices.interval")}>{price.billingInterval}</td>
                  <td data-label={t("scp.plans.prices.base")}>
                    {money(price.baseAmountMinor, price.currencyCode)}
                  </td>
                  <td data-label={t("scp.plans.prices.unit")}>
                    {price.unitAmountMinor !== null && price.unitAmountMinor !== undefined
                      ? money(price.unitAmountMinor, price.currencyCode)
                      : "—"}
                  </td>
                  <td data-label={t("scp.plans.prices.effective")}>
                    {day(price.effectiveFrom)}
                    {" → "}
                    {price.effectiveTo ? day(price.effectiveTo) : "—"}
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
