package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.service.LastAdminGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedRoleGuardTest {

    @Test
    void protectedMutationRequiresPlatformManage() {
        ProtectedRoleGuard guard = new ProtectedRoleGuard(null);
        assertThatThrownBy(() -> guard.assertProtectedMutation("TENANT_ADMIN", false))
                .isInstanceOf(AccessConflictException.class);
        assertThatThrownBy(() -> guard.assertProtectedMutation("PLATFORM_ADMIN", false))
                .isInstanceOf(AccessConflictException.class);
        assertThatCode(() -> guard.assertProtectedMutation("TENANT_ADMIN", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.assertProtectedMutation("AGENT_CUSTOM_ADMIN", false))
                .doesNotThrowAnyException();
    }

    @Test
    void lastAdminSimulationRejectsZeroRemainingAdminsBeforeWrite() {
        LastAdminGuard guard = new LastAdminGuard(null);
        assertThatThrownBy(() -> guard.assertTenantSurvives(0L))
                .isInstanceOf(AccessConflictException.class);
        assertThatCode(() -> guard.assertTenantSurvives(1L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertTenantSurvives(2L)).doesNotThrowAnyException();
    }
}
