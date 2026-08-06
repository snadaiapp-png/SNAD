package com.sanad.platform.crm.ownership.domain.skills;

import java.time.Instant;
import java.util.UUID;

/**
 * Staff skill entity (CRM-008).
 *
 * <p>Tracks skills and proficiency levels for staff members.
 * Each skill has a name, level, and proficiency score (1-100).
 */
public record StaffSkill(
        UUID id,
        UUID tenantId,
        UUID staffId,
        String skillName,
        SkillLevel level,
        int proficiency,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public StaffSkill {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (staffId == null) throw new IllegalArgumentException("staffId required");
        if (skillName == null || skillName.isBlank()) throw new IllegalArgumentException("skillName required");
        if (level == null) throw new IllegalArgumentException("level required");
        if (proficiency < 1 || proficiency > 100) throw new IllegalArgumentException("proficiency must be between 1 and 100");
    }

    public boolean isBeginner() { return level == SkillLevel.BEGINNER; }
    public boolean isIntermediate() { return level == SkillLevel.INTERMEDIATE; }
    public boolean isAdvanced() { return level == SkillLevel.ADVANCED; }
    public boolean isExpert() { return level == SkillLevel.EXPERT; }
}
