package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.config.BillingProviderModeGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R0C13G09ProviderModeContractTest {

    @Test
    void liveModeWithoutAuthorityFailsClosed() {
        BillingProviderModeGuard guard = new BillingProviderModeGuard();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sanad.subscription.billing.provider.mode", "LIVE");

        assertThatThrownBy(() ->
                guard.postProcessEnvironment(environment, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE")
                .hasMessageContaining("not authorized");
    }
}
