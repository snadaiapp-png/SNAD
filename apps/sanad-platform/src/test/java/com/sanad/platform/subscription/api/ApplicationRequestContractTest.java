package com.sanad.platform.subscription.api;

import jakarta.validation.constraints.Pattern;
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

    @Test
    void applicationRequestStatusPatternAdmitsTheWidenedLifecycle() throws Exception {
        // jakarta @Pattern does not target RECORD_COMPONENT — the constraint is
        // propagated to the record's backing field.
        java.lang.reflect.Field status = ScpDtos.ApplicationRequest.class.getDeclaredField("status");
        Pattern pattern = status.getAnnotation(Pattern.class);
        assertThat(pattern).as("status must be pattern-constrained").isNotNull();
        for (String allowed : new String[]{"ACTIVE", "INACTIVE", "DEPRECATED", "DRAFT", "ARCHIVED"}) {
            assertThat(allowed.matches(pattern.regexp()))
                    .as("status pattern must admit " + allowed)
                    .isTrue();
        }
        assertThat("DELETED".matches(pattern.regexp()))
                .as("status pattern must reject non-lifecycle values")
                .isFalse();
    }
}
