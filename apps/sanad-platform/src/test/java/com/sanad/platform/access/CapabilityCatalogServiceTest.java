package com.sanad.platform.access;

import com.sanad.platform.access.capability.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CapabilityCatalogServiceTest {

    @Mock private AccessCapabilityRepository repository;
    private AccessCapabilityService service;
    private AccessCapability capability;
    private UUID capabilityId;

    @BeforeEach
    void setUp() {
        service = new AccessCapabilityService(repository);
        capability = new AccessCapability("USER.READ", "Read users", "desc");
        capabilityId = UUID.randomUUID();
    }

    @Test
    void createNormalizesCatalogCode() {
        when(repository.existsByCode("USER.READ")).thenReturn(false);
        when(repository.save(any(AccessCapability.class))).thenAnswer(call -> call.getArgument(0));

        CapabilityResponse response = service.create(
                " user.read ", " Read users ", " desc ");
        assertThat(response.code()).isEqualTo("USER.READ");
        assertThat(response.name()).isEqualTo("Read users");
        assertThat(response.status()).isEqualTo(CapabilityStatus.ACTIVE);
    }

    @Test
    void duplicateCatalogCodeIsRejected() {
        when(repository.existsByCode("USER.READ")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                "USER.READ", "Read users", null))
                .isInstanceOf(AccessConflictException.class);
    }

    @Test
    void invalidCatalogCodeIsRejected() {
        assertThatThrownBy(() -> service.create(
                "invalid code", "Read users", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void listIsSortedByRepositoryContract() {
        when(repository.findAllByOrderByCodeAsc()).thenReturn(List.of(capability));
        assertThat(service.list()).extracting(CapabilityResponse::code)
                .containsExactly("USER.READ");
    }

    @Test
    void unknownCatalogEntryIsRejected() {
        when(repository.findById(capabilityId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(capabilityId))
                .isInstanceOf(AccessResourceNotFoundException.class);
    }

    @Test
    void updateAndStatusLifecyclePersist() {
        when(repository.findById(capabilityId)).thenReturn(Optional.of(capability));
        when(repository.save(capability)).thenReturn(capability);

        assertThat(service.update(capabilityId, "View users", "updated").name())
                .isEqualTo("View users");
        assertThat(service.changeStatus(capabilityId, CapabilityStatus.INACTIVE).status())
                .isEqualTo(CapabilityStatus.INACTIVE);
    }
}
