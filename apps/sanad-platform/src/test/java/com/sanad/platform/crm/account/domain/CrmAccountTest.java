package com.sanad.platform.crm.account.domain;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrmAccountTest {

    @Test
    void createsAccountWithGlobalPreferences() {
        UUID actor = UUID.randomUUID();
        Tenant tenant = new Tenant("Global Tenant", "global-tenant", TenantStatus.ACTIVE);
        CrmAccount account = new CrmAccount(
                tenant,
                "شركة عالمية",
                CrmAccountType.BUSINESS,
                actor,
                "sar",
                "ar-SA",
                "Asia/Riyadh",
                "MANUAL",
                actor
        );

        assertEquals("شركة عالمية", account.getDisplayName());
        assertEquals("SAR", account.getPrimaryCurrencyCode());
        assertEquals("ar-SA", account.getPreferredLocale());
        assertEquals(CrmAccountStatus.ACTIVE, account.getLifecycleStatus());
    }

    @Test
    void archivesWithoutDeletingTheAggregate() {
        UUID actor = UUID.randomUUID();
        Tenant tenant = new Tenant("Global Tenant", "global-tenant", TenantStatus.ACTIVE);
        CrmAccount account = new CrmAccount(
                tenant, "Acme", CrmAccountType.BUSINESS,
                actor, "USD", "en-US", "UTC", "IMPORT", actor);

        account.archive(actor);

        assertEquals(CrmAccountStatus.ARCHIVED, account.getLifecycleStatus());
    }
}
