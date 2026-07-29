package com.sanad.platform.crm.ownership.domain.skills;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for StaffSkill entities.
 */
public interface SkillRepository {

    record CreateSkillCommand(
            UUID tenantId,
            UUID staffId,
            String skillName,
            SkillLevel level,
            int proficiency,
            UUID createdBy
    ) {}

    record UpdateSkillCommand(
            SkillLevel level,
            int proficiency,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<StaffSkill> findById(UUID tenantId, UUID id);

    List<StaffSkill> findByStaffId(UUID tenantId, UUID staffId);

    List<StaffSkill> findBySkillName(UUID tenantId, String skillName);

    StaffSkill create(CreateSkillCommand command);

    Optional<StaffSkill> update(UUID tenantId, UUID id, UpdateSkillCommand command);

    boolean delete(UUID tenantId, UUID id);

    boolean existsByStaffAndSkill(UUID tenantId, UUID staffId, String skillName, UUID excludeId);
}
