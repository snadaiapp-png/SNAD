"use client";

import type { ReactNode } from "react";
import { useParams } from "next/navigation";
import { AddressCommunicationWorkspace } from "../../../components/address-communication-workspace";
import { ContactRelationshipWorkspace } from "../../../components/contact-relationship-workspace";
import styles from "../../../crm.module.css";

export default function AccountDetailLayout({ children }: { children: ReactNode }) {
  const params = useParams<{ accountId: string }>();
  const accountId = params?.accountId ?? "";
  return (
    <>
      {children}
      {accountId ? (
        <div className={styles.contentInner}>
          <AddressCommunicationWorkspace accountId={accountId} />
          <ContactRelationshipWorkspace accountId={accountId} />
        </div>
      ) : null}
    </>
  );
}
