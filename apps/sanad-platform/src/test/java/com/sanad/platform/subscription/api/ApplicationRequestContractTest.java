package com.sanad.platform.subscription.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRequestContractTest {

    @Test
    void applicationRequestExposesGovernedLifecycleStatus() {
        assertThat(Arrays.stream(ScpDtos.ApplicationRequest.class.getRecordComponents())
                .map(component -> component.getName()))
                .contains("status");
    }
}
