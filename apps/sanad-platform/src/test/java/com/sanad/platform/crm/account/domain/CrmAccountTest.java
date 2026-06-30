package com.sanad.platform.crm.account.domain;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrmAccountTest {

    @Test
    void createsGlobalTenantScopedAccountAndNormalizesCurrency() {
        Tenant tenant = new Tenant("Test Tenant", "test-tenant", TenantStatus.ACTIVE);
        tenant.setId(UUID.randomUUID());
        UUID actorId = UUID.randomUUID();

        CrmAccount account = new CrmAccount(
                tenant, "شركة عالمية", CrmAccountType.BUSINESS,
                actorId, "sar", "ar-SA", "Asia/Riyadh", "MANUAL", actorId);

        assertEquals("شركة عالمية", account.getDisplayName());
        assertEquals("SAR", account.getPrimaryCurrencyCode());
        assertEquals(CrmAccountStatus.ACTIVE, account.getLifecycleStatus());
    }

    @Test
    void archiveRetainsTheRecordAndMarksLifecycle() {
        Tenant tenant = new Tenant("Test Tenant", "test-tenant", TenantStatus.ACTIVE);
        UUID actorId = UUID.randomUUID();
        CrmAccount account = new CrmAccount(
                tenant, "Acme", CrmAccountType.BUSINESS,
                actorId, "USD", "en-US", "UTC", "IMPORT", actorId);

        account.archive(actorId);

        assertEquals(CrmAccountStatus.ARCHIVED, account.getLifecycleStatus());
        assertNotNull(account.getArchivedAt());
    }
}
