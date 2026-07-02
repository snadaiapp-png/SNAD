package com.sanad.platform.crm.account.domain;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrmAccountTest {

    @Test
    void createsAccountWithNormalizedGlobalPreferences() {
        UUID actor = UUID.randomUUID();
        Tenant tenant = new Tenant("Global Tenant", "global-tenant", TenantStatus.ACTIVE);
        CrmAccount account = new CrmAccount(
                tenant,
                "  شركة عالمية  ",
                CrmAccountType.BUSINESS,
                actor,
                " sar ",
                "ar-sa",
                "Asia/Riyadh",
                " MANUAL ",
                actor
        );

        assertEquals("شركة عالمية", account.getDisplayName());
        assertEquals("SAR", account.getPrimaryCurrencyCode());
        assertEquals("ar-SA", account.getPreferredLocale());
        assertEquals("Asia/Riyadh", account.getTimeZone());
        assertEquals("MANUAL", account.getSource());
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
        account.archive(actor);

        assertEquals(CrmAccountStatus.ARCHIVED, account.getLifecycleStatus());
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new CrmAccount(
                tenant(), "Acme", CrmAccountType.BUSINESS,
                null, "US", "en-US", "UTC", null, UUID.randomUUID()));
    }

    @Test
    void rejectsInvalidLocale() {
        assertThrows(IllegalArgumentException.class, () -> new CrmAccount(
                tenant(), "Acme", CrmAccountType.BUSINESS,
                null, "USD", "not_a_locale", "UTC", null, UUID.randomUUID()));
    }

    @Test
    void rejectsInvalidTimeZone() {
        assertThrows(IllegalArgumentException.class, () -> new CrmAccount(
                tenant(), "Acme", CrmAccountType.BUSINESS,
                null, "USD", "en-US", "Mars/Capital", null, UUID.randomUUID()));
    }

    private Tenant tenant() {
        return new Tenant("Global Tenant", "global-tenant", TenantStatus.ACTIVE);
    }
}
