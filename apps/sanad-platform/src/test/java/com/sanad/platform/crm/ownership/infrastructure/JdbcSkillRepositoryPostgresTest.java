package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.skills.SkillLevel;
import com.sanad.platform.crm.ownership.domain.skills.SkillRepository.CreateSkillCommand;
import com.sanad.platform.crm.ownership.domain.skills.SkillRepository.UpdateSkillCommand;
import com.sanad.platform.crm.ownership.domain.skills.StaffSkill;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcSkillRepository}.
 *
 * <p>Covers CRUD lifecycle, findByStaffId, findBySkillName, existsByStaffAndSkill,
 * delete, optimistic concurrency, and tenant isolation.
 */
class JdbcSkillRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcSkillRepository repo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcSkillRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        UUID staffId = UUID.randomUUID();
        StaffSkill saved = inTransaction(() -> repo.create(
                new CreateSkillCommand(tenantId, staffId, "Java",
                        SkillLevel.ADVANCED, 80, actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.staffId()).isEqualTo(staffId);
        assertThat(saved.skillName()).isEqualTo("Java");
        assertThat(saved.level()).isEqualTo(SkillLevel.ADVANCED);
        assertThat(saved.proficiency()).isEqualTo(80);
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    void createfindById_roundTrip() {
        UUID staffId = UUID.randomUUID();
        StaffSkill saved = inTransaction(() -> repo.create(
                new CreateSkillCommand(tenantId, staffId, "Spring",
                        SkillLevel.INTERMEDIATE, 60, actorId)));

        Optional<StaffSkill> found = repo.findById(tenantId, saved.id());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(repo.findById(tenantId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByStaffId_returnsSkillsForStaff() {
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffA, "Java", SkillLevel.ADVANCED, 80, actorId)));
        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffA, "Spring", SkillLevel.INTERMEDIATE, 60, actorId)));
        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffB, "Java", SkillLevel.BEGINNER, 30, actorId)));

        List<StaffSkill> skillsA = repo.findByStaffId(tenantId, staffA);
        assertThat(skillsA).hasSize(2);

        List<StaffSkill> skillsB = repo.findByStaffId(tenantId, staffB);
        assertThat(skillsB).hasSize(1);
    }

    @Test
    void findBySkillName_returnsOrderedByProficiencyDesc() {
        UUID staffA = UUID.randomUUID();
        UUID staffB = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffA, "Java", SkillLevel.BEGINNER, 30, actorId)));
        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffB, "Java", SkillLevel.EXPERT, 95, actorId)));
        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffA, "Python", SkillLevel.ADVANCED, 70, actorId)));

        List<StaffSkill> javaSkills = repo.findBySkillName(tenantId, "Java");
        assertThat(javaSkills).hasSize(2);
        assertThat(javaSkills.get(0).proficiency()).isEqualTo(95);
        assertThat(javaSkills.get(1).proficiency()).isEqualTo(30);
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        UUID staffId = UUID.randomUUID();
        StaffSkill created = inTransaction(() -> repo.create(
                new CreateSkillCommand(tenantId, staffId, "Java",
                        SkillLevel.BEGINNER, 30, actorId)));

        Optional<StaffSkill> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateSkillCommand(
                        SkillLevel.ADVANCED, 80, actorId, 0)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().level()).isEqualTo(SkillLevel.ADVANCED);
        assertThat(updated.get().proficiency()).isEqualTo(80);
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        UUID staffId = UUID.randomUUID();
        StaffSkill created = inTransaction(() -> repo.create(
                new CreateSkillCommand(tenantId, staffId, "Java",
                        SkillLevel.BEGINNER, 30, actorId)));

        // first update (v0 -> v1)
        inTransaction(() -> repo.update(tenantId, created.id(),
                new UpdateSkillCommand(SkillLevel.INTERMEDIATE, 50, actorId, 0)));

        // stale version
        Optional<StaffSkill> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(),
                new UpdateSkillCommand(SkillLevel.EXPERT, 90, actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Test
    void delete_removesRecord() {
        UUID staffId = UUID.randomUUID();
        StaffSkill created = inTransaction(() -> repo.create(
                new CreateSkillCommand(tenantId, staffId, "Java",
                        SkillLevel.BEGINNER, 30, actorId)));

        assertThat(inTransaction(() -> repo.delete(tenantId, created.id()))).isTrue();
        assertThat(repo.findById(tenantId, created.id())).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenAbsent() {
        assertThat(inTransaction(() -> repo.delete(tenantId, UUID.randomUUID()))).isFalse();
    }

    // ── EXISTS BY STAFF AND SKILL ──────────────────────────────────────────

    @Test
    void existsByStaffAndSkill_trueWhenPresent() {
        UUID staffId = UUID.randomUUID();
        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffId, "Java", SkillLevel.BEGINNER, 30, actorId)));
        assertThat(repo.existsByStaffAndSkill(tenantId, staffId, "Java", null)).isTrue();
    }

    @Test
    void existsByStaffAndSkill_falseWhenAbsent() {
        assertThat(repo.existsByStaffAndSkill(tenantId, UUID.randomUUID(), "Java", null)).isFalse();
    }

    @Test
    void existsByStaffAndSkill_excludesSpecifiedId() {
        UUID staffId = UUID.randomUUID();
        StaffSkill created = inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffId, "Java", SkillLevel.BEGINNER, 30, actorId)));
        // excludes itself -> false
        assertThat(repo.existsByStaffAndSkill(tenantId, staffId, "Java", created.id())).isFalse();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID staffId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        StaffSkill saved = inTransaction(() -> repo.create(
                new CreateSkillCommand(tenantId, staffId, "Java",
                        SkillLevel.BEGINNER, 30, actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }

    @Test
    void existsByStaffAndSkill_isolatedByTenant() {
        UUID staffId = UUID.randomUUID();
        inTransaction(() -> repo.create(new CreateSkillCommand(
                tenantId, staffId, "Java", SkillLevel.BEGINNER, 30, actorId)));
        UUID otherTenant = newTenant();
        assertThat(repo.existsByStaffAndSkill(otherTenant, staffId, "Java", null)).isFalse();
    }
}
