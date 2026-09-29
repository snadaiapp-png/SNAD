package com.sanad.platform.platformiam;

import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.service.PlatformMembershipService;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformMembershipServiceRlsTest {

    private static final UUID CONTROL_TENANT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void activeMembershipReadBindsTrustedControlTenantBeforeRepositoryQuery() {
        PlatformMembershipRepository memberships = mock(PlatformMembershipRepository.class);
        TenantRlsTransactionContext rlsContext = mock(TenantRlsTransactionContext.class);
        PlatformMembershipService service = new PlatformMembershipService(memberships, rlsContext);
        PlatformMembership membership = activeMembership();
        when(memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership));

        PlatformMembership result = service.requireActive(CONTROL_TENANT, USER_ID);

        assertThat(result).isSameAs(membership);
        InOrder order = inOrder(rlsContext, memberships);
        order.verify(rlsContext).applyForCurrentTransaction(CONTROL_TENANT);
        order.verify(memberships).findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID);
    }

    private static PlatformMembership activeMembership() {
        Instant now = Instant.parse("2026-09-29T12:00:00Z");
        return new PlatformMembership(
                UUID.randomUUID(), CONTROL_TENANT, USER_ID, PlatformMembershipStatus.ACTIVE,
                now.minusSeconds(600), now.minusSeconds(300), null, null, null,
                USER_ID, USER_ID, null, now.minusSeconds(600), now);
    }
}
