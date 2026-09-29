package com.sanad.platform.security.authorization;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Wave 1 Task 13: an authorization change must evict cached decisions immediately. */
class AuthorizationInvalidationListenerTest {

    @Test
    void userScopedEventEvictsEveryCachedDecisionForSubject() {
        AuthorizationDecisionCache cache = mock(AuthorizationDecisionCache.class);
        AuthorizationInvalidationListener listener = new AuthorizationInvalidationListener(cache);
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        listener.onChanged(new AuthorizationChangedEvent(
                tenant, user, "USER_OVERRIDE_CHANGED", 7L));

        verify(cache).invalidateSubject(tenant, user);
    }

    @Test
    void eventContractCarriesVersionSoStaleAllowCannotBeReused() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        AuthorizationChangedEvent event = new AuthorizationChangedEvent(
                tenant, user, "USER_ROLE_CHANGED", 11L);
        assertThat(event.tenantId()).isEqualTo(tenant);
        assertThat(event.userId()).isEqualTo(user);
        assertThat(event.authorizationVersion()).isEqualTo(11L);
    }
}
