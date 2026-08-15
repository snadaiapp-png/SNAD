package com.sanad.platform.management.application;

import com.sanad.platform.module.registry.ModuleCapabilityRepository;
import com.sanad.platform.module.registry.ModuleEntity;
import com.sanad.platform.module.registry.ModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive read projection of the global SNAD Module Registry.
 * This service does not create, mutate, or duplicate module registry data.
 */
@Service
public class ModuleGovernanceService {

    private final ModuleRepository moduleRepository;
    private final ModuleCapabilityRepository capabilityRepository;

    public ModuleGovernanceService(ModuleRepository moduleRepository,
                                   ModuleCapabilityRepository capabilityRepository) {
        this.moduleRepository = moduleRepository;
        this.capabilityRepository = capabilityRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModuleStatuses() {
        return moduleRepository.findAll().stream()
                .map(this::toExecutiveProjection)
                .toList();
    }

    private Map<String, Object> toExecutiveProjection(ModuleEntity module) {
        var projection = new LinkedHashMap<String, Object>();
        projection.put("id", module.getId());
        projection.put("code", module.getCode());
        projection.put("name", module.getName());
        projection.put("status", module.getStatus());
        projection.put("enabled", module.isEnabled());
        projection.put("displayOrder", module.getDisplayOrder());
        projection.put("version", module.getVersion());
        projection.put("capabilities", capabilityRepository.findByModuleId(module.getId()).stream()
                .map(capability -> capability.getCode())
                .toList());
        return projection;
    }
}
