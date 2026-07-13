package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC adapter implementing AccountRepository port.
 * Tenant-scoped SQL with atomic version checks.
 * Branch: crm/004-modular-domain-architecture
 */
@Repository
public class JdbcAccountRepository implements AccountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AccountRecord findById(UUID tenantId, UUID accountId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT * FROM crm_accounts WHERE tenant_id = :tenantId AND id = :id",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", accountId));
            return mapRow(row);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        }
    }

    @Override
    public List<AccountRecord> findAll(UUID tenantId, int limit, String search) {
        String sql = "SELECT * FROM crm_accounts WHERE tenant_id = :tenantId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (search != null && !search.isBlank()) {
            sql += " AND (LOWER(display_name) LIKE LOWER(:search) OR LOWER(normalized_name) LIKE LOWER(:search))";
            params.addValue("search", "%" + search + "%");
        }
        sql += " ORDER BY updated_at DESC, id DESC LIMIT :limit";
        params.addValue("limit", limit);
        return jdbc.queryForList(sql, params).stream().map(this::mapRow).toList();
    }

    @Override
    public AccountRecord create(UUID tenantId, UUID actorId, CreateAccountCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String normalizedName = cmd.displayName() == null ? null : cmd.displayName().toLowerCase().trim();
        jdbc.update(
                "INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name, account_type, " +
                "lifecycle_status, primary_currency_code, preferred_locale, time_zone, source, parent_account_id, " +
                "owner_user_id, created_by, updated_by, created_at, updated_at) " +
                "VALUES (:id, :tenantId, 0, :displayName, :normalizedName, :accountType, 'ACTIVE', " +
                ":currencyCode, :locale, :timeZone, :source, :parentAccountId, :ownerUserId, :actorId, :actorId, :now, :now)",
                new MapSqlParameterSource()
                        .addValue("id", id).addValue("tenantId", tenantId)
                        .addValue("displayName", cmd.displayName())
                        .addValue("normalizedName", normalizedName)
                        .addValue("accountType", cmd.accountType() == null ? "BUSINESS" : cmd.accountType().toUpperCase())
                        .addValue("currencyCode", cmd.primaryCurrencyCode() == null ? "SAR" : cmd.primaryCurrencyCode())
                        .addValue("locale", cmd.preferredLocale() == null ? "ar-SA" : cmd.preferredLocale())
                        .addValue("timeZone", cmd.timeZone() == null ? "Asia/Riyadh" : cmd.timeZone())
                        .addValue("source", cmd.source()).addValue("parentAccountId", cmd.parentAccountId())
                        .addValue("ownerUserId", cmd.ownerUserId()).addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public AccountRecord update(UUID tenantId, UUID actorId, UUID accountId, UpdateAccountCommand cmd, long expectedVersion) {
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_accounts SET display_name = COALESCE(:displayName, display_name), " +
                "normalized_name = COALESCE(:normalizedName, normalized_name), " +
                "parent_account_id = :parentAccountId, " +
                "owner_user_id = COALESCE(:ownerUserId, owner_user_id), " +
                "primary_currency_code = COALESCE(:currency, primary_currency_code), " +
                "preferred_locale = COALESCE(:locale, preferred_locale), " +
                "time_zone = COALESCE(:timeZone, time_zone), " +
                "source = COALESCE(:source, source), " +
                "updated_by = :actorId, updated_at = :now, version = version + 1 " +
                "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("id", accountId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("displayName", cmd.displayName())
                        .addValue("normalizedName", cmd.displayName() == null ? null : cmd.displayName().toLowerCase().trim())
                        .addValue("parentAccountId", cmd.parentAccountId())
                        .addValue("ownerUserId", cmd.ownerUserId())
                        .addValue("currency", cmd.primaryCurrencyCode())
                        .addValue("locale", cmd.preferredLocale())
                        .addValue("timeZone", cmd.timeZone())
                        .addValue("source", cmd.source())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, accountId);
    }

    @Override
    public AccountRecord archive(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) {
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status = 'ARCHIVED', updated_by = :actorId, " +
                "updated_at = :now, version = version + 1 " +
                "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", accountId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, accountId);
    }

    @Override
    public AccountRecord restore(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) {
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status = 'ACTIVE', updated_by = :actorId, " +
                "updated_at = :now, version = version + 1 " +
                "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", accountId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, accountId);
    }

    private AccountRecord mapRow(Map<String, Object> row) {
        return new AccountRecord(
                (UUID) row.get("id"), asLong(row.get("version")),
                (String) row.get("display_name"), (String) row.get("normalized_name"),
                (String) row.get("account_type"), (String) row.get("lifecycle_status"),
                (String) row.get("primary_currency_code"), (String) row.get("preferred_locale"),
                (String) row.get("time_zone"), (String) row.get("source"),
                (UUID) row.get("parent_account_id"), (UUID) row.get("owner_user_id"),
                asInstant(row.get("created_at")), asInstant(row.get("updated_at")));
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }

    private static Instant asInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof Instant i) return i;
        return null;
    }
}
