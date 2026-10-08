package com.sanad.platform.user.access;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationIamRegistryRouteTest {

    @Test
    void resolvesCurrentAndFutureModulesFromRegistryMetadataWithoutFrontendHardcoding() {
        var future = new ApplicationIamRegistration(
                "FUTURE_LEDGER",
                "Future Ledger",
                "دفتر المستقبل",
                "ACTIVE",
                "1",
                Set.of("FUTURE_LEDGER"),
                Set.of("TENANT"),
                Set.of("FUTURE_LEDGER.READ"),
                Set.of(),
                Map.of("routeRoots", List.of("future-ledger", "future-ledger-v2")));

        assertThat(ApplicationIamRegistryRepository.normalizeRouteRoot("/future-ledger/dashboard"))
                .isEqualTo("future-ledger");
        assertThat(ApplicationIamRegistryRepository.matchesRoute(future, "future-ledger")).isTrue();
        assertThat(ApplicationIamRegistryRepository.matchesRoute(future, "future-ledger-v2")).isTrue();
        assertThat(ApplicationIamRegistryRepository.matchesRoute(future, "crm")).isFalse();
    }
}
