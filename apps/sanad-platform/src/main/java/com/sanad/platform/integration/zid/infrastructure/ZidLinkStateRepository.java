package com.sanad.platform.integration.zid.infrastructure;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for {@code zid_link_states}. Only SHA-256 hashes of
 * OAuth state values are stored; raw states never reach the database.
 */
@Repository
public class ZidLinkStateRepository {

    public record ZidLinkStateRecord(UUID id,
                                     UUID tenantId,
                                     UUID userId,
                                     String stateHash,
                                     String status,
                                     Instant createdAt,
                                     Instant expiresAt) {
    }

    private static final RowMapper<ZidLinkStateRecord> ROW_MAPPER = (rs, rowNum) -> new ZidLinkStateRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("state_hash"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public ZidLinkStateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(ZidLinkStateRecord record) {
        jdbc.update("""
                INSERT INTO zid_link_states (id, tenant_id, user_id, state_hash, status, created_at, expires_at)
                VALUES (:id, :tenantId, :userId, :stateHash, :status, :createdAt, :expiresAt)
                """, new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("tenantId", record.tenantId())
                .addValue("userId", record.userId())
                .addValue("stateHash", record.stateHash())
                .addValue("status", record.status())
                .addValue("createdAt", Timestamp.from(record.createdAt()))
                .addValue("expiresAt", Timestamp.from(record.expiresAt())));
    }

    public Optional<ZidLinkStateRecord> findByStateHash(String stateHash) {
        List<ZidLinkStateRecord> rows = jdbc.query(
                "SELECT * FROM zid_link_states WHERE state_hash = :stateHash",
                new MapSqlParameterSource("stateHash", stateHash), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    /** Atomically claim a pending state: only the first caller transitions PENDING → COMPLETED. */
    public boolean claimPending(UUID id) {
        int updated = jdbc.update("""
                UPDATE zid_link_states
                SET status = 'COMPLETED'
                WHERE id = :id AND status = 'PENDING' AND expires_at > NOW()
                """, new MapSqlParameterSource("id", id));
        return updated == 1;
    }

    public void markExpired(UUID id) {
        jdbc.update("UPDATE zid_link_states SET status = 'EXPIRED' WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    /** Opportunistic cleanup of terminal states older than the TTL. */
    public int purgeTerminal(Instant olderThan) {
        return jdbc.update("""
                DELETE FROM zid_link_states
                WHERE status <> 'PENDING' AND created_at < :olderThan
                """, new MapSqlParameterSource("olderThan", Timestamp.from(olderThan)));
    }
}
