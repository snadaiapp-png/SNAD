package com.sanad.platform.organization.legalentity;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalEntityServiceEmployerBootstrapTest {

    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private LegalEntityOrganizationEligibilityRepository eligibilityRepository;
    @Mock private OrganizationRepository organizationRepository;

    private LegalEntityService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final LocalDate effectiveDate = LocalDate.of(2026, 10, 9);

    @BeforeEach
    void setUp() {
        service = new LegalEntityService(
                legalEntityRepository,
                eligibilityRepository,
                organizationRepository);
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(mock(Organization.class)));
    }

    @Test
    void zeroActiveLinksBootstrapsCanonicalEmployerFoundation() {
        UUID legalEntityId = UUID.randomUUID();
        LegalEntity created = legalEntity(legalEntityId);
        LegalEntityOrganizationEligibility link = eligibility(legalEntityId);

        when(eligibilityRepository.findActiveForOrganizationOn(
                tenantId, organizationId, effectiveDate))
                .thenReturn(List.of(), List.of(link));
        when(legalEntityRepository.findByTenantIdAndCode(tenantId, "G2-ACCEPTANCE-LE"))
                .thenReturn(Optional.empty());
        when(legalEntityRepository.save(any(LegalEntity.class))).thenReturn(created);
        when(eligibilityRepository.ensureActive(
                tenantId, organizationId, legalEntityId, effectiveDate)).thenReturn(link);
        when(legalEntityRepository.findByTenantIdAndId(tenantId, legalEntityId))
                .thenReturn(Optional.of(created));

        LegalEntity resolved = service.bootstrapSingleActiveForOrganization(
                tenantId,
                organizationId,
                effectiveDate,
                "G2-ACCEPTANCE-LE",
                "G2 Acceptance Legal Entity",
                "SA",
                "SA");

        assertThat(resolved.id()).isEqualTo(legalEntityId);
        verify(legalEntityRepository).save(any(LegalEntity.class));
        verify(eligibilityRepository).ensureActive(
                tenantId, organizationId, legalEntityId, effectiveDate);
    }

    @Test
    void oneActiveLinkIsReusedWithoutMutation() {
        UUID legalEntityId = UUID.randomUUID();
        LegalEntity existing = legalEntity(legalEntityId);
        when(eligibilityRepository.findActiveForOrganizationOn(
                tenantId, organizationId, effectiveDate))
                .thenReturn(List.of(eligibility(legalEntityId)));
        when(legalEntityRepository.findByTenantIdAndId(tenantId, legalEntityId))
                .thenReturn(Optional.of(existing));

        LegalEntity resolved = service.bootstrapSingleActiveForOrganization(
                tenantId,
                organizationId,
                effectiveDate,
                "G2-ACCEPTANCE-LE",
                "G2 Acceptance Legal Entity",
                "SA",
                "SA");

        assertThat(resolved.id()).isEqualTo(legalEntityId);
        verify(legalEntityRepository, never()).save(any());
        verify(eligibilityRepository, never()).ensureActive(any(), any(), any(), any());
    }

    @Test
    void multipleActiveLinksFailClosedWithoutMutation() {
        UUID le1 = UUID.randomUUID();
        UUID le2 = UUID.randomUUID();
        when(eligibilityRepository.findActiveForOrganizationOn(
                tenantId, organizationId, effectiveDate))
                .thenReturn(List.of(eligibility(le1), eligibility(le2)));

        assertThatThrownBy(() -> service.bootstrapSingleActiveForOrganization(
                tenantId,
                organizationId,
                effectiveDate,
                "G2-ACCEPTANCE-LE",
                "G2 Acceptance Legal Entity",
                "SA",
                "SA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("found 2");

        verify(legalEntityRepository, never()).save(any());
        verify(eligibilityRepository, never()).ensureActive(any(), any(), any(), any());
    }

    private LegalEntity legalEntity(UUID id) {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        return new LegalEntity(
                id,
                tenantId,
                "G2-ACCEPTANCE-LE",
                "G2 Acceptance Legal Entity",
                "SA",
                "SA",
                LegalEntityStatus.ACTIVE,
                now,
                now);
    }

    private LegalEntityOrganizationEligibility eligibility(UUID legalEntityId) {
        return new LegalEntityOrganizationEligibility(
                UUID.randomUUID(),
                tenantId,
                organizationId,
                legalEntityId,
                effectiveDate,
                null,
                "ACTIVE",
                Instant.parse("2026-10-09T00:00:00Z"));
    }
}
