package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.skills.SkillLevel;
import com.sanad.platform.crm.ownership.domain.skills.SkillRepository;
import com.sanad.platform.crm.ownership.domain.skills.StaffSkill;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Skill Management.
 *
 * <p>Manages staff skill registrations including creation, updates,
 * and queries. Enforces uniqueness constraints per staff member.
 */
public class SkillManagementUseCases {

    private final SkillRepository skills;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public SkillManagementUseCases(SkillRepository skills,
                                   AuditPort audit,
                                   TimelineEventPort timeline,
                                   ObjectMapper mapper) {
        this.skills = skills;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    /**
     * Register a new skill for a staff member.
     */
    @Transactional
    public StaffSkill registerSkill(UUID tenantId, UUID actorId, RegisterSkillCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");
        requireId(cmd.staffId(), "staffId");
        if (cmd.skillName() == null || cmd.skillName().isBlank()) {
            throw new IllegalArgumentException("skillName required");
        }
        if (cmd.level() == null) throw new IllegalArgumentException("level required");
        if (cmd.proficiency() < 1 || cmd.proficiency() > 100) {
            throw new OwnershipDomainException("proficiency must be between 1 and 100");
        }

        // Check uniqueness
        if (skills.existsByStaffAndSkill(tenantId, cmd.staffId(), cmd.skillName(), null)) {
            throw new OwnershipDomainException(
                    "Skill already registered for this staff member: " + cmd.skillName());
        }

        StaffSkill created = skills.create(new SkillRepository.CreateSkillCommand(
                tenantId, cmd.staffId(), cmd.skillName().trim(),
                cmd.level(), cmd.proficiency(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "STAFF_SKILL", created.id(),
                new AuditChange(null, serializeSkill(created)), now);
        timeline.record(tenantId, "STAFF_SKILL", created.id(),
                "crm.skill.registered", "Skill registered: " + cmd.skillName(),
                "CRM_STAFF_SKILL", created.id(), actorId, now);
        return created;
    }

    /**
     * Update an existing skill record (level and/or proficiency).
     */
    @Transactional
    public StaffSkill updateSkill(UUID tenantId, UUID actorId, UUID skillId,
                                   UpdateSkillCommand cmd) {
        requireContext(tenantId, actorId);
        requireId(skillId, "skillId");
        if (cmd == null) throw new IllegalArgumentException("command required");

        StaffSkill current = skills.findById(tenantId, skillId)
                .orElseThrow(() -> new OwnershipDomainException("Staff skill not found: " + skillId));

        SkillLevel level = cmd.level() != null ? cmd.level() : current.level();
        int proficiency = cmd.proficiency() > 0 ? cmd.proficiency() : current.proficiency();

        if (proficiency < 1 || proficiency > 100) {
            throw new OwnershipDomainException("proficiency must be between 1 and 100");
        }

        return skills.update(tenantId, skillId, new SkillRepository.UpdateSkillCommand(
                level, proficiency, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + skillId));
    }

    /**
     * Delete a skill record.
     */
    @Transactional
    public boolean deleteSkill(UUID tenantId, UUID actorId, UUID skillId) {
        requireContext(tenantId, actorId);
        requireId(skillId, "skillId");

        StaffSkill current = skills.findById(tenantId, skillId)
                .orElseThrow(() -> new OwnershipDomainException("Staff skill not found: " + skillId));

        boolean deleted = skills.delete(tenantId, skillId);

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "DELETE", "STAFF_SKILL", skillId,
                new AuditChange(serializeSkill(current), null), now);
        timeline.record(tenantId, "STAFF_SKILL", skillId,
                "crm.skill.deleted", "Skill deleted: " + current.skillName(),
                "CRM_STAFF_SKILL", skillId, actorId, now);
        return deleted;
    }

    /**
     * List all skills for a staff member.
     */
    public List<StaffSkill> listSkillsByStaff(UUID tenantId, UUID staffId) {
        requireId(tenantId, "tenantId");
        requireId(staffId, "staffId");
        return List.copyOf(skills.findByStaffId(tenantId, staffId));
    }

    /**
     * Find all staff with a specific skill.
     */
    public List<StaffSkill> listBySkillName(UUID tenantId, String skillName) {
        requireId(tenantId, "tenantId");
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName required");
        }
        return List.copyOf(skills.findBySkillName(tenantId, skillName));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private com.fasterxml.jackson.databind.JsonNode serializeSkill(StaffSkill s) {
        if (s == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", s.id().toString());
        node.put("staffId", s.staffId().toString());
        node.put("skillName", s.skillName());
        node.put("level", s.level().name());
        node.put("proficiency", s.proficiency());
        return node;
    }

    // ── Command Records ──────────────────────────────────────────────────

    public record RegisterSkillCommand(
            UUID staffId,
            String skillName,
            SkillLevel level,
            int proficiency) {}

    public record UpdateSkillCommand(
            SkillLevel level,
            int proficiency) {}
}
