package com.sanad.platform.crm.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.application.AddressCommunicationUseCases;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CommunicationMethodRecord;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CreateCommunicationMethodCommand;
import com.sanad.platform.crm.party.domain.LegacyAddressProjectionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressCommunicationUseCasesTest {
    private AddressCommunicationRepository repository;
    private AddressCommunicationUseCases useCases;

    @BeforeEach
    void setUp() {
        repository = mock(AddressCommunicationRepository.class);
        useCases = new AddressCommunicationUseCases(repository, mock(LegacyAddressProjectionPort.class),
                mock(AuditPort.class), mock(TimelineEventPort.class), new ObjectMapper());
    }

    @Test
    void normalizesSaudiMobileOnlyWhenCountryHintIsExplicit() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(repository.createCommunicationMethod(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    CreateCommunicationMethodCommand command = invocation.getArgument(4);
                    return method(ownerId, command);
                });

        CommunicationMethodRecord created = useCases.createCommunicationMethod(
                tenantId, actorId, "PERSON", ownerId,
                new CreateCommunicationMethodCommand(
                        "mobile", "055 123 4567", null, null, "الجوال", true,
                        "confidential", "GRANTED", "GENERAL", null, null),
                "SA");

        ArgumentCaptor<CreateCommunicationMethodCommand> captor =
                ArgumentCaptor.forClass(CreateCommunicationMethodCommand.class);
        verify(repository).createCommunicationMethod(any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().normalizedValue()).isEqualTo("+966551234567");
        assertThat(created.normalizedValue()).isEqualTo("+966551234567");
    }

    @Test
    void refusesToInventCountryCodeForLocalPhone() {
        assertThatThrownBy(() -> useCases.createCommunicationMethod(
                UUID.randomUUID(), UUID.randomUUID(), "ACCOUNT", UUID.randomUUID(),
                new CreateCommunicationMethodCommand(
                        "PHONE", "011 123 4567", null, null, null, false,
                        "INTERNAL", null, null, null, null),
                null))
                .isInstanceOf(CrmContractException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void masksConfidentialEmailWithoutSensitiveCapability() {
        CommunicationMethodRecord record = new CommunicationMethodRecord(
                UUID.randomUUID(), 2, "PERSON", UUID.randomUUID(), "EMAIL",
                "private@example.test", "private@example.test", "private@example.test",
                "Work", true, true, "VERIFIED", Instant.now(), "CONFIDENTIAL",
                "GRANTED", "GENERAL", "ACTIVE", null, null, Instant.now(), Instant.now(), null);

        CommunicationMethodRecord masked = useCases.masked(record, false);

        assertThat(masked.rawValue()).isNull();
        assertThat(masked.normalizedValue()).isNull();
        assertThat(masked.displayValue()).isEqualTo("p***@example.test");
        assertThat(useCases.masked(record, true).rawValue()).isEqualTo("private@example.test");
    }

    @Test
    void rejectsUnsupportedCountryExtensionFields() {
        assertThatThrownBy(() -> useCases.createAddress(
                UUID.randomUUID(), UUID.randomUUID(), "ACCOUNT", UUID.randomUUID(),
                new AddressCommunicationRepository.CreateAddressCommand(
                        "REGISTERED", "المقر", "طريق الملك فهد، الرياض", "طريق الملك فهد",
                        null, null, null, "الرياض", null, "12345", "SA",
                        "{\"unboundedCustomObject\":{\"nested\":true}}", null, null,
                        true, false, null, null, null)))
                .isInstanceOf(CrmContractException.class)
                .hasMessageContaining("Unsupported countryExtension field");
    }

    private static CommunicationMethodRecord method(
            UUID ownerId, CreateCommunicationMethodCommand command) {
        Instant now = Instant.now();
        return new CommunicationMethodRecord(UUID.randomUUID(), 0, "PERSON", ownerId,
                command.methodType(), command.rawValue(), command.normalizedValue(), command.displayValue(),
                command.label(), command.preferred(), false, "UNVERIFIED", null,
                command.privacyClassification(), command.consentStateReference(), command.usagePurpose(),
                "ACTIVE", command.validFrom(), command.validTo(), now, now, null);
    }
}
