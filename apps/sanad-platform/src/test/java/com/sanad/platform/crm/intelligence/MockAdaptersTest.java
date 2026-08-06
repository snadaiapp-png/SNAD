package com.sanad.platform.crm.intelligence;

import com.sanad.platform.crm.intelligence.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CRM-010 mock external data adapters.
 * Verifies deterministic synthetic data generation and availability flag.
 */
class MockAdaptersTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Test
    void mockErp_returnsAvailableSnapshotWithDeterministicData() {
        MockErpDataAdapter adapter = new MockErpDataAdapter();
        var snapshot = adapter.loadCustomerSnapshot(TENANT_ID, ACCOUNT_ID);

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(snapshot.totalRevenue()).isGreaterThanOrEqualTo(50000);
        assertThat(snapshot.orderCount()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void mockErp_isDeterministicForSameAccountId() {
        MockErpDataAdapter adapter = new MockErpDataAdapter();
        var s1 = adapter.loadCustomerSnapshot(TENANT_ID, ACCOUNT_ID);
        var s2 = adapter.loadCustomerSnapshot(TENANT_ID, ACCOUNT_ID);

        assertThat(s1.totalRevenue()).isEqualTo(s2.totalRevenue());
        assertThat(s1.orderCount()).isEqualTo(s2.orderCount());
    }

    @Test
    void mockHrm_returnsAvailableSnapshot() {
        MockHrmDataAdapter adapter = new MockHrmDataAdapter();
        var snapshot = adapter.loadAccountTeam(TENANT_ID, ACCOUNT_ID);

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.accountManagerName()).isNotBlank();
        assertThat(snapshot.teamSize()).isGreaterThan(0);
    }

    @Test
    void mockPos_returnsAvailableSnapshot() {
        MockPosDataAdapter adapter = new MockPosDataAdapter();
        var snapshot = adapter.loadCustomerSnapshot(TENANT_ID, ACCOUNT_ID);

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.preferredStore()).isNotBlank();
        assertThat(snapshot.transactionCount30d()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mockAccounting_returnsAvailableSnapshot() {
        MockAccountingDataAdapter adapter = new MockAccountingDataAdapter();
        var snapshot = adapter.loadSnapshot(TENANT_ID, ACCOUNT_ID);

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.creditRating()).isNotBlank();
        assertThat(snapshot.revenueYtd()).isGreaterThan(0);
    }

    @Test
    void mockCommerce_returnsAvailableSnapshot() {
        MockCommerceDataAdapter adapter = new MockCommerceDataAdapter();
        var snapshot = adapter.loadSnapshot(TENANT_ID, ACCOUNT_ID);

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.preferredChannel()).isNotBlank();
        assertThat(snapshot.productCategories()).isNotEmpty();
    }

    @Test
    void erpSnapshotUnavailable_factoryReturnsUnavailable() {
        var snapshot = com.sanad.platform.crm.intelligence.domain.ErpDataPort.ErpCustomerSnapshot.unavailable(ACCOUNT_ID);
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.totalRevenue()).isZero();
    }

    @Test
    void hrmSnapshotUnavailable_factoryReturnsUnavailable() {
        var snapshot = com.sanad.platform.crm.intelligence.domain.HrmDataPort.HrmAccountTeamSnapshot.unavailable(ACCOUNT_ID);
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.teamSize()).isZero();
    }
}
