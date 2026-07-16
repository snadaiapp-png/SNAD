"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi, type CrmAccount } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { ContactRelationshipWorkspace } from "./contact-relationship-workspace";
import { CrmLoading } from "./crm-loading";
import styles from "../crm.module.css";

export function ContactRelationshipRouteExtension({ contactId }: { contactId: string }) {
  const [accounts, setAccounts] = useState<CrmAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setAccounts(await crmApi.accounts());
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setAccounts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  if (loading) return <CrmLoading rows={2} />;
  if (error) return <div className={styles.error} role="alert">{error}</div>;
  return <ContactRelationshipWorkspace contactId={contactId} accounts={accounts} />;
}
