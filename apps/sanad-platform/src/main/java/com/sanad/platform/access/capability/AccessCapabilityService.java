package com.sanad.platform.access.capability;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccessCapabilityService {

    private final AccessCapabilityRepository repository;

    public AccessCapabilityService(AccessCapabilityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CapabilityResponse create(String code, String name, String description) {
        String normalizedCode = requireCode(code);
        String normalizedName = requireName(name);
        if (repository.existsByCode(normalizedCode)) {
            throw new AccessConflictException("Capability code already exists: " + normalizedCode);
        }
        return CapabilityResponse.from(repository.save(
                new AccessCapability(normalizedCode, normalizedName, description)));
    }

    @Transactional(readOnly = true)
    public List<CapabilityResponse> list() {
        return repository.findAllByOrderByCodeAsc().stream()
                .map(CapabilityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CapabilityResponse get(UUID capabilityId) {
        return CapabilityResponse.from(load(capabilityId));
    }

    @Transactional
    public CapabilityResponse update(
            UUID capabilityId, String name, String description) {
        AccessCapability capability = load(capabilityId);
        capability.setName(requireName(name));
        capability.setDescription(description);
        return CapabilityResponse.from(repository.save(capability));
    }

    @Transactional
    public CapabilityResponse changeStatus(UUID capabilityId, CapabilityStatus status) {
        AccessCapability capability = load(capabilityId);
        capability.setStatus(Objects.requireNonNull(status, "status must not be null"));
        return CapabilityResponse.from(repository.save(capability));
    }

    public AccessCapability load(UUID capabilityId) {
        Objects.requireNonNull(capabilityId, "capabilityId must not be null");
        return repository.findById(capabilityId)
                .orElseThrow(() -> new AccessResourceNotFoundException("Capability not found"));
    }

    public AccessCapability loadByCode(String code) {
        return repository.findByCode(requireCode(code))
                .orElseThrow(() -> new AccessResourceNotFoundException("Capability not found"));
    }

    private static String requireCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 150
                || !normalized.matches("[A-Z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid capability code");
        }
        return normalized;
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException("Invalid capability name");
        }
        return normalized;
    }
}
