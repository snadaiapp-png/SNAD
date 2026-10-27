package com.sanad.platform.integration.zid.infrastructure;

import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
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
 * JDBC repository for {@code zid_store_connections}.
 *
 * <p>All queries are executed inside transactions whose
 * {@code app.tenant_id} GUC is established either by the DataSource
 * RLS proxy (request paths) or by
 * {@code TenantRlsTransactionContext.applyForCurrentTransaction}
 * (worker paths). The repository itself relies on FORCE RLS for
 * tenant isolation and never filters by a client-supplied tenant id
 * — the GUC is the only isolation mechanism, mirroring the CRM
 * repositories.</p>
 */
@Repository
public class ZidConnectionRepository {

    private static final RowMapper<ZidConnectionRecord> ROW_MAPPER = (rs, rowNum) -> new ZidConnectionRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("zid_store_id"),
            rs.getString("zid_store_name"),
            rs.getString("status"),
            rs.getString("authorization_token_enc"),
            rs.getString("manager_token_enc"),
            rs.getString("refresh_token_enc"),
            rs.getString("token_key_version"),
            toInstant(rs.getTimestamp("authorization_expires_at")),
            toInstant(rs.getTimestamp("refresh_expires_at")),
            toInstant(rs.getTimestamp("last_refresh_at")),
            toInstant(rs.getTimestamp("last_sync_at")),
            rs.getString("last_sync_error"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public ZidConnectionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static MapSqlParameterSource params(ZidConnectionRecord record) {
        return new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("tenantId", record.tenantId())
                .addValue("zidStoreId", record.zidStoreId())
                .addValue("zidStoreName", record.zidStoreName())
                .addValue("status", record.status())
                .addValue("authorizationTokenEnc", record.authorizationTokenEnc())
                .addValue("managerTokenEnc", record.managerTokenEnc())
                .addValue("refreshTokenEnc", record.refreshTokenEnc())
                .addValue("tokenKeyVersion", record.tokenKeyVersion())
                .addValue("authorizationExpiresAt", toTimestamp(record.authorizationExpiresAt()))
                .addValue("refreshExpiresAt", toTimestamp(record.refreshExpiresAt()))
                .addValue("lastRefreshAt", toTimestamp(record.lastRefreshAt()))
                .addValue("lastSyncAt", toTimestamp(record.lastSyncAt()))
                .addValue("lastSyncError", record.lastSyncError());
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public Optional<ZidConnectionRecord> findById(UUID id) {
        List<ZidConnectionRecord> rows = jdbc.query(
                "SELECT * FROM zid_store_connections WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public Optional<ZidConnectionRecord> findByStoreId(String zidStoreId) {
        List<ZidConnectionRecord> rows = jdbc.query(
                "SELECT * FROM zid_store_connections WHERE zid_store_id = :storeId",
                new MapSqlParameterSource("storeId", zidStoreId), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public List<ZidConnectionRecord> findAllConnected() {
        return jdbc.query(
                "SELECT * FROM zid_store_connections WHERE status = 'CONNECTED'",
                ROW_MAPPER);
    }

    /** All connections visible to the current RLS scope (tenant-scoped in practice). */
    public List<ZidConnectionRecord> findAll() {
        return jdbc.query("SELECT * FROM zid_store_connections ORDER BY created_at", ROW_MAPPER);
    }

    public ZidConnectionRecord insert(ZidConnectionRecord record) {
        jdbc.update("""
                INSERT INTO zid_store_connections
                    (id, tenant_id, zid_store_id, zid_store_name, status,
                     authorization_token_enc, manager_token_enc, refresh_token_enc, token_key_version,
                     authorization_expires_at, refresh_expires_at, last_refresh_at,
                     authorized_by, created_at, updated_at)
                VALUES
                    (:id, :tenantId, :zidStoreId, :zidStoreName, :status,
                     :authorizationTokenEnc, :managerTokenEnc, :refreshTokenEnc, :tokenKeyVersion,
                     :authorizationExpiresAt, :refreshExpiresAt, :lastRefreshAt,
                     :tenantId, NOW(), NOW())
                """, params(record));
        return record;
    }

    /**
     * Atomically replace the full token set (documented Zid refresh
     * contract: a refresh revokes the previous refresh token, so the
     * stored set must be swapped as one unit).
     */
    public void updateTokenSet(UUID id, ZidConnectionRecord tokens) {
        jdbc.update("""
                UPDATE zid_store_connections
                SET authorization_token_enc = :authorizationTokenEnc,
                    manager_token_enc = :managerTokenEnc,
                    refresh_token_enc = :refreshTokenEnc,
                    token_key_version = :tokenKeyVersion,
                    authorization_expires_at = :authorizationExpiresAt,
                    refresh_expires_at = :refreshExpiresAt,
                    last_refresh_at = NOW(),
                    status = 'CONNECTED',
                    updated_at = NOW()
                WHERE id = :id
                """, params(tokens).addValue("id", id));
    }

    public void updateStatus(UUID id, String status, String lastSyncError) {
        jdbc.update("""
                UPDATE zid_store_connections
                SET status = :status, last_sync_error = :lastSyncError, updated_at = NOW()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("lastSyncError", lastSyncError));
    }

    public void markSynced(UUID id, Instant syncedAt) {
        jdbc.update("""
                UPDATE zid_store_connections
                SET last_sync_at = :syncedAt, updated_at = NOW()
                WHERE id = :id
                """, new MapSqlParameterSource("syncedAt", toTimestamp(syncedAt)).addValue("id", id));
    }

    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM zid_store_connections WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }
}
