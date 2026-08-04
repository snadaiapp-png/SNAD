package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.web.CrmOwnershipControllerTestSupport.Fixture;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.sanad.platform.crm.ownership.web.CrmOwnershipControllerTestSupport.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link SkillController}.
 *
 * <p>Tests CRUD lifecycle, list by staff/skillName, delete, validation,
 * not-found handling, and tenant isolation for skill endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class SkillControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;

    private Fixture fixture;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        fixture = createTenantFixture(jdbc, "skill-test");
        staffId = UUID.randomUUID();
    }

    private UUID seedSkill(String skillName, String level, int proficiency) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_staff_skills (id,tenant_id,staff_id,skill_name,level,proficiency,
                    created_by,updated_by,created_at,updated_at,version)
                VALUES (:id,:tenantId,:staffId,:skillName,:level,:proficiency,
                    :actor,:actor,:now,:now,1)
                """, p()
                .addValue("id", id)
                .addValue("tenantId", fixture.tenantId())
                .addValue("staffId", staffId)
                .addValue("skillName", skillName)
                .addValue("level", level)
                .addValue("proficiency", proficiency)
                .addValue("actor", fixture.userId())
                .addValue("now", java.time.Instant.now()));
        return id;
    }

    // ── GET /api/v1/crm/skills ────────────────────────────────────────────

    @Test
    void listSkills_byStaff() throws Exception {
        seedSkill("Java", "ADVANCED", 80);

        mockMvc.perform(get("/api/v1/crm/skills")
                        .param("staffId", staffId.toString())
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].skill_name").value("Java"));
    }

    @Test
    void listSkills_bySkillName() throws Exception {
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();
        // seed under current staffId
        seedSkill("Java", "ADVANCED", 80);

        mockMvc.perform(get("/api/v1/crm/skills")
                        .param("skillName", "Java")
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listSkills_emptyWhenNoParams() throws Exception {
        mockMvc.perform(get("/api/v1/crm/skills").with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/v1/crm/skills ───────────────────────────────────────────

    @Test
    void registerSkill_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/crm/skills")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","skillName":"Spring","level":"INTERMEDIATE","proficiency":60}
                                """.formatted(staffId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skill_name").value("Spring"))
                .andExpect(jsonPath("$.level").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.proficiency").value(60));
    }

    @Test
    void registerSkill_returns400_whenSkillNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/crm/skills")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","skillName":"","level":"BEGINNER","proficiency":30}
                                """.formatted(staffId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerSkill_returns400_whenProficiencyOutOfRange() throws Exception {
        mockMvc.perform(post("/api/v1/crm/skills")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","skillName":"Java","level":"BEGINNER","proficiency":0}
                                """.formatted(staffId)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/skills/{skillId} ────────────────────────────────

    @Test
    void updateSkill_appliesChanges() throws Exception {
        UUID skillId = seedSkill("Java", "BEGINNER", 30);

        mockMvc.perform(patch("/api/v1/crm/skills/{skillId}", skillId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"level":"EXPERT","proficiency":95}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("EXPERT"))
                .andExpect(jsonPath("$.proficiency").value(95));
    }

    // ── DELETE /api/v1/crm/skills/{skillId} ──────────────────────────────

    @Test
    void deleteSkill_returns204() throws Exception {
        UUID skillId = seedSkill("Java", "BEGINNER", 30);
        mockMvc.perform(delete("/api/v1/crm/skills/{skillId}", skillId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isNoContent());
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void listSkills_isolatedByTenant() throws Exception {
        seedSkill("Java", "ADVANCED", 80);
        Fixture other = createTenantFixture(jdbc, "skill-outsider");

        mockMvc.perform(get("/api/v1/crm/skills")
                        .param("staffId", staffId.toString())
                        .with(authentication(auth(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void listSkills_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/skills"))
                .andExpect(status().isUnauthorized());
    }
}
