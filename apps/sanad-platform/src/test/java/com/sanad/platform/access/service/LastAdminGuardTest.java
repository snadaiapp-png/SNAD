package com.sanad.platform.access.service;

import com.sanad.platform.access.AccessConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LastAdminGuardTest {
    private final LastAdminGuard guard = new LastAdminGuard(null);

    @Test
    void zeroRemainingActiveAdminsIsRejectedBeforeMutation() {
        assertThatThrownBy(() -> guard.assertTenantSurvives(0L))
                .isInstanceOf(AccessConflictException.class);
    }

    @Test
    void oneOrMoreRemainingActiveAdminsSurvive() {
        assertThatCode(() -> guard.assertTenantSurvives(1L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertTenantSurvives(2L)).doesNotThrowAnyException();
    }
}
