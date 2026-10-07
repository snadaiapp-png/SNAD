package com.sanad.platform.organization.legalentity;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmployerContextBootstrapServiceTest {

    private OrganizationRepository organizationRepository;
    private LegalEntityRepository legalEntityRepository;
    private LegalEntityOrganizationEligibilityRepository eligibilityRepository;
    private EmployerContextBootstrapService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID legalEntityId = UUID.randomUUID();
    private final LocalDate effectiveDate = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        legalEntityRepository = mock(LegalEntityRepository.class);
        eligibilityRepository = mock(LegalEntityOrganizationEligibilityRepository.class);
        service = new EmployerContextBootstrapService(
                organizationRepository,
                legalEntityRepository,
                eligibilityRepository);

        Organization organization = mock(Organization.class);
        when(organization.getStatus()).thenReturn(OrganizationStatus.ACTIVE);
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(organization));
    }

    @Test
    void createsMissingLinkOnlyWhenTenantHasExactlyOneActiveLegalEntity() {
        LegalEntity legalEntity = activeLegalEntity(legalEntityId);
        LegalEntityOrganizationEligibility link = activeLink(legalEntityId);

        when(eligibilityRepository.findActiveForOrganizationOn(tenantId, organizationId, effectiveDate))
                .thenReturn(List.of(), List.of(link));
        when(legalEntityRepository.findActiveByTenantId(tenantId))
                .thenReturn(List.of(legalEntity));
        when(eligibilityRepository.createActive(
                tenantId, organizationId, legalEntityId, effectiveDate))
                .thenReturn(link);

        var result = service.ensureSingleActiveEmployerContext(
                tenantId, organizationId, effectiveDate);

        assertThat(result.legalEntity().id()).isEqualTo(legalEntityId);
        assertThat(result.bootstrapped()).isTrue();
        verify(eligibilityRepository).createActive(
                tenantId, organizationId, legalEntityId, effectiveDate);
    }

    @Test
    void reusesExistingEffectiveLinkIdempotently() {
        LegalEntity legalEntity = activeLegalEntity(legalEntityId);
        LegalEntityOrganizationEligibility link = activeLink(legalEntityId);

        when(eligibilityRepository.findActiveForOrganizationOn(tenantId, organizationId, effectiveDate))
                .thenReturn(List.of(link));
        when(legalEntityRepository.findByTenantIdAndId(tenantId, legalEntityId))
                .thenReturn(Optional.of(legalEntity));

        var result = service.ensureSingleActiveEmployerContext(
                tenantId, organizationId, effectiveDate);

        assertThat(result.legalEntity().id()).isEqualTo(legalEntityId);
        assertThat(result.bootstrapped()).isFalse();
        verify(eligibilityRepository, never()).createActive(any(), any(), any(), any());
    }

    @Test
    void failsClosedWhenTenantHasMultipleActiveLegalEntities() {
        when(eligibilityRepository.findActiveForOrganizationOn(tenantId, organizationId, effectiveDate))
                .thenReturn(List.of());
        when(legalEntityRepository.findActiveByTenantId(tenantId))
                .thenReturn(List.of(
                        activeLegalEntity(UUID.randomUUID()),
                        activeLegalEntity(UUID.randomUUID())));

        assertThatThrownBy(() -> service.ensureSingleActiveEmployerContext(
                tenantId, organizationId, effectiveDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one ACTIVE legal entity");

        verify(eligibilityRepository, never()).createActive(any(), any(), any(), any());
    }

    @Test
    void failsClosedWhenOrganizationAlreadyMapsToMultipleDistinctLegalEntities() {
        when(eligibilityRepository.findActiveForOrganizationOn(tenantId, organizationId, effectiveDate))
                .thenReturn(List.of(
                        activeLink(UUID.randomUUID()),
                        activeLink(UUID.randomUUID())));

        assertThatThrownBy(() -> service.ensureSingleActiveEmployerContext(
                tenantId, organizationId, effectiveDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ambiguous");

        verify(eligibilityRepository, never()).createActive(any(), any(), any(), any());
    }

    private LegalEntity activeLegalEntity(UUID id) {
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        return new LegalEntity(
                id, tenantId, "LE-" + id.toString().substring(0, 8),
                "G2 Employer", "SA", "SA", LegalEntityStatus.ACTIVE, now, now);
    }

    private LegalEntityOrganizationEligibility activeLink(UUID leId) {
        return new LegalEntityOrganizationEligibility(
                UUID.randomUUID(), tenantId, organizationId, leId,
                effectiveDate, null, "ACTIVE", Instant.parse("2026-10-08T00:00:00Z"));
    }
}
