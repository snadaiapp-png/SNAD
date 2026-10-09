package com.sanad.platform.access.capability;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.security.authorization.AuthorizationMutationCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccessCapabilityService {
    private final AccessCapabilityRepository repository;
    private AccessMutationAuditSupport audit;
    private AuthorizationMutationCoordinator authChanges;
    public AccessCapabilityService(AccessCapabilityRepository repository) { this.repository = repository; }
    @Autowired(required = false) void setAudit(AccessMutationAuditSupport audit) { this.audit = audit; }
    @Autowired(required = false) void setAuthChanges(AuthorizationMutationCoordinator authChanges) { this.authChanges = authChanges; }

    @Transactional
    public CapabilityResponse create(String code, String name, String description) {
        String normalizedCode = requireCode(code); String normalizedName = requireName(name);
        if (repository.existsByCode(normalizedCode)) throw new AccessConflictException("Capability code already exists: " + normalizedCode);
        AccessCapability saved = repository.save(new AccessCapability(normalizedCode, normalizedName, description));
        audit("CAPABILITY_CREATE", saved.getId(), null, Map.of("code", saved.getCode(), "status", saved.getStatus().name()));
        return CapabilityResponse.from(saved);
    }
    @Transactional(readOnly = true) public List<CapabilityResponse> list() { return repository.findAllByOrderByCodeAsc().stream().map(CapabilityResponse::from).toList(); }
    @Transactional(readOnly = true) public CapabilityResponse get(UUID capabilityId) { return CapabilityResponse.from(load(capabilityId)); }

    @Transactional
    public CapabilityResponse update(UUID capabilityId, String name, String description) {
        AccessCapability capability = load(capabilityId);
        Map<String,Object> before = Map.of("name", safe(capability.getName()), "description", safe(capability.getDescription()));
        capability.setName(requireName(name)); capability.setDescription(description);
        AccessCapability saved = repository.save(capability);
        audit("CAPABILITY_UPDATE", capabilityId, before, Map.of("name", safe(saved.getName()), "description", safe(saved.getDescription())));
        return CapabilityResponse.from(saved);
    }

    @Transactional
    public CapabilityResponse changeStatus(UUID capabilityId, CapabilityStatus status) {
        AccessCapability capability = load(capabilityId); String before = capability.getStatus().name();
        capability.setStatus(Objects.requireNonNull(status, "status must not be null"));
        AccessCapability saved = repository.save(capability);
        audit("CAPABILITY_STATUS_CHANGE", capabilityId, Map.of("status", before), Map.of("status", saved.getStatus().name()));
        if (!before.equals(saved.getStatus().name()) && authChanges != null) {
            authChanges.capabilityChanged(capabilityId, "CAPABILITY_STATUS_CHANGED");
        }
        return CapabilityResponse.from(saved);
    }
    public AccessCapability load(UUID capabilityId) { Objects.requireNonNull(capabilityId); return repository.findById(capabilityId).orElseThrow(() -> new AccessResourceNotFoundException("Capability not found")); }
    public AccessCapability loadByCode(String code) { return repository.findByCode(requireCode(code)).orElseThrow(() -> new AccessResourceNotFoundException("Capability not found")); }
    private void audit(String action, UUID id, Object before, Object after) { if (audit != null) audit.success(null, action, "ACCESS_CAPABILITY", id == null ? null : id.toString(), before, after); }
    private static String safe(String v) { return v == null ? "" : v; }
    private static String requireCode(String value) { String n=value==null?"":value.trim().toUpperCase(Locale.ROOT); if(n.isEmpty()||n.length()>150||!n.matches("[A-Z0-9._:-]+")) throw new IllegalArgumentException("Invalid capability code"); return n; }
    private static String requireName(String value) { String n=value==null?"":value.trim(); if(n.isEmpty()||n.length()>200) throw new IllegalArgumentException("Invalid capability name"); return n; }
}
