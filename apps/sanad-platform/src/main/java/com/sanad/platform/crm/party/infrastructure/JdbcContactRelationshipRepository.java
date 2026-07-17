package com.sanad.platform.crm.party.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcContactRelationshipRepository implements ContactRelationshipRepository {

    private static final String PROFILE_COLUMNS = """
            id, version, legal_name, preferred_name, given_name, middle_name, family_name,
            display_name, primary_email, primary_phone, preferred_locale, time_zone, pronouns,
            lifecycle_status, owner_user_id, source, created_at, updated_at
            """;

    private static final String RELATIONSHIP_COLUMNS = """
            r.id, r.version, r.contact_id, r.account_id,
            c.display_name AS contact_display_name,
            a.display_name AS account_display_name,
            r.role_code, r.custom_role_id,
            cr.name_ar AS custom_role_name_ar,
            cr.name_en AS custom_role_name_en,
            r.status, r.primary_relationship, r.valid_from, r.valid_to,
            r.job_title, r.department, r.decision_authority, r.owner_user_id,
            r.created_at, r.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcContactRelationshipRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public ContactProfileRecord findProfile(UUID tenantId, UUID contactId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT " + PROFILE_COLUMNS + " FROM crm_contacts " +
                            "WHERE tenant_id = :tenantId AND id = :contactId",
                    params("tenantId", tenantId).addValue("contactId", contactId));
            return mapProfile(row);
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.CRM_CONTACT_NOT_FOUND);
        }
    }

    @Override
    public ContactProfileRecord updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UpdateContactProfileCommand command,
            long expectedVersion) {
        ContactProfileRecord current = findProfile(tenantId, contactId);
        String givenName = valueOr(command.givenName(), current.givenName());
        String middleName = valueOr(command.middleName(), current.middleName());
        String familyName = valueOr(command.familyName(), current.familyName());
        String preferredName = valueOr(command.preferredName(), current.preferredName());
        String legalName = valueOr(command.legalName(), current.legalName());
        String displayName = buildDisplayName(preferredName, givenName, middleName, familyName, legalName);
        String primaryEmail = valueOr(command.primaryEmail(), current.primaryEmail());
        UUID ownerUserId = command.ownerUserId() == null ? current.ownerUserId() : command.ownerUserId();
        Instant now = Instant.now();

        int updated = jdbc.update(
                """
                UPDATE crm_contacts
                SET legal_name = :legalName,
                    preferred_name = :preferredName,
                    given_name = :givenName,
                    middle_name = :middleName,
                    family_name = :familyName,
                    display_name = :displayName,
                    normalized_name = LOWER(:displayName),
                    primary_email = :primaryEmail,
                    normalized_email = :normalizedEmail,
                    primary_phone = COALESCE(:primaryPhone, primary_phone),
                    preferred_locale = COALESCE(:preferredLocale, preferred_locale),
                    time_zone = COALESCE(:timeZone, time_zone),
                    pronouns = COALESCE(:pronouns, pronouns),
                    owner_user_id = :ownerUserId,
                    source = COALESCE(:source, source),
                    updated_by = :actorId,
                    updated_at = :now,
                    version = version + 1
                WHERE tenant_id = :tenantId
                  AND id = :contactId
                  AND version = :expectedVersion
                """,
                params("tenantId", tenantId)
                        .addValue("contactId", contactId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("legalName", legalName)
                        .addValue("preferredName", preferredName)
                        .addValue("givenName", givenName)
                        .addValue("middleName", middleName)
                        .addValue("familyName", familyName)
                        .addValue("displayName", displayName)
                        .addValue("primaryEmail", primaryEmail)
                        .addValue("normalizedEmail", normalize(primaryEmail))
                        .addValue("primaryPhone", command.primaryPhone())
                        .addValue("preferredLocale", command.preferredLocale())
                        .addValue("timeZone", command.timeZone())
                        .addValue("pronouns", command.pronouns())
                        .addValue("ownerUserId", ownerUserId)
                        .addValue("source", command.source())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        if (!Objects.equals(current.ownerUserId(), ownerUserId)) {
            jdbc.update(
                    """
                    INSERT INTO crm_contact_ownership_history
                        (id, tenant_id, contact_id, previous_owner_user_id, new_owner_user_id,
                         changed_by, changed_at, reason)
                    VALUES (:id, :tenantId, :contactId, :previousOwner, :newOwner,
                            :actorId, :changedAt, :reason)
                    """,
                    params("id", UUID.randomUUID())
                            .addValue("tenantId", tenantId)
                            .addValue("contactId", contactId)
                            .addValue("previousOwner", current.ownerUserId())
                            .addValue("newOwner", ownerUserId)
                            .addValue("actorId", actorId)
                            .addValue("changedAt", Timestamp.from(now))
                            .addValue("reason", command.ownerChangeReason()));
        }
        return findProfile(tenantId, contactId);
    }

    @Override
    public RelationshipRecord findRelationship(UUID tenantId, UUID relationshipId) {
        try {
            return mapRelationship(jdbc.queryForMap(
                    relationshipSelect() + " WHERE r.tenant_id = :tenantId AND r.id = :relationshipId",
                    params("tenantId", tenantId).addValue("relationshipId", relationshipId)));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND, "Contact relationship was not found.");
        }
    }

    @Override
    public List<RelationshipRecord> listByContact(
            UUID tenantId,
            UUID contactId,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId) {
        assertContactExists(tenantId, contactId);
        String cursorClause = cursorClause(beforeUpdatedAt, beforeId);
        return jdbc.queryForList(
                        relationshipSelect() +
                                " WHERE r.tenant_id = :tenantId AND r.contact_id = :contactId" + cursorClause +
                                " ORDER BY r.updated_at DESC, r.id DESC LIMIT :limit",
                        cursorParams(tenantId, limit, beforeUpdatedAt, beforeId)
                                .addValue("contactId", contactId))
                .stream().map(this::mapRelationship).toList();
    }

    @Override
    public List<RelationshipRecord> listByAccount(
            UUID tenantId,
            UUID accountId,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId) {
        assertAccountExists(tenantId, accountId);
        String cursorClause = cursorClause(beforeUpdatedAt, beforeId);
        return jdbc.queryForList(
                        relationshipSelect() +
                                " WHERE r.tenant_id = :tenantId AND r.account_id = :accountId" + cursorClause +
                                " ORDER BY r.updated_at DESC, r.id DESC LIMIT :limit",
                        cursorParams(tenantId, limit, beforeUpdatedAt, beforeId)
                                .addValue("accountId", accountId))
                .stream().map(this::mapRelationship).toList();
    }

    @Override
    public RelationshipRecord createRelationship(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            CreateRelationshipCommand command) {
        assertContactExists(tenantId, contactId);
        assertAccountExists(tenantId, command.accountId());
        String roleCode = normalizedRole(command.roleCode());
        String roleKey = roleKey(tenantId, roleCode, command.customRoleId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            if (command.primaryRelationship()) {
                demoteCurrentPrimary(tenantId, contactId, actorId, id, now);
            }
            jdbc.update(
                    """
                    INSERT INTO crm_contact_account_relationships
                        (id, tenant_id, contact_id, account_id, version, role_code, custom_role_id,
                         role_key, status, primary_relationship, primary_scope_contact_id,
                         valid_from, valid_to, job_title, department, decision_authority, owner_user_id,
                         created_by, updated_by, created_at, updated_at)
                    VALUES (:id, :tenantId, :contactId, :accountId, 0, :roleCode, :customRoleId,
                            :roleKey, 'ACTIVE', :primaryRelationship, :primaryScopeContactId,
                            :validFrom, :validTo, :jobTitle, :department, :decisionAuthority, :ownerUserId,
                            :actorId, :actorId, :now, :now)
                    """,
                    params("id", id)
                            .addValue("tenantId", tenantId)
                            .addValue("contactId", contactId)
                            .addValue("accountId", command.accountId())
                            .addValue("roleCode", roleCode)
                            .addValue("customRoleId", command.customRoleId())
                            .addValue("roleKey", roleKey)
                            .addValue("primaryRelationship", command.primaryRelationship())
                            .addValue("primaryScopeContactId", command.primaryRelationship() ? contactId : null)
                            .addValue("validFrom", sqlDate(command.validFrom()))
                            .addValue("validTo", sqlDate(command.validTo()))
                            .addValue("jobTitle", command.jobTitle())
                            .addValue("department", command.department())
                            .addValue("decisionAuthority", normalizedDecisionAuthority(command.decisionAuthority()))
                            .addValue("ownerUserId", command.ownerUserId())
                            .addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
            RelationshipRecord created = findRelationship(tenantId, id);
            recordHistory(tenantId, actorId, created, "CREATED", null, created.version(), now);
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "This person and account relationship already exists or violates relationship rules.");
        }
    }

    @Override
    public RelationshipRecord updateRelationship(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            UpdateRelationshipCommand command,
            long expectedVersion) {
        RelationshipRecord current = findRelationship(tenantId, relationshipId);
        String roleCode = command.roleCode() == null ? current.roleCode() : normalizedRole(command.roleCode());
        UUID customRoleId = command.roleCode() == null && command.customRoleId() == null
                ? current.customRoleId() : command.customRoleId();
        String roleKey = roleKey(tenantId, roleCode, customRoleId);
        Instant now = Instant.now();
        try {
            int updated = jdbc.update(
                    """
                    UPDATE crm_contact_account_relationships
                    SET role_code = :roleCode,
                        custom_role_id = :customRoleId,
                        role_key = :roleKey,
                        valid_from = :validFrom,
                        valid_to = :validTo,
                        job_title = :jobTitle,
                        department = :department,
                        decision_authority = :decisionAuthority,
                        owner_user_id = :ownerUserId,
                        updated_by = :actorId,
                        updated_at = :now,
                        version = version + 1
                    WHERE tenant_id = :tenantId
                      AND id = :relationshipId
                      AND version = :expectedVersion
                    """,
                    params("tenantId", tenantId)
                            .addValue("relationshipId", relationshipId)
                            .addValue("expectedVersion", expectedVersion)
                            .addValue("roleCode", roleCode)
                            .addValue("customRoleId", customRoleId)
                            .addValue("roleKey", roleKey)
                            .addValue("validFrom", sqlDate(command.validFrom() == null ? current.validFrom() : command.validFrom()))
                            .addValue("validTo", sqlDate(command.validTo() == null ? current.validTo() : command.validTo()))
                            .addValue("jobTitle", valueOr(command.jobTitle(), current.jobTitle()))
                            .addValue("department", valueOr(command.department(), current.department()))
                            .addValue("decisionAuthority", command.decisionAuthority() == null
                                    ? current.decisionAuthority() : normalizedDecisionAuthority(command.decisionAuthority()))
                            .addValue("ownerUserId", command.ownerUserId() == null ? current.ownerUserId() : command.ownerUserId())
                            .addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
            if (updated == 0) {
                throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
            }
            RelationshipRecord result = findRelationship(tenantId, relationshipId);
            recordHistory(tenantId, actorId, result, "UPDATED", current.version(), result.version(), now);
            return result;
        } catch (DataIntegrityViolationException exception) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "The updated relationship conflicts with an existing relationship.");
        }
    }

    @Override
    public RelationshipRecord changeStatus(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            String status,
            long expectedVersion) {
        RelationshipRecord current = findRelationship(tenantId, relationshipId);
        String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        boolean active = "ACTIVE".equals(normalizedStatus);
        int updated = jdbc.update(
                """
                UPDATE crm_contact_account_relationships
                SET status = :status,
                    primary_relationship = CASE WHEN :active = TRUE THEN primary_relationship ELSE FALSE END,
                    primary_scope_contact_id = CASE WHEN :active = TRUE THEN primary_scope_contact_id ELSE NULL END,
                    archived_at = CASE WHEN :status = 'ARCHIVED' THEN :now ELSE NULL END,
                    updated_by = :actorId,
                    updated_at = :now,
                    version = version + 1
                WHERE tenant_id = :tenantId
                  AND id = :relationshipId
                  AND version = :expectedVersion
                """,
                params("tenantId", tenantId)
                        .addValue("relationshipId", relationshipId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("status", normalizedStatus)
                        .addValue("active", active)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        RelationshipRecord result = findRelationship(tenantId, relationshipId);
        String eventType = switch (normalizedStatus) {
            case "ACTIVE" -> "ARCHIVED".equals(current.status()) ? "REACTIVATED" : "ACTIVATED";
            case "INACTIVE" -> "DEACTIVATED";
            case "ARCHIVED" -> "ARCHIVED";
            default -> "UPDATED";
        };
        recordHistory(tenantId, actorId, result, eventType, current.version(), result.version(), now);
        return result;
    }

    @Override
    public RelationshipRecord setPrimary(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            long expectedVersion) {
        RelationshipRecord current = findRelationship(tenantId, relationshipId);
        if (!"ACTIVE".equals(current.status())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Only an active relationship can be primary.");
        }
        if (current.primaryRelationship()) {
            return current;
        }
        Instant now = Instant.now();
        demoteCurrentPrimary(tenantId, current.contactId(), actorId, relationshipId, now);
        int updated = jdbc.update(
                """
                UPDATE crm_contact_account_relationships
                SET primary_relationship = TRUE,
                    primary_scope_contact_id = contact_id,
                    updated_by = :actorId,
                    updated_at = :now,
                    version = version + 1
                WHERE tenant_id = :tenantId
                  AND id = :relationshipId
                  AND version = :expectedVersion
                  AND status = 'ACTIVE'
                """,
                params("tenantId", tenantId)
                        .addValue("relationshipId", relationshipId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        RelationshipRecord result = findRelationship(tenantId, relationshipId);
        recordHistory(tenantId, actorId, result, "PRIMARY_CHANGED", current.version(), result.version(), now);
        return result;
    }

    @Override
    public List<RelationshipHistoryRecord> relationshipHistory(
            UUID tenantId,
            UUID relationshipId,
            int limit) {
        findRelationship(tenantId, relationshipId);
        return jdbc.queryForList(
                        """
                        SELECT id, relationship_id, contact_id, account_id, event_type,
                               previous_version, new_version, snapshot, changed_by, changed_at
                        FROM crm_contact_relationship_history
                        WHERE tenant_id = :tenantId AND relationship_id = :relationshipId
                        ORDER BY changed_at DESC, id DESC
                        LIMIT :limit
                        """,
                        params("tenantId", tenantId)
                                .addValue("relationshipId", relationshipId)
                                .addValue("limit", limit))
                .stream().map(this::mapRelationshipHistory).toList();
    }

    @Override
    public List<OwnershipHistoryRecord> ownershipHistory(UUID tenantId, UUID contactId, int limit) {
        assertContactExists(tenantId, contactId);
        return jdbc.queryForList(
                        """
                        SELECT id, contact_id, previous_owner_user_id, new_owner_user_id,
                               changed_by, changed_at, reason
                        FROM crm_contact_ownership_history
                        WHERE tenant_id = :tenantId AND contact_id = :contactId
                        ORDER BY changed_at DESC, id DESC
                        LIMIT :limit
                        """,
                        params("tenantId", tenantId)
                                .addValue("contactId", contactId)
                                .addValue("limit", limit))
                .stream().map(this::mapOwnershipHistory).toList();
    }

    @Override
    public List<RelationshipRoleRecord> listRoles(UUID tenantId, boolean includeInactive) {
        String activeClause = includeInactive ? "" : " AND active = TRUE";
        return jdbc.queryForList(
                        """
                        SELECT id, version, code, name_ar, name_en, active, created_at, updated_at
                        FROM crm_contact_relationship_roles
                        WHERE tenant_id = :tenantId
                        """ + activeClause + " ORDER BY code ASC, id ASC",
                        params("tenantId", tenantId))
                .stream().map(this::mapRole).toList();
    }

    @Override
    public RelationshipRoleRecord createRole(
            UUID tenantId,
            UUID actorId,
            CreateRelationshipRoleCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String code = command.code() == null ? null : command.code().trim().toUpperCase(Locale.ROOT);
        try {
            jdbc.update(
                    """
                    INSERT INTO crm_contact_relationship_roles
                        (id, tenant_id, version, code, name_ar, name_en, active,
                         created_by, updated_by, created_at, updated_at)
                    VALUES (:id, :tenantId, 0, :code, :nameAr, :nameEn, TRUE,
                            :actorId, :actorId, :now, :now)
                    """,
                    params("id", id)
                            .addValue("tenantId", tenantId)
                            .addValue("code", code)
                            .addValue("nameAr", command.nameAr())
                            .addValue("nameEn", command.nameEn())
                            .addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
            return findRole(tenantId, id);
        } catch (DataIntegrityViolationException exception) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "A relationship role with the same code already exists.");
        }
    }

    private RelationshipRoleRecord findRole(UUID tenantId, UUID roleId) {
        try {
            return mapRole(jdbc.queryForMap(
                    """
                    SELECT id, version, code, name_ar, name_en, active, created_at, updated_at
                    FROM crm_contact_relationship_roles
                    WHERE tenant_id = :tenantId AND id = :roleId
                    """,
                    params("tenantId", tenantId).addValue("roleId", roleId)));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND,
                    "Relationship role was not found.");
        }
    }

    private void demoteCurrentPrimary(
            UUID tenantId,
            UUID contactId,
            UUID actorId,
            UUID exceptRelationshipId,
            Instant now) {
        List<RelationshipRecord> currentPrimaries = jdbc.queryForList(
                        relationshipSelect() +
                                " WHERE r.tenant_id = :tenantId AND r.contact_id = :contactId" +
                                " AND r.primary_relationship = TRUE AND r.id <> :exceptId",
                        params("tenantId", tenantId)
                                .addValue("contactId", contactId)
                                .addValue("exceptId", exceptRelationshipId))
                .stream().map(this::mapRelationship).toList();
        for (RelationshipRecord primary : currentPrimaries) {
            int changed = jdbc.update(
                    """
                    UPDATE crm_contact_account_relationships
                    SET primary_relationship = FALSE,
                        primary_scope_contact_id = NULL,
                        updated_by = :actorId,
                        updated_at = :now,
                        version = version + 1
                    WHERE tenant_id = :tenantId AND id = :relationshipId AND version = :version
                    """,
                    params("tenantId", tenantId)
                            .addValue("relationshipId", primary.id())
                            .addValue("version", primary.version())
                            .addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
            if (changed == 0) {
                throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
            }
            RelationshipRecord demoted = findRelationship(tenantId, primary.id());
            recordHistory(tenantId, actorId, demoted, "PRIMARY_CHANGED",
                    primary.version(), demoted.version(), now);
        }
    }

    private void recordHistory(
            UUID tenantId,
            UUID actorId,
            RelationshipRecord relationship,
            String eventType,
            Long previousVersion,
            long newVersion,
            Instant changedAt) {
        jdbc.update(
                """
                INSERT INTO crm_contact_relationship_history
                    (id, tenant_id, relationship_id, contact_id, account_id, event_type,
                     previous_version, new_version, snapshot, changed_by, changed_at)
                VALUES (:id, :tenantId, :relationshipId, :contactId, :accountId, :eventType,
                        :previousVersion, :newVersion, :snapshot, :changedBy, :changedAt)
                """,
                params("id", UUID.randomUUID())
                        .addValue("tenantId", tenantId)
                        .addValue("relationshipId", relationship.id())
                        .addValue("contactId", relationship.contactId())
                        .addValue("accountId", relationship.accountId())
                        .addValue("eventType", eventType)
                        .addValue("previousVersion", previousVersion)
                        .addValue("newVersion", newVersion)
                        .addValue("snapshot", json(relationship))
                        .addValue("changedBy", actorId)
                        .addValue("changedAt", Timestamp.from(changedAt)));
    }

    private String roleKey(UUID tenantId, String roleCode, UUID customRoleId) {
        if ("OTHER".equals(roleCode)) {
            if (customRoleId == null) {
                throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                        "customRoleId is required when roleCode is OTHER.");
            }
            RelationshipRoleRecord role = findRole(tenantId, customRoleId);
            if (!role.active()) {
                throw new CrmContractException(CrmErrorCode.CONFLICT,
                        "The selected custom relationship role is inactive.");
            }
            return "CUSTOM:" + customRoleId;
        }
        if (customRoleId != null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "customRoleId is only allowed when roleCode is OTHER.");
        }
        return roleCode;
    }

    private void assertContactExists(UUID tenantId, UUID contactId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id = :tenantId AND id = :contactId",
                params("tenantId", tenantId).addValue("contactId", contactId), Integer.class);
        if (count == null || count == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONTACT_NOT_FOUND);
        }
    }

    private void assertAccountExists(UUID tenantId, UUID accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = :tenantId AND id = :accountId",
                params("tenantId", tenantId).addValue("accountId", accountId), Integer.class);
        if (count == null || count == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        }
    }

    private static String relationshipSelect() {
        return "SELECT " + RELATIONSHIP_COLUMNS +
                " FROM crm_contact_account_relationships r" +
                " JOIN crm_contacts c ON c.tenant_id = r.tenant_id AND c.id = r.contact_id" +
                " JOIN crm_accounts a ON a.tenant_id = r.tenant_id AND a.id = r.account_id" +
                " LEFT JOIN crm_contact_relationship_roles cr" +
                " ON cr.tenant_id = r.tenant_id AND cr.id = r.custom_role_id";
    }

    private static String cursorClause(Instant beforeUpdatedAt, UUID beforeId) {
        if (beforeUpdatedAt == null || beforeId == null) return "";
        return " AND (r.updated_at < :beforeUpdatedAt OR " +
                "(r.updated_at = :beforeUpdatedAt AND r.id < :beforeId))";
    }

    private static MapSqlParameterSource cursorParams(
            UUID tenantId,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId) {
        return params("tenantId", tenantId)
                .addValue("limit", limit)
                .addValue("beforeUpdatedAt", beforeUpdatedAt == null ? null : Timestamp.from(beforeUpdatedAt))
                .addValue("beforeId", beforeId);
    }

    private ContactProfileRecord mapProfile(Map<String, Object> row) {
        return new ContactProfileRecord(
                uuid(row.get("id")), number(row.get("version")),
                string(row.get("legal_name")), string(row.get("preferred_name")),
                string(row.get("given_name")), string(row.get("middle_name")),
                string(row.get("family_name")), string(row.get("display_name")),
                string(row.get("primary_email")), string(row.get("primary_phone")),
                string(row.get("preferred_locale")), string(row.get("time_zone")),
                string(row.get("pronouns")), string(row.get("lifecycle_status")),
                uuid(row.get("owner_user_id")), string(row.get("source")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private RelationshipRecord mapRelationship(Map<String, Object> row) {
        return new RelationshipRecord(
                uuid(row.get("id")), number(row.get("version")),
                uuid(row.get("contact_id")), uuid(row.get("account_id")),
                string(row.get("contact_display_name")), string(row.get("account_display_name")),
                string(row.get("role_code")), uuid(row.get("custom_role_id")),
                string(row.get("custom_role_name_ar")), string(row.get("custom_role_name_en")),
                string(row.get("status")), Boolean.TRUE.equals(row.get("primary_relationship")),
                localDate(row.get("valid_from")), localDate(row.get("valid_to")),
                string(row.get("job_title")), string(row.get("department")),
                string(row.get("decision_authority")), uuid(row.get("owner_user_id")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private RelationshipHistoryRecord mapRelationshipHistory(Map<String, Object> row) {
        Object previous = row.get("previous_version");
        return new RelationshipHistoryRecord(
                uuid(row.get("id")), uuid(row.get("relationship_id")),
                uuid(row.get("contact_id")), uuid(row.get("account_id")),
                string(row.get("event_type")), previous == null ? null : number(previous),
                number(row.get("new_version")), string(row.get("snapshot")),
                uuid(row.get("changed_by")), instant(row.get("changed_at")));
    }

    private OwnershipHistoryRecord mapOwnershipHistory(Map<String, Object> row) {
        return new OwnershipHistoryRecord(
                uuid(row.get("id")), uuid(row.get("contact_id")),
                uuid(row.get("previous_owner_user_id")), uuid(row.get("new_owner_user_id")),
                uuid(row.get("changed_by")), instant(row.get("changed_at")),
                string(row.get("reason")));
    }

    private RelationshipRoleRecord mapRole(Map<String, Object> row) {
        return new RelationshipRoleRecord(
                uuid(row.get("id")), number(row.get("version")), string(row.get("code")),
                string(row.get("name_ar")), string(row.get("name_en")),
                Boolean.TRUE.equals(row.get("active")), instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize relationship history snapshot", exception);
        }
    }

    private static String normalizedRole(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizedDecisionAuthority(String value) {
        return value == null || value.isBlank() ? "NONE" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String buildDisplayName(
            String preferredName,
            String givenName,
            String middleName,
            String familyName,
            String legalName) {
        if (preferredName != null && !preferredName.isBlank()) return preferredName.trim();
        List<String> parts = new ArrayList<>();
        if (givenName != null && !givenName.isBlank()) parts.add(givenName.trim());
        if (middleName != null && !middleName.isBlank()) parts.add(middleName.trim());
        if (familyName != null && !familyName.isBlank()) parts.add(familyName.trim());
        if (!parts.isEmpty()) return String.join(" ", parts);
        if (legalName != null && !legalName.isBlank()) return legalName.trim();
        throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                "A person name is required.");
    }

    private static String valueOr(String candidate, String current) {
        return candidate == null ? current : candidate;
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(value.toString());
    }

    private static Instant instant(Object value) {
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

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static MapSqlParameterSource params(String key, Object value) {
        return new MapSqlParameterSource().addValue(key, value);
    }
}
