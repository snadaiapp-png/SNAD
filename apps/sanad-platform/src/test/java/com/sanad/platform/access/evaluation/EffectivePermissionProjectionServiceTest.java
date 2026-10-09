package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.relationship.AccessRelationshipService;
import com.sanad.platform.access.relationship.HrScopedRelationshipResolver;
import com.sanad.platform.access.relationship.SubjectRelationshipRepository;
import com.sanad.platform.hr.security.HrResourceContextResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Wave 1 Task 12 runtime contract. */
class EffectivePermissionProjectionServiceTest {

    @Test
    void projectionAndRelationshipServicesExposeCanonicalRuntimeSurface() throws Exception {
        assertThat(EffectivePermissionProjectionService.class
                .getMethod("rebuild", UUID.class, UUID.class)).isNotNull();
        assertThat(EffectivePermissionProjectionService.class
                .getMethod("list", UUID.class, UUID.class)).isNotNull();
        assertThat(EffectivePermissionProjectionService.class
                .getMethod("listCurrent", UUID.class, UUID.class)).isNotNull();
        assertThat(SubjectRelationshipRepository.class
                .getMethod("hasRelationship", UUID.class, UUID.class,
                        String.class, String.class, UUID.class)).isNotNull();
        assertThat(AccessRelationshipService.class
                .getMethod("list", UUID.class, UUID.class)).isNotNull();
    }

    @Test
    void hrScopedResolverDelegatesDirectReportPredicateWithoutReimplementingHrLogic() {
        HrResourceContextResolver hr = mock(HrResourceContextResolver.class);
        HrScopedRelationshipResolver resolver = new HrScopedRelationshipResolver(hr);
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID employment = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 29);
        when(hr.isDirectReport(tenant, actor, employment, date)).thenReturn(true);

        assertThat(resolver.matches(tenant, actor, "DIRECT_REPORTS", employment, null, date))
                .isTrue();
        verify(hr).isDirectReport(tenant, actor, employment, date);
    }

    @Test
    void hrScopedResolverFailsClosedForUnknownScope() {
        HrScopedRelationshipResolver resolver =
                new HrScopedRelationshipResolver(mock(HrResourceContextResolver.class));
        assertThat(resolver.matches(UUID.randomUUID(), UUID.randomUUID(),
                "UNKNOWN_SCOPE", UUID.randomUUID(), null, LocalDate.now())).isFalse();
    }
}
