package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.AccountMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** JDBC adapter for the tenant-scoped enterprise Account Master. */
@Repository
public class JdbcAccountMasterRepository implements AccountMasterRepository {
    private static final String PROFILE_COLUMNS = "account_id,version,legal_name,trade_name," +
            "registration_number,tax_registration_number,industry,organization_size,website_url," +
            "customer_tier,risk_level,risk_flags,classification_id,segment_id,merge_candidate," +
            "created_at,updated_at";
    private static final String RELATIONSHIP_COLUMNS = "id,version,source_account_id,target_account_id," +
            "relationship_type,status,effective_from,effective_to,description,created_at,updated_at";
    private static final String IDENTIFIER_COLUMNS = "id,account_id,provider,system_scope,external_id," +
            "label,active,created_at,updated_at";
    private static final String TAXONOMY_COLUMNS = "id,version,taxonomy_type,code,name_ar,name_en," +
            "parent_id,active,created_at,updated_at";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountMasterRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void initializeProfile(
            UUID tenantId, UUID actorId, UUID accountId, String legalName, String tradeName) {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_account_profiles " +
                        "(account_id,tenant_id,version,legal_name,trade_name,risk_level,merge_candidate," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "SELECT :accountId,:tenantId,0,:legalName,:tradeName,'UNKNOWN',FALSE," +
                        ":actorId,:actorId,:now,:now WHERE NOT EXISTS (" +
                        "SELECT 1 FROM crm_account_profiles WHERE tenant_id=:tenantId AND account_id=:accountId)",
                params(tenantId).addValue("accountId", accountId).addValue("actorId", actorId)
                        .addValue("legalName", legalName).addValue("tradeName", tradeName)
                        .addValue("now", Timestamp.from(now)));
    }

    @Override
    public AccountProfileRecord findProfile(UUID tenantId, UUID accountId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT " + PROFILE_COLUMNS + " FROM crm_account_profiles " +
                            "WHERE tenant_id=:tenantId AND account_id=:accountId",
                    params(tenantId).addValue("accountId", accountId));
            return mapProfile(row);
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        }
    }

    @Override
    public AccountProfileRecord updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UpdateAccountProfileCommand command,
            long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_account_profiles SET " +
                        "legal_name=COALESCE(:legalName,legal_name)," +
                        "trade_name=COALESCE(:tradeName,trade_name)," +
                        "registration_number=COALESCE(:registrationNumber,registration_number)," +
                        "tax_registration_number=COALESCE(:taxRegistrationNumber,tax_registration_number)," +
                        "industry=COALESCE(:industry,industry)," +
                        "organization_size=COALESCE(:organizationSize,organization_size)," +
                        "website_url=COALESCE(:websiteUrl,website_url)," +
                        "customer_tier=COALESCE(:customerTier,customer_tier)," +
                        "risk_level=COALESCE(:riskLevel,risk_level)," +
                        "risk_flags=COALESCE(:riskFlags,risk_flags)," +
                        "classification_id=COALESCE(:classificationId,classification_id)," +
                        "segment_id=COALESCE(:segmentId,segment_id)," +
                        "merge_candidate=COALESCE(:mergeCandidate,merge_candidate)," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND account_id=:accountId AND version=:expectedVersion",
                params(tenantId)
                        .addValue("accountId", accountId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("legalName", command.legalName())
                        .addValue("tradeName", command.tradeName())
                        .addValue("registrationNumber", command.registrationNumber())
                        .addValue("taxRegistrationNumber", command.taxRegistrationNumber())
                        .addValue("industry", command.industry())
                        .addValue("organizationSize", command.organizationSize())
                        .addValue("websiteUrl", command.websiteUrl())
                        .addValue("customerTier", command.customerTier())
                        .addValue("riskLevel", command.riskLevel())
                        .addValue("riskFlags", encodeFlags(command.riskFlags()))
                        .addValue("classificationId", command.classificationId())
                        .addValue("segmentId", command.segmentId())
                        .addValue("mergeCandidate", command.mergeCandidate())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated != 1) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findProfile(tenantId, accountId);
    }

    @Override
    public List<AccountRelationshipRecord> findRelationships(UUID tenantId, UUID accountId) {
        return jdbc.queryForList(
                        "SELECT " + RELATIONSHIP_COLUMNS + " FROM crm_account_relationships " +
                                "WHERE tenant_id=:tenantId AND (source_account_id=:accountId OR target_account_id=:accountId) " +
                                "ORDER BY status,effective_from DESC,created_at DESC,id",
                        params(tenantId).addValue("accountId", accountId))
                .stream().map(this::mapRelationship).toList();
    }

    @Override
    public AccountRelationshipRecord createRelationship(
            UUID tenantId, UUID actorId, CreateAccountRelationshipCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO crm_account_relationships " +
                            "(id,tenant_id,version,source_account_id,target_account_id,relationship_type,status," +
                            "effective_from,effective_to,description,created_by,updated_by,created_at,updated_at) " +
                            "VALUES (:id,:tenantId,0,:sourceId,:targetId,:type,'ACTIVE',:effectiveFrom," +
                            ":effectiveTo,:description,:actorId,:actorId,:now,:now)",
                    params(tenantId).addValue("id", id)
                            .addValue("sourceId", command.sourceAccountId())
                            .addValue("targetId", command.targetAccountId())
                            .addValue("type", command.relationshipType())
                            .addValue("effectiveFrom", date(command.effectiveFrom()))
                            .addValue("effectiveTo", date(command.effectiveTo()))
                            .addValue("description", command.description())
                            .addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
        } catch (DataIntegrityViolationException exception) {
            throw new CrmContractException(
                    CrmErrorCode.CONFLICT, "The account relationship already exists or is invalid", null, exception);
        }
        return findRelationship(tenantId, id);
    }

    @Override
    public AccountRelationshipRecord endRelationship(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            long expectedVersion,
            LocalDate effectiveTo) {
        int updated = jdbc.update(
                "UPDATE crm_account_relationships SET status='ENDED',effective_to=:effectiveTo," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE' AND version=:expectedVersion",
                params(tenantId).addValue("id", relationshipId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("effectiveTo", date(effectiveTo))
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated != 1) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findRelationship(tenantId, relationshipId);
    }

    @Override
    public boolean hasActiveHierarchyPath(UUID tenantId, UUID fromAccountId, UUID toAccountId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT source_account_id,target_account_id FROM crm_account_relationships " +
                        "WHERE tenant_id=:tenantId AND status='ACTIVE' " +
                        "AND relationship_type IN ('PARENT','SUBSIDIARY','BRANCH')",
                params(tenantId));
        Map<UUID, List<UUID>> graph = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID source = uuid(row.get("source_account_id"));
            UUID target = uuid(row.get("target_account_id"));
            graph.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
        }
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.add(fromAccountId);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(toAccountId)) return true;
            pending.addAll(graph.getOrDefault(current, List.of()));
        }
        return false;
    }

    @Override
    public List<ExternalIdentifierRecord> findExternalIdentifiers(UUID tenantId, UUID accountId) {
        return jdbc.queryForList(
                        "SELECT " + IDENTIFIER_COLUMNS + " FROM crm_account_external_identifiers " +
                                "WHERE tenant_id=:tenantId AND account_id=:accountId AND active=TRUE " +
                                "ORDER BY provider,system_scope,external_id,id",
                        params(tenantId).addValue("accountId", accountId))
                .stream().map(this::mapIdentifier).toList();
    }

    @Override
    public ExternalIdentifierRecord createExternalIdentifier(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            CreateExternalIdentifierCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO crm_account_external_identifiers " +
                            "(id,tenant_id,account_id,provider,system_scope,external_id,label,active," +
                            "created_by,updated_by,created_at,updated_at) " +
                            "VALUES (:id,:tenantId,:accountId,:provider,:systemScope,:externalId,:label,TRUE," +
                            ":actorId,:actorId,:now,:now)",
                    params(tenantId).addValue("id", id).addValue("accountId", accountId)
                            .addValue("provider", command.provider())
                            .addValue("systemScope", command.systemScope())
                            .addValue("externalId", command.externalId())
                            .addValue("label", command.label())
                            .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        } catch (DataIntegrityViolationException exception) {
            throw new CrmContractException(
                    CrmErrorCode.CONFLICT, "The external identifier already belongs to an account", null, exception);
        }
        return findIdentifier(tenantId, accountId, id);
    }

    @Override
    public void deactivateExternalIdentifier(
            UUID tenantId, UUID actorId, UUID accountId, UUID identifierId) {
        int updated = jdbc.update(
                "UPDATE crm_account_external_identifiers SET active=FALSE,updated_by=:actorId,updated_at=:now " +
                        "WHERE tenant_id=:tenantId AND account_id=:accountId AND id=:id AND active=TRUE",
                params(tenantId).addValue("accountId", accountId).addValue("id", identifierId)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated != 1) throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND);
    }

    @Override
    public List<TaxonomyRecord> findTaxonomies(UUID tenantId, String taxonomyType) {
        return jdbc.queryForList(
                        "SELECT " + TAXONOMY_COLUMNS + " FROM crm_account_taxonomies " +
                                "WHERE tenant_id=:tenantId AND taxonomy_type=:type AND active=TRUE " +
                                "ORDER BY code,id",
                        params(tenantId).addValue("type", taxonomyType))
                .stream().map(this::mapTaxonomy).toList();
    }

    @Override
    public TaxonomyRecord createTaxonomy(UUID tenantId, UUID actorId, CreateTaxonomyCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO crm_account_taxonomies " +
                            "(id,tenant_id,version,taxonomy_type,code,name_ar,name_en,parent_id,active," +
                            "created_by,updated_by,created_at,updated_at) " +
                            "VALUES (:id,:tenantId,0,:type,:code,:nameAr,:nameEn,:parentId,TRUE," +
                            ":actorId,:actorId,:now,:now)",
                    params(tenantId).addValue("id", id).addValue("type", command.taxonomyType())
                            .addValue("code", command.code()).addValue("nameAr", command.nameAr())
                            .addValue("nameEn", command.nameEn()).addValue("parentId", command.parentId())
                            .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        } catch (DataIntegrityViolationException exception) {
            throw new CrmContractException(
                    CrmErrorCode.CONFLICT, "The taxonomy code already exists or is invalid", null, exception);
        }
        return findTaxonomy(tenantId, id);
    }

    @Override
    public boolean taxonomyExists(UUID tenantId, UUID taxonomyId, String taxonomyType) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_account_taxonomies " +
                        "WHERE tenant_id=:tenantId AND id=:id AND taxonomy_type=:type AND active=TRUE",
                params(tenantId).addValue("id", taxonomyId).addValue("type", taxonomyType), Long.class);
        return count != null && count == 1;
    }

    @Override
    public void recordStatusChange(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            String fromStatus,
            String toStatus,
            String reason,
            Instant changedAt) {
        jdbc.update(
                "INSERT INTO crm_account_status_history " +
                        "(id,tenant_id,account_id,from_status,to_status,reason,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:accountId,:fromStatus,:toStatus,:reason,:actorId,:changedAt)",
                params(tenantId).addValue("id", UUID.randomUUID()).addValue("accountId", accountId)
                        .addValue("fromStatus", fromStatus).addValue("toStatus", toStatus)
                        .addValue("reason", reason).addValue("actorId", actorId)
                        .addValue("changedAt", Timestamp.from(changedAt)));
    }

    @Override
    public void recordOwnershipChange(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UUID fromOwnerUserId,
            UUID toOwnerUserId,
            String reason,
            Instant changedAt) {
        jdbc.update(
                "INSERT INTO crm_account_ownership_history " +
                        "(id,tenant_id,account_id,from_owner_user_id,to_owner_user_id,reason,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:accountId,:fromOwner,:toOwner,:reason,:actorId,:changedAt)",
                params(tenantId).addValue("id", UUID.randomUUID()).addValue("accountId", accountId)
                        .addValue("fromOwner", fromOwnerUserId).addValue("toOwner", toOwnerUserId)
                        .addValue("reason", reason).addValue("actorId", actorId)
                        .addValue("changedAt", Timestamp.from(changedAt)));
    }

    @Override
    public List<StatusHistoryRecord> findStatusHistory(UUID tenantId, UUID accountId) {
        return jdbc.queryForList(
                        "SELECT id,account_id,from_status,to_status,reason,changed_by,changed_at " +
                                "FROM crm_account_status_history WHERE tenant_id=:tenantId AND account_id=:accountId " +
                                "ORDER BY changed_at DESC,id",
                        params(tenantId).addValue("accountId", accountId))
                .stream().map(row -> new StatusHistoryRecord(
                        uuid(row.get("id")), uuid(row.get("account_id")), string(row.get("from_status")),
                        string(row.get("to_status")), string(row.get("reason")), uuid(row.get("changed_by")),
                        instant(row.get("changed_at")))).toList();
    }

    @Override
    public List<OwnershipHistoryRecord> findOwnershipHistory(UUID tenantId, UUID accountId) {
        return jdbc.queryForList(
                        "SELECT id,account_id,from_owner_user_id,to_owner_user_id,reason,changed_by,changed_at " +
                                "FROM crm_account_ownership_history WHERE tenant_id=:tenantId AND account_id=:accountId " +
                                "ORDER BY changed_at DESC,id",
                        params(tenantId).addValue("accountId", accountId))
                .stream().map(row -> new OwnershipHistoryRecord(
                        uuid(row.get("id")), uuid(row.get("account_id")), uuid(row.get("from_owner_user_id")),
                        uuid(row.get("to_owner_user_id")), string(row.get("reason")), uuid(row.get("changed_by")),
                        instant(row.get("changed_at")))).toList();
    }

    @Override
    public List<ProjectionSnapshotRecord> findProjectionSnapshots(UUID tenantId, UUID accountId) {
        return jdbc.queryForList(
                        "SELECT id,account_id,projection_type,source_system,connection_status,payload_json," +
                                "source_updated_at,synced_at FROM crm_account_projection_snapshots " +
                                "WHERE tenant_id=:tenantId AND account_id=:accountId " +
                                "ORDER BY projection_type,source_system",
                        params(tenantId).addValue("accountId", accountId))
                .stream().map(row -> new ProjectionSnapshotRecord(
                        uuid(row.get("id")), uuid(row.get("account_id")), string(row.get("projection_type")),
                        string(row.get("source_system")), string(row.get("connection_status")),
                        string(row.get("payload_json")), instant(row.get("source_updated_at")),
                        instant(row.get("synced_at")))).toList();
    }

    private AccountRelationshipRecord findRelationship(UUID tenantId, UUID relationshipId) {
        try {
            return mapRelationship(jdbc.queryForMap(
                    "SELECT " + RELATIONSHIP_COLUMNS + " FROM crm_account_relationships " +
                            "WHERE tenant_id=:tenantId AND id=:id",
                    params(tenantId).addValue("id", relationshipId)));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private ExternalIdentifierRecord findIdentifier(UUID tenantId, UUID accountId, UUID identifierId) {
        try {
            return mapIdentifier(jdbc.queryForMap(
                    "SELECT " + IDENTIFIER_COLUMNS + " FROM crm_account_external_identifiers " +
                            "WHERE tenant_id=:tenantId AND account_id=:accountId AND id=:id",
                    params(tenantId).addValue("accountId", accountId).addValue("id", identifierId)));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private TaxonomyRecord findTaxonomy(UUID tenantId, UUID taxonomyId) {
        try {
            return mapTaxonomy(jdbc.queryForMap(
                    "SELECT " + TAXONOMY_COLUMNS + " FROM crm_account_taxonomies " +
                            "WHERE tenant_id=:tenantId AND id=:id",
                    params(tenantId).addValue("id", taxonomyId)));
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private AccountProfileRecord mapProfile(Map<String, Object> row) {
        return new AccountProfileRecord(
                uuid(row.get("account_id")), number(row.get("version")), string(row.get("legal_name")),
                string(row.get("trade_name")), string(row.get("registration_number")),
                string(row.get("tax_registration_number")), string(row.get("industry")),
                string(row.get("organization_size")), string(row.get("website_url")),
                string(row.get("customer_tier")), string(row.get("risk_level")),
                decodeFlags(string(row.get("risk_flags"))), uuid(row.get("classification_id")),
                uuid(row.get("segment_id")), bool(row.get("merge_candidate")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private AccountRelationshipRecord mapRelationship(Map<String, Object> row) {
        return new AccountRelationshipRecord(
                uuid(row.get("id")), number(row.get("version")), uuid(row.get("source_account_id")),
                uuid(row.get("target_account_id")), string(row.get("relationship_type")),
                string(row.get("status")), localDate(row.get("effective_from")),
                localDate(row.get("effective_to")), string(row.get("description")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private ExternalIdentifierRecord mapIdentifier(Map<String, Object> row) {
        return new ExternalIdentifierRecord(
                uuid(row.get("id")), uuid(row.get("account_id")), string(row.get("provider")),
                string(row.get("system_scope")), string(row.get("external_id")),
                string(row.get("label")), bool(row.get("active")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private TaxonomyRecord mapTaxonomy(Map<String, Object> row) {
        return new TaxonomyRecord(
                uuid(row.get("id")), number(row.get("version")), string(row.get("taxonomy_type")),
                string(row.get("code")), string(row.get("name_ar")), string(row.get("name_en")),
                uuid(row.get("parent_id")), bool(row.get("active")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private static MapSqlParameterSource params(UUID tenantId) {
        return new MapSqlParameterSource("tenantId", tenantId);
    }

    private static String encodeFlags(List<String> flags) {
        return flags == null ? null : String.join("|", flags);
    }

    private static List<String> decodeFlags(String flags) {
        if (flags == null || flags.isBlank()) return List.of();
        return Arrays.stream(flags.split("\\|"))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(value.toString());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private static LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof LocalDate localDate) return localDate;
        return LocalDate.parse(value.toString());
    }
}
