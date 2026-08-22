package com.sanad.platform.crm.collaboration.infrastructure;
import com.sanad.platform.crm.collaboration.domain.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
@Repository
public class JdbcEntityParticipantRepository implements EntityParticipantRepository {
    private static final String COLS = "id, tenant_id, entity_type, entity_id, user_id, role, added_by, added_at, removed_by, removed_at, version";
    private final NamedParameterJdbcTemplate jdbc;
    private static final RowMapper<EntityParticipant> M = (rs, r) -> new EntityParticipant(
        rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
        CollaborationEntityType.valueOf(rs.getString("entity_type")), rs.getObject("entity_id", UUID.class),
        rs.getObject("user_id", UUID.class), ParticipantRole.valueOf(rs.getString("role")),
        rs.getObject("added_by", UUID.class), rs.getTimestamp("added_at").toInstant(),
        rs.getObject("removed_by", UUID.class),
        rs.getTimestamp("removed_at") != null ? rs.getTimestamp("removed_at").toInstant() : null,
        rs.getLong("version"));
    public JdbcEntityParticipantRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public EntityParticipant insert(EntityParticipant p) {
        Objects.requireNonNull(p, "participant");
        jdbc.update("INSERT INTO crm_entity_participants (" + COLS + ") VALUES (:id,:t,:et,:ei,:u,:r,:ab,:aa,:rb,:ra,:v)",
            new MapSqlParameterSource().addValue("id", p.id()).addValue("t", p.tenantId())
                .addValue("et", p.entityType().name()).addValue("ei", p.entityId())
                .addValue("u", p.userId()).addValue("r", p.role().name())
                .addValue("ab", p.addedByUserId()).addValue("aa", Timestamp.from(p.addedAt()))
                .addValue("rb", p.removedByUserId()).addValue("ra", p.removedAt() != null ? Timestamp.from(p.removedAt()) : null)
                .addValue("v", p.version()));
        return p;
    }
    @Override public Optional<EntityParticipant> findActive(UUID t, CollaborationEntityType et, UUID ei, UUID u, ParticipantRole r) {
        Objects.requireNonNull(t, "tenantId"); Objects.requireNonNull(et, "entityType"); Objects.requireNonNull(ei, "entityId"); Objects.requireNonNull(u, "userId"); Objects.requireNonNull(r, "role");
        return jdbc.query("SELECT " + COLS + " FROM crm_entity_participants WHERE tenant_id=:t AND entity_type=:et AND entity_id=:ei AND user_id=:u AND role=:r AND removed_at IS NULL",
            new MapSqlParameterSource().addValue("t", t).addValue("et", et.name()).addValue("ei", ei).addValue("u", u).addValue("r", r.name()), M).stream().findFirst();
    }
    @Override public Optional<EntityParticipant> findById(UUID t, UUID pid) {
        Objects.requireNonNull(t, "tenantId"); Objects.requireNonNull(pid, "participantId");
        return jdbc.query("SELECT " + COLS + " FROM crm_entity_participants WHERE tenant_id=:t AND id=:pid",
            new MapSqlParameterSource().addValue("t", t).addValue("pid", pid), M).stream().findFirst();
    }
    @Override public List<EntityParticipant> listActive(UUID t, CollaborationEntityType et, UUID ei) {
        Objects.requireNonNull(t, "tenantId"); Objects.requireNonNull(et, "entityType"); Objects.requireNonNull(ei, "entityId");
        return jdbc.query("SELECT " + COLS + " FROM crm_entity_participants WHERE tenant_id=:t AND entity_type=:et AND entity_id=:ei AND removed_at IS NULL ORDER BY added_at ASC, id ASC",
            new MapSqlParameterSource().addValue("t", t).addValue("et", et.name()).addValue("ei", ei), M);
    }
    @Override public boolean markRemoved(UUID t, UUID pid, long ev, UUID rb, Instant ra) {
        Objects.requireNonNull(t, "tenantId"); Objects.requireNonNull(pid, "participantId"); Objects.requireNonNull(rb, "removedByUserId"); Objects.requireNonNull(ra, "removedAt");
        if (ev < 0) throw new IllegalArgumentException("expectedVersion must be non-negative");
        return jdbc.update("UPDATE crm_entity_participants SET removed_by=:rb, removed_at=:ra, version=version+1 WHERE tenant_id=:t AND id=:pid AND version=:ev AND removed_at IS NULL",
            new MapSqlParameterSource().addValue("t", t).addValue("pid", pid).addValue("ev", ev).addValue("rb", rb).addValue("ra", Timestamp.from(ra))) == 1;
    }
}
