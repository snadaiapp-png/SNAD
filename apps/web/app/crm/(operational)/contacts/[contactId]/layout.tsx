"use client";

import type { ReactNode } from "react";
import { useParams } from "next/navigation";
import { ContactRelationshipRouteExtension } from "../../../components/contact-relationship-route-extension";
import styles from "../../../crm.module.css";

export default function ContactDetailLayout({ children }: { children: ReactNode }) {
  const params = useParams<{ contactId: string }>();
  const contactId = params?.contactId ?? "";
  return (
    <>
      {children}
      {contactId ? (
        <div className={styles.contentInner}>
          <ContactRelationshipRouteExtension contactId={contactId} />
        </div>
      ) : null}
    </>
  );
}
