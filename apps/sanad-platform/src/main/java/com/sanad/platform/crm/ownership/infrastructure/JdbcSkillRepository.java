package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.skills.SkillRepository;
import com.sanad.platform.crm.ownership.domain.skills.StaffSkill;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.staffSkillMapper;

@Repository
public class JdbcSkillRepository implements SkillRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSkillRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StaffSkill> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_staff_skills
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), staffSkillMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<StaffSkill> findByStaffId(UUID tenantId, UUID staffId) {
        return jdbc.query("""
                SELECT * FROM crm_staff_skills
                 WHERE tenant_id=:tenantId AND staff_id=:staffId
                 ORDER BY skill_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId), staffSkillMapper());
    }

    @Override
    public List<StaffSkill> findBySkillName(UUID tenantId, String skillName) {
        return jdbc.query("""
                SELECT * FROM crm_staff_skills
                 WHERE tenant_id=:tenantId AND skill_name=:skillName
                 ORDER BY proficiency DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("skillName", skillName), staffSkillMapper());
    }

    @Override
    @Transactional
    public StaffSkill create(CreateSkillCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_staff_skills
                  (id, tenant_id, staff_id, skill_name, level, proficiency,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :staffId, :skillName, :level, :proficiency,
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("staffId", cmd.staffId())
                .addValue("skillName", cmd.skillName())
                .addValue("level", cmd.level().name())
                .addValue("proficiency", cmd.proficiency())
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<StaffSkill> update(UUID tenantId, UUID id, UpdateSkillCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_staff_skills
                   SET level=:level, proficiency=:proficiency,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("level", cmd.level().name())
                .addValue("proficiency", cmd.proficiency())
                .addValue("updatedBy", cmd.updatedBy())
                .addValue("expectedVersion", cmd.expectedVersion()));
        if (rows != 1) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    @Override
    @Transactional
    public boolean delete(UUID tenantId, UUID id) {
        int rows = jdbc.update("""
                DELETE FROM crm_staff_skills
                 WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id));
        return rows == 1;
    }

    @Override
    public boolean existsByStaffAndSkill(UUID tenantId, UUID staffId, String skillName, UUID excludeId) {
        String sql = excludeId != null
                ? "SELECT COUNT(*) FROM crm_staff_skills WHERE tenant_id=:tenantId AND staff_id=:staffId AND skill_name=:skillName AND id<>:excludeId"
                : "SELECT COUNT(*) FROM crm_staff_skills WHERE tenant_id=:tenantId AND staff_id=:staffId AND skill_name=:skillName";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("skillName", skillName);
        if (excludeId != null) params.addValue("excludeId", excludeId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
}
