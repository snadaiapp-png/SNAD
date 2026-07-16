package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.ContactRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcContactRepository implements ContactRepository {

    private static final String CONTACT_COLUMNS = """
            id, version, account_id, given_name, family_name, display_name,
            primary_email, normalized_email, primary_phone, preferred_locale,
            time_zone, lifecycle_status, owner_user_id, consent_summary,
            created_at, updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcContactRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ContactRecord findById(UUID tenantId, UUID contactId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT " + CONTACT_COLUMNS + " FROM crm_contacts " +
                            "WHERE tenant_id = :tenantId AND id = :id",
                    params("tenantId", tenantId).addValue("id", contactId));
            return mapRow(row);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.CRM_CONTACT_NOT_FOUND);
        }
    }

    @Override
    public List<ContactRecord> findAll(UUID tenantId, int limit, UUID accountId, String search) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT " + CONTACT_COLUMNS.replace("id", "c.id") +
                        " FROM crm_contacts c WHERE c.tenant_id = :tenantId");
        MapSqlParameterSource parameters = params("tenantId", tenantId);
        if (accountId != null) {
            sql.append(" AND (c.account_id = :accountId OR EXISTS (")
                    .append("SELECT 1 FROM crm_contact_account_relationships r ")
                    .append("WHERE r.tenant_id = c.tenant_id AND r.contact_id = c.id ")
                    .append("AND r.account_id = :accountId AND r.status <> 'ARCHIVED'))");
            parameters.addValue("accountId", accountId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (")
                    .append("LOWER(c.display_name) LIKE LOWER(:search) ")
                    .append("OR LOWER(COALESCE(c.preferred_name, '')) LIKE LOWER(:search) ")
                    .append("OR LOWER(COALESCE(c.primary_email, '')) LIKE LOWER(:search) ")
                    .append("OR LOWER(COALESCE(c.owner_user_id::text, '')) LIKE LOWER(:search) ")
                    .append("OR EXISTS (SELECT 1 FROM crm_contact_account_relationships r ")
                    .append("JOIN crm_accounts a ON a.tenant_id = r.tenant_id AND a.id = r.account_id ")
                    .append("WHERE r.tenant_id = c.tenant_id AND r.contact_id = c.id ")
                    .append("AND (LOWER(a.display_name) LIKE LOWER(:search) ")
                    .append("OR LOWER(r.role_code) LIKE LOWER(:search) ")
                    .append("OR LOWER(COALESCE(r.department, '')) LIKE LOWER(:search) ")
                    .append("OR LOWER(COALESCE(r.job_title, '')) LIKE LOWER(:search))))");
            parameters.addValue("search", "%" + search.trim() + "%");
        }
        sql.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT :limit");
        parameters.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), parameters).stream().map(this::mapRow).toList();
    }

    @Override
    public ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = displayName(command.givenName(), command.familyName());
        String normalizedEmail = normalize(command.primaryEmail());
        jdbc.update(
                """
                INSERT INTO crm_contacts
                    (id, tenant_id, version, account_id, legal_name, preferred_name,
                     given_name, family_name, display_name, normalized_name,
                     primary_email, normalized_email, primary_phone, preferred_locale,
                     time_zone, lifecycle_status, owner_user_id, consent_summary,
                     created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :accountId, :displayName, :givenName,
                        :givenName, :familyName, :displayName, LOWER(:displayName),
                        :primaryEmail, :normalizedEmail, :primaryPhone, :locale,
                        :timeZone, 'ACTIVE', :ownerUserId, :consent,
                        :actorId, :actorId, :now, :now)
                """,
                params("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("accountId", command.accountId())
                        .addValue("givenName", command.givenName())
                        .addValue("familyName", command.familyName())
                        .addValue("displayName", displayName)
                        .addValue("primaryEmail", command.primaryEmail())
                        .addValue("normalizedEmail", normalizedEmail)
                        .addValue("primaryPhone", command.primaryPhone())
                        .addValue("locale", command.preferredLocale())
                        .addValue("timeZone", command.timeZone())
                        .addValue("ownerUserId", command.ownerUserId())
                        .addValue("consent", command.consentSummary())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        if (command.accountId() != null) {
            createLegacyRelationship(tenantId, actorId, id, command.accountId(), command.ownerUserId(), now);
        }
        return findById(tenantId, id);
    }

    @Override
    public ContactRecord update(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UpdateContactCommand command,
            long expectedVersion) {
        ContactRecord current = findById(tenantId, contactId);
        Instant now = Instant.now();
        String newDisplayName = command.givenName() != null || command.familyName() != null
                ? displayName(
                        command.givenName() == null ? current.givenName() : command.givenName(),
                        command.familyName() == null ? current.familyName() : command.familyName())
                : current.displayName();
        int updated = jdbc.update(
                """
                UPDATE crm_contacts
                SET account_id = COALESCE(:accountId, account_id),
                    legal_name = CASE WHEN :nameChanged = TRUE THEN :displayName ELSE legal_name END,
                    preferred_name = COALESCE(:givenName, preferred_name),
                    given_name = COALESCE(:givenName, given_name),
                    family_name = COALESCE(:familyName, family_name),
                    display_name = :displayName,
                    normalized_name = LOWER(:displayName),
                    primary_email = COALESCE(:primaryEmail, primary_email),
                    normalized_email = COALESCE(:normalizedEmail, normalized_email),
                    primary_phone = COALESCE(:primaryPhone, primary_phone),
                    preferred_locale = COALESCE(:locale, preferred_locale),
                    time_zone = COALESCE(:timeZone, time_zone),
                    owner_user_id = COALESCE(:ownerUserId, owner_user_id),
                    consent_summary = COALESCE(:consent, consent_summary),
                    updated_by = :actorId,
                    updated_at = :now,
                    version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
                """,
                params("tenantId", tenantId)
                        .addValue("id", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("accountId", command.accountId())
                        .addValue("nameChanged", command.givenName() != null || command.familyName() != null)
                        .addValue("givenName", command.givenName())
                        .addValue("familyName", command.familyName())
                        .addValue("displayName", newDisplayName)
                        .addValue("primaryEmail", command.primaryEmail())
                        .addValue("normalizedEmail", normalize(command.primaryEmail()))
                        .addValue("primaryPhone", command.primaryPhone())
                        .addValue("locale", command.preferredLocale())
                        .addValue("timeZone", command.timeZone())
                        .addValue("ownerUserId", command.ownerUserId())
                        .addValue("consent", command.consentSummary())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        if (command.accountId() != null && !Objects.equals(current.accountId(), command.accountId())) {
            switchLegacyRelationship(tenantId, actorId, contactId, command.accountId(),
                    command.ownerUserId() == null ? current.ownerUserId() : command.ownerUserId(), now);
        }
        return findById(tenantId, contactId);
    }

    @Override
    public ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
        int updated = jdbc.update(
                """
                UPDATE crm_contacts
                SET lifecycle_status = 'ARCHIVED', archived_at = :now,
                    updated_by = :actorId, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
                """,
                params("tenantId", tenantId).addValue("id", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, contactId);
    }

    @Override
    public ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
        int updated = jdbc.update(
                """
                UPDATE crm_contacts
                SET lifecycle_status = 'ACTIVE', archived_at = NULL,
                    updated_by = :actorId, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
                """,
                params("tenantId", tenantId).addValue("id", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, contactId);
    }

    private void switchLegacyRelationship(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UUID accountId,
            UUID ownerUserId,
            Instant now) {
        jdbc.update(
                """
                UPDATE crm_contact_account_relationships
                SET status = 'INACTIVE', primary_relationship = FALSE, primary_scope_contact_id = NULL,
                    updated_by = :actorId, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND contact_id = :contactId
                  AND role_key = 'LEGACY_ACCOUNT' AND status <> 'ARCHIVED'
                """,
                params("tenantId", tenantId).addValue("contactId", contactId)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        Integer existing = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM crm_contact_account_relationships
                WHERE tenant_id = :tenantId AND contact_id = :contactId
                  AND account_id = :accountId AND role_key = 'LEGACY_ACCOUNT'
                """,
                params("tenantId", tenantId).addValue("contactId", contactId)
                        .addValue("accountId", accountId), Integer.class);
        if (existing != null && existing > 0) {
            jdbc.update(
                    """
                    UPDATE crm_contact_account_relationships
                    SET status = 'ACTIVE', primary_relationship = TRUE, primary_scope_contact_id = contact_id,
                        owner_user_id = :ownerUserId, archived_at = NULL,
                        updated_by = :actorId, updated_at = :now, version = version + 1
                    WHERE tenant_id = :tenantId AND contact_id = :contactId
                      AND account_id = :accountId AND role_key = 'LEGACY_ACCOUNT'
                    """,
                    params("tenantId", tenantId).addValue("contactId", contactId)
                            .addValue("accountId", accountId).addValue("ownerUserId", ownerUserId)
                            .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        } else {
            createLegacyRelationship(tenantId, actorId, contactId, accountId, ownerUserId, now);
        }
    }

    private void createLegacyRelationship(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UUID accountId,
            UUID ownerUserId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO crm_contact_account_relationships
                    (id, tenant_id, contact_id, account_id, version, role_code, custom_role_id,
                     role_key, status, primary_relationship, primary_scope_contact_id,
                     valid_from, decision_authority, owner_user_id,
                     created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, :contactId, :accountId, 0, 'OTHER', NULL,
                        'LEGACY_ACCOUNT', 'ACTIVE', TRUE, :contactId,
                        :validFrom, 'NONE', :ownerUserId,
                        :actorId, :actorId, :now, :now)
                """,
                params("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("contactId", contactId).addValue("accountId", accountId)
                        .addValue("validFrom", java.sql.Date.valueOf(now.atZone(java.time.ZoneOffset.UTC).toLocalDate()))
                        .addValue("ownerUserId", ownerUserId).addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
    }

    private ContactRecord mapRow(Map<String, Object> row) {
        return new ContactRecord(
                uuid(row.get("id")), asLong(row.get("version")), uuid(row.get("account_id")),
                string(row.get("given_name")), string(row.get("family_name")),
                string(row.get("display_name")), string(row.get("primary_email")),
                string(row.get("normalized_email")), string(row.get("primary_phone")),
                string(row.get("preferred_locale")), string(row.get("time_zone")),
                string(row.get("lifecycle_status")), uuid(row.get("owner_user_id")),
                string(row.get("consent_summary")), asInstant(row.get("created_at")),
                asInstant(row.get("updated_at")));
    }

    private static String displayName(String givenName, String familyName) {
        String value = ((givenName == null ? "" : givenName.trim()) + " " +
                (familyName == null ? "" : familyName.trim())).trim();
        if (value.isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Contact name is required.");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT).trim();
    }

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private static Instant asInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(value.toString());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static MapSqlParameterSource params(String key, Object value) {
        return new MapSqlParameterSource().addValue(key, value);
    }
}
