package com.sanad.platform.executive.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutivePlatformServiceArchiveContractTest {

    @SuppressWarnings("unchecked")
    @Test
    void archiveSoftDeleteMustBeReachableFromEveryNonArchivedTenantState() throws Exception {
        Field field = ExecutivePlatformService.class.getDeclaredField("TENANT_TRANSITIONS");
        field.setAccessible(true);
        Map<String, Set<String>> transitions = (Map<String, Set<String>>) field.get(null);

        for (String status : Set.of("PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED")) {
            assertThat(transitions.get(status))
                    .as("%s must support soft-delete transition to ARCHIVED", status)
                    .contains("ARCHIVED");
        }
        assertThat(transitions.get("ARCHIVED")).isEmpty();
    }
}
