package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.party.domain.ContactRepository;
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

@Repository
public class JdbcContactRepository implements ContactRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcContactRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ContactRecord findById(UUID tenantId, UUID contactId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT * FROM crm_contacts WHERE tenant_id = :tenantId AND id = :id",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", contactId));
            return mapRow(row);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_CONTACT_NOT_FOUND);
        }
    }

    @Override
    public List<ContactRecord> findAll(UUID tenantId, int limit, UUID accountId, String search) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_contacts WHERE tenant_id = :tenantId");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (accountId != null) { sql.append(" AND account_id = :accountId"); params.addValue("accountId", accountId); }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(display_name) LIKE LOWER(:search) OR LOWER(primary_email) LIKE LOWER(:search))");
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    @Override
    public ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = (cmd.givenName() != null ? cmd.givenName() : "") + " " + (cmd.familyName() != null ? cmd.familyName() : "");
        String normalizedEmail = cmd.primaryEmail() != null ? cmd.primaryEmail().toLowerCase().trim() : null;
        jdbc.update(
                "INSERT INTO crm_contacts (id, tenant_id, version, account_id, given_name, family_name, display_name, " +
                "normalized_name, primary_email, normalized_email, primary_phone, preferred_locale, time_zone, " +
                "lifecycle_status, owner_user_id, consent_summary, created_by, updated_by, created_at, updated_at) " +
                "VALUES (:id, :tenantId, 0, :accountId, :givenName, :familyName, :displayName, " +
                "LOWER(:displayName), :primaryEmail, :normalizedEmail, :primaryPhone, :locale, :timeZone, " +
                "'ACTIVE', :ownerUserId, :consent, :actorId, :actorId, :now, :now)",
                new MapSqlParameterSource()
                        .addValue("id", id).addValue("tenantId", tenantId).addValue("accountId", cmd.accountId())
                        .addValue("givenName", cmd.givenName()).addValue("familyName", cmd.familyName())
                        .addValue("displayName", displayName.trim()).addValue("primaryEmail", cmd.primaryEmail())
                        .addValue("normalizedEmail", normalizedEmail).addValue("primaryPhone", cmd.primaryPhone())
                        .addValue("locale", cmd.preferredLocale()).addValue("timeZone", cmd.timeZone())
                        .addValue("ownerUserId", cmd.ownerUserId()).addValue("consent", cmd.consentSummary())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public ContactRecord update(UUID tenantId, UUID actorId, UUID contactId, UpdateContactCommand cmd, long expectedVersion) {
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_contacts SET account_id = COALESCE(:accountId, account_id), " +
                "given_name = COALESCE(:givenName, given_name), family_name = COALESCE(:familyName, family_name), " +
                "display_name = COALESCE(:displayName, display_name), " +
                "primary_email = COALESCE(:primaryEmail, primary_email), " +
                "normalized_email = COALESCE(:normalizedEmail, normalized_email), " +
                "primary_phone = COALESCE(:primaryPhone, primary_phone), " +
                "preferred_locale = COALESCE(:locale, preferred_locale), " +
                "time_zone = COALESCE(:timeZone, time_zone), " +
                "owner_user_id = COALESCE(:ownerUserId, owner_user_id), " +
                "consent_summary = COALESCE(:consent, consent_summary), " +
                "updated_by = :actorId, updated_at = :now, version = version + 1 " +
                "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("accountId", cmd.accountId())
                        .addValue("givenName", cmd.givenName()).addValue("familyName", cmd.familyName())
                        .addValue("displayName", cmd.givenName() != null || cmd.familyName() != null ?
                                ((cmd.givenName() != null ? cmd.givenName() : "") + " " + (cmd.familyName() != null ? cmd.familyName() : "")).trim() : null)
                        .addValue("primaryEmail", cmd.primaryEmail())
                        .addValue("normalizedEmail", cmd.primaryEmail() != null ? cmd.primaryEmail().toLowerCase().trim() : null)
                        .addValue("primaryPhone", cmd.primaryPhone())
                        .addValue("locale", cmd.preferredLocale()).addValue("timeZone", cmd.timeZone())
                        .addValue("ownerUserId", cmd.ownerUserId()).addValue("consent", cmd.consentSummary())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, contactId);
    }

    @Override
    public ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_contacts SET lifecycle_status = 'ARCHIVED', updated_by = :actorId, " +
                "updated_at = :now, version = version + 1 " +
                "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, contactId);
    }

    @Override
    public ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_contacts SET lifecycle_status = 'ACTIVE', updated_by = :actorId, " +
                "updated_at = :now, version = version + 1 " +
                "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, contactId);
    }

    private ContactRecord mapRow(Map<String, Object> row) {
        return new ContactRecord(
                (UUID) row.get("id"), asLong(row.get("version")), (UUID) row.get("account_id"),
                (String) row.get("given_name"), (String) row.get("family_name"),
                (String) row.get("display_name"), (String) row.get("primary_email"),
                (String) row.get("normalized_email"), (String) row.get("primary_phone"),
                (String) row.get("preferred_locale"), (String) row.get("time_zone"),
                (String) row.get("lifecycle_status"), (UUID) row.get("owner_user_id"),
                (String) row.get("consent_summary"),
                asInstant(row.get("created_at")), asInstant(row.get("updated_at")));
    }

    private static long asLong(Object v) {
        if (v == null) return 0L; if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }
    private static Instant asInstant(Object v) {
        if (v == null) return null; if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof Instant i) return i; return null;
    }
}
