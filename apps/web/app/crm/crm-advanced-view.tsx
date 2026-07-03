"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  crmApi,
  type CrmAccount,
  type CrmOpportunity,
  type CrmPipeline,
  type CrmStage,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { CrmPipelineBoard } from "./crm-pipeline-board";
import { CrmVirtualTable } from "./crm-virtual-table";
import { formatNumber } from "./crm-view-utils";
import styles from "./crm.module.css";

export function CrmAdvancedView() {
  const { state } = useAuth();
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<Record<string, CrmStage[]>>({});
  const [opportunities, setOpportunities] = useState<CrmOpportunity[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    if (state !== "AUTHENTICATED") return;
    setBusy(true);
    setError("");
    try {
      const [nextAccounts, nextPipelines, nextOpportunities] = await Promise.all([
        crmApi.accounts(),
        crmApi.pipelines(),
        crmApi.opportunities(),
      ]);
      const entries = await Promise.all(
        nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
      );
      setAccounts(nextAccounts);
      setPipelines(nextPipelines);
      setOpportunities(nextOpportunities);
      setStages(Object.fromEntries(entries));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }, [state]);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  const accountNames = useMemo(
    () => new Map(accounts.map((account) => [account.id, account.display_name])),
    [accounts],
  );

  if (state !== "AUTHENTICATED") return null;

  return (
    <section className={styles.advancedView} dir="rtl" aria-label="عرض CRM المتقدم">
      {error ? <div className={styles.error} role="alert">{error}</div> : null}
      <CrmPipelineBoard
        pipelines={pipelines}
        stages={stages}
        opportunities={opportunities}
        accountNames={accountNames}
        busy={busy}
        onMove={async (opportunityId, stageId) => {
          setBusy(true);
          try {
            await crmApi.moveOpportunity(opportunityId, stageId);
            await reload();
          } catch (reason) {
            setError(toUserFacingError(reason).message);
          } finally {
            setBusy(false);
          }
        }}
      />
      <div className={styles.listCard}>
        <div className={styles.pipelineToolbar}>
          <h2>قائمة الفرص الافتراضية</h2>
          <button type="button" disabled={busy} onClick={() => void reload()}>تحديث</button>
        </div>
        <CrmVirtualTable<CrmOpportunity>
          rows={opportunities}
          label="جدول فرص CRM الافتراضي"
          columns={[
            { key: "name", header: "الفرصة", render: (item) => item.name },
            { key: "account", header: "الحساب", render: (item) => accountNames.get(item.account_id) ?? "—" },
            { key: "amount", header: "القيمة", render: (item) => `${formatNumber(item.amount)} ${item.currency_code}` },
            { key: "status", header: "الحالة", render: (item) => item.status },
          ]}
        />
      </div>
    </section>
  );
}
