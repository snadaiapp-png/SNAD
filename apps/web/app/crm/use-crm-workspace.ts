"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  crmApi,
  type CrmAccount,
  type CrmActivity,
  type CrmContact,
  type CrmDashboard,
  type CrmLead,
  type CrmOpportunity,
  type CrmPipeline,
  type CrmStage,
  type Customer360,
} from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";

export type CrmTab = "accounts" | "contacts" | "leads" | "sales" | "activities";

export function useCrmWorkspace() {
  const router = useRouter();
  const { state, me } = useAuth();
  const [tab, setTab] = useState<CrmTab>("accounts");
  const [dashboard, setDashboard] = useState<CrmDashboard | null>(null);
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [contacts, setContacts] = useState<CrmContact[]>([]);
  const [leads, setLeads] = useState<CrmLead[]>([]);
  const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
  const [stages, setStages] = useState<Record<string, CrmStage[]>>({});
  const [opportunities, setOpportunities] = useState<CrmOpportunity[]>([]);
  const [activities, setActivities] = useState<CrmActivity[]>([]);
  const [customer, setCustomer] = useState<Customer360 | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  const reload = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const [
        nextDashboard,
        nextAccounts,
        nextContacts,
        nextLeads,
        nextPipelines,
        nextOpportunities,
        nextActivities,
      ] = await Promise.all([
        crmApi.dashboard(),
        crmApi.accounts(),
        crmApi.contacts(),
        crmApi.leads(),
        crmApi.pipelines(),
        crmApi.opportunities(),
        crmApi.activities(),
      ]);
      setDashboard(nextDashboard);
      setAccounts(nextAccounts);
      setContacts(nextContacts);
      setLeads(nextLeads);
      setPipelines(nextPipelines);
      setOpportunities(nextOpportunities);
      setActivities(nextActivities);
      const entries = await Promise.all(
        nextPipelines.map(async (pipeline) => [pipeline.id, await crmApi.stages(pipeline.id)] as const),
      );
      setStages(Object.fromEntries(entries));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload, state]);

  const accountNames = useMemo(
    () => new Map(accounts.map((account) => [account.id, account.display_name])),
    [accounts],
  );

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(successMessage);
      await reload();
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  async function openCustomer(accountId: string) {
    setBusy(true);
    setError("");
    try {
      setCustomer(await crmApi.customer360(accountId));
    } catch (reason) {
      setError(toUserFacingError(reason).message);
    } finally {
      setBusy(false);
    }
  }

  return {
    router,
    state,
    me,
    tab,
    setTab,
    dashboard,
    accounts,
    contacts,
    leads,
    pipelines,
    stages,
    opportunities,
    activities,
    customer,
    setCustomer,
    busy,
    error,
    notice,
    accountNames,
    reload,
    mutate,
    openCustomer,
  };
}
