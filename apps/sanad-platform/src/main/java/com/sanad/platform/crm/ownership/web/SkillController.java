package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.SkillManagementUseCases;
import com.sanad.platform.crm.ownership.application.SkillManagementUseCases.RegisterSkillCommand;
import com.sanad.platform.crm.ownership.application.SkillManagementUseCases.UpdateSkillCommand;
import com.sanad.platform.crm.ownership.domain.skills.StaffSkill;
import com.sanad.platform.crm.ownership.web.TeamModels.RegisterSkillRequest;
import com.sanad.platform.crm.ownership.web.TeamModels.UpdateSkillRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Staff Skills.
 *
 * <p>Mounted under {@code /api/v1/crm/skills}.
 */
@RestController
@RequestMapping("/api/v1/crm/skills")
public class SkillController {

    private final SkillManagementUseCases skills;

    public SkillController(SkillManagementUseCases skills) {
        this.skills = skills;
    }

    @RequireCapability("CRM.SKILLS.READ")
    @GetMapping
    public List<Map<String, Object>> listSkills(
            Authentication authentication,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) String skillName) {
        UUID tenantId = tenantId(authentication);
        if (staffId != null) {
            return skills.listSkillsByStaff(tenantId, staffId)
                    .stream().map(this::toRow).toList();
        }
        if (skillName != null) {
            return skills.listBySkillName(tenantId, skillName)
                    .stream().map(this::toRow).toList();
        }
        return List.of();
    }

    @RequireCapability("CRM.SKILLS.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerSkill(
            Authentication authentication,
            @Valid @RequestBody RegisterSkillRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        StaffSkill created = skills.registerSkill(tenantId, actorId,
                new RegisterSkillCommand(
                        request.staffId(),
                        request.skillName(),
                        request.level(),
                        request.proficiency()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.SKILLS.MANAGE")
    @PatchMapping("/{skillId}")
    public Map<String, Object> updateSkill(
            Authentication authentication,
            @PathVariable UUID skillId,
            @Valid @RequestBody UpdateSkillRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        StaffSkill updated = skills.updateSkill(tenantId, actorId, skillId,
                new UpdateSkillCommand(
                        request.level(),
                        request.proficiency()));

        return toRow(updated);
    }

    @RequireCapability("CRM.SKILLS.MANAGE")
    @DeleteMapping("/{skillId}")
    public ResponseEntity<Void> deleteSkill(Authentication authentication,
                                             @PathVariable UUID skillId) {
        skills.deleteSkill(tenantId(authentication), userId(authentication), skillId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toRow(StaffSkill s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.id());
        row.put("tenant_id", s.tenantId());
        row.put("staff_id", s.staffId());
        row.put("skill_name", s.skillName());
        row.put("level", s.level().name());
        row.put("proficiency", s.proficiency());
        row.put("created_by", s.createdBy());
        row.put("updated_by", s.updatedBy());
        row.put("created_at", toIso(s.createdAt()));
        row.put("updated_at", toIso(s.updatedAt()));
        row.put("version", s.version());
        return row;
    }

    private static String toIso(Instant v) {
        return v == null ? null : v.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
