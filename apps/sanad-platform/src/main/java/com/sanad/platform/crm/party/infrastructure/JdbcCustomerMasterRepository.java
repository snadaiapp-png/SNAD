package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcCustomerMasterRepository implements CustomerMasterRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCustomerMasterRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CustomerMasterProfile findProfile(UUID tenantId, UUID accountId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT * FROM crm_accounts WHERE tenant_id=:tenantId AND id=:accountId",
                    p().addValue("tenantId", tenantId).addValue("accountId", accountId));
            return mapProfile(row);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        }
    }

    @Override
    public CustomerMasterProfile updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UpdateCustomerMasterCommand command,
            long expectedVersion) {
        CustomerMasterProfile current = findProfile(tenantId, accountId);
        if (current.mergedIntoAccountId() != null) {
            throw new CrmContractException(CrmErrorCode.CONFLICT, "Merged customer records cannot be modified.");
        }
        String legalName = value(command.legalName(), current.legalName(), current.displayName());
        String tradingName = value(command.tradingName(), current.tradingName(), null);
        String registrationNumber = value(command.registrationNumber(), current.registrationNumber(), null);
        String taxNumber = value(command.taxNumber(), current.taxNumber(), null);
        String industryCode = value(command.industryCode(), current.industryCode(), null);
        String segment = value(command.customerSegment(), current.customerSegment(), null);
        String tier = value(command.customerTier(), current.customerTier(), null);
        String website = value(command.website(), current.website(), null);
        String email = value(command.primaryEmail(), current.primaryEmail(), null);
        String phone = value(command.primaryPhone(), current.primaryPhone(), null);
        String country = value(command.countryCode(), current.countryCode(), null);
        String risk = value(command.riskRating(), current.riskRating(), "UNASSESSED");
        BigDecimal creditLimit = command.creditLimit() == null ? current.creditLimit() : command.creditLimit();
        Integer paymentTerms = command.paymentTermsDays() == null ? current.paymentTermsDays() : command.paymentTermsDays();
        int quality = qualityScore(legalName, registrationNumber, taxNumber, email, phone, country, industryCode, segment);
        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE crm_accounts SET legal_name=:legalName,trading_name=:tradingName," +
                        "registration_number=:registrationNumber,tax_number=:taxNumber,industry_code=:industryCode," +
                        "customer_segment=:segment,customer_tier=:tier,website=:website,primary_email=:email," +
                        "primary_phone=:phone,country_code=:country,risk_rating=:risk,credit_limit=:creditLimit," +
                        "payment_terms_days=:paymentTerms,data_quality_score=:quality,updated_by=:actorId," +
                        "updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:accountId AND version=:expectedVersion",
                p().addValue("tenantId", tenantId).addValue("accountId", accountId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("legalName", legalName).addValue("tradingName", tradingName)
                        .addValue("registrationNumber", registrationNumber).addValue("taxNumber", taxNumber)
                        .addValue("industryCode", industryCode).addValue("segment", segment).addValue("tier", tier)
                        .addValue("website", website).addValue("email", email).addValue("phone", phone)
                        .addValue("country", upper(country)).addValue("risk", upper(risk))
                        .addValue("creditLimit", creditLimit).addValue("paymentTerms", paymentTerms)
                        .addValue("quality", quality).addValue("now", Timestamp.from(now)));
        if (updated != 1) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findProfile(tenantId, accountId);
    }

    @Override
    public List<AccountAddress> listAddresses(UUID tenantId, UUID accountId) {
        findProfile(tenantId, accountId);
        return jdbc.queryForList(
                "SELECT * FROM crm_account_addresses WHERE tenant_id=:tenantId AND account_id=:accountId " +
                        "AND active=TRUE ORDER BY primary_address DESC,address_type,updated_at DESC,id",
                p().addValue("tenantId", tenantId).addValue("accountId", accountId))
                .stream().map(this::mapAddress).toList();
    }

    @Override
    public AccountAddress addAddress(UUID tenantId, UUID actorId, UUID accountId, CreateAddressCommand command) {
        findProfile(tenantId, accountId);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        if (command.primaryAddress()) {
            jdbc.update("UPDATE crm_account_addresses SET primary_address=FALSE,updated_by=:actorId,updated_at=:now," +
                            "version=version+1 WHERE tenant_id=:tenantId AND account_id=:accountId AND active=TRUE",
                    p().addValue("tenantId", tenantId).addValue("accountId", accountId)
                            .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        }
        jdbc.update(
                "INSERT INTO crm_account_addresses (id,tenant_id,account_id,version,address_type,label,line1,line2," +
                        "city,state_region,postal_code,country_code,primary_address,active,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:accountId,0,:addressType,:label,:line1,:line2,:city,:stateRegion," +
                        ":postalCode,:countryCode,:primaryAddress,TRUE,:actorId,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", tenantId).addValue("accountId", accountId)
                        .addValue("addressType", upper(command.addressType())).addValue("label", command.label())
                        .addValue("line1", command.line1()).addValue("line2", command.line2())
                        .addValue("city", command.city()).addValue("stateRegion", command.stateRegion())
                        .addValue("postalCode", command.postalCode()).addValue("countryCode", upper(command.countryCode()))
                        .addValue("primaryAddress", command.primaryAddress()).addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findAddress(tenantId, accountId, id);
    }

    @Override
    public void deactivateAddress(UUID tenantId, UUID actorId, UUID accountId, UUID addressId) {
        int updated = jdbc.update(
                "UPDATE crm_account_addresses SET active=FALSE,primary_address=FALSE,updated_by=:actorId," +
                        "updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND account_id=:accountId " +
                        "AND id=:addressId AND active=TRUE",
                p().addValue("tenantId", tenantId).addValue("accountId", accountId).addValue("addressId", addressId)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated != 1) throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND, "Address not found.");
    }

    @Override
    public List<AccountIdentifier> listIdentifiers(UUID tenantId, UUID accountId) {
        findProfile(tenantId, accountId);
        return jdbc.queryForList(
                "SELECT * FROM crm_account_identifiers WHERE tenant_id=:tenantId AND account_id=:accountId " +
                        "AND active=TRUE ORDER BY primary_identifier DESC,identifier_type,created_at,id",
                p().addValue("tenantId", tenantId).addValue("accountId", accountId))
                .stream().map(this::mapIdentifier).toList();
    }

    @Override
    public AccountIdentifier addIdentifier(
            UUID tenantId, UUID actorId, UUID accountId, CreateIdentifierCommand command) {
        findProfile(tenantId, accountId);
        String normalized = normalizeIdentity(command.identifierValue());
        Long duplicates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_account_identifiers WHERE tenant_id=:tenantId AND identifier_type=:type " +
                        "AND normalized_value=:value AND active=TRUE",
                p().addValue("tenantId", tenantId).addValue("type", upper(command.identifierType()))
                        .addValue("value", normalized), Long.class);
        if (duplicates != null && duplicates > 0) {
            throw new CrmContractException(CrmErrorCode.CRM_DUPLICATE_ACCOUNT,
                    "The customer identifier is already assigned within this tenant.");
        }
        if (command.primaryIdentifier()) {
            jdbc.update("UPDATE crm_account_identifiers SET primary_identifier=FALSE WHERE tenant_id=:tenantId " +
                            "AND account_id=:accountId AND identifier_type=:type AND active=TRUE",
                    p().addValue("tenantId", tenantId).addValue("accountId", accountId)
                            .addValue("type", upper(command.identifierType())));
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_account_identifiers (id,tenant_id,account_id,identifier_type,identifier_value," +
                        "normalized_value,issuer_country_code,primary_identifier,verified,active,created_by,created_at) " +
                        "VALUES (:id,:tenantId,:accountId,:type,:value,:normalized,:country,:primary,:verified,TRUE,:actorId,:now)",
                p().addValue("id", id).addValue("tenantId", tenantId).addValue("accountId", accountId)
                        .addValue("type", upper(command.identifierType())).addValue("value", command.identifierValue())
                        .addValue("normalized", normalized).addValue("country", upper(command.issuerCountryCode()))
                        .addValue("primary", command.primaryIdentifier()).addValue("verified", command.verified())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        return findIdentifier(tenantId, accountId, id);
    }

    @Override
    public List<AccountRelationship> listRelationships(UUID tenantId, UUID accountId) {
        findProfile(tenantId, accountId);
        return jdbc.queryForList(
                "SELECT * FROM crm_account_relationships WHERE tenant_id=:tenantId " +
                        "AND (source_account_id=:accountId OR target_account_id=:accountId) " +
                        "ORDER BY status,updated_at DESC,id",
                p().addValue("tenantId", tenantId).addValue("accountId", accountId))
                .stream().map(this::mapRelationship).toList();
    }

    @Override
    public AccountRelationship addRelationship(
            UUID tenantId, UUID actorId, UUID accountId, CreateRelationshipCommand command) {
        findProfile(tenantId, accountId);
        findProfile(tenantId, command.targetAccountId());
        if (accountId.equals(command.targetAccountId())) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "An account cannot relate to itself.");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_account_relationships (id,tenant_id,source_account_id,target_account_id," +
                        "relationship_type,status,effective_from,effective_to,notes,created_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:sourceId,:targetId,:type,'ACTIVE',:effectiveFrom,:effectiveTo,:notes,:actorId,:now,:now)",
                p().addValue("id", id).addValue("tenantId", tenantId).addValue("sourceId", accountId)
                        .addValue("targetId", command.targetAccountId()).addValue("type", upper(command.relationshipType()))
                        .addValue("effectiveFrom", command.effectiveFrom() == null ? null : Date.valueOf(command.effectiveFrom()))
                        .addValue("effectiveTo", command.effectiveTo() == null ? null : Date.valueOf(command.effectiveTo()))
                        .addValue("notes", command.notes()).addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findRelationship(tenantId, id);
    }

    @Override
    public List<DuplicateCandidate> findDuplicateCandidates(UUID tenantId, UUID accountId, int limit) {
        CustomerMasterProfile source = findProfile(tenantId, accountId);
        List<DuplicateCandidate> candidates = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT * FROM crm_accounts WHERE tenant_id=:tenantId AND id<>:accountId " +
                        "AND lifecycle_status<>'ARCHIVED' AND merged_into_account_id IS NULL ORDER BY updated_at DESC LIMIT 250",
                p().addValue("tenantId", tenantId).addValue("accountId", accountId))) {
            CustomerMasterProfile other = mapProfile(row);
            List<String> matches = new ArrayList<>();
            int score = 0;
            if (same(source.registrationNumber(), other.registrationNumber())) { score += 45; matches.add("registrationNumber"); }
            if (same(source.taxNumber(), other.taxNumber())) { score += 45; matches.add("taxNumber"); }
            if (same(source.primaryEmail(), other.primaryEmail())) { score += 35; matches.add("primaryEmail"); }
            if (same(source.legalName(), other.legalName())) { score += 25; matches.add("legalName"); }
            if (same(source.displayName(), other.displayName())) { score += 20; matches.add("displayName"); }
            if (score >= 25) {
                candidates.add(new DuplicateCandidate(other.accountId(), other.displayName(), other.legalName(),
                        other.registrationNumber(), other.taxNumber(), other.primaryEmail(), Math.min(score, 100), matches));
            }
        }
        return candidates.stream()
                .sorted((left, right) -> Integer.compare(right.confidenceScore(), left.confidenceScore()))
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
    }

    @Override
    public MergeResult mergeAccounts(
            UUID tenantId,
            UUID actorId,
            UUID sourceAccountId,
            UUID targetAccountId,
            long expectedSourceVersion,
            long expectedTargetVersion,
            String reason) {
        if (sourceAccountId.equals(targetAccountId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Source and target accounts must differ.");
        }
        CustomerMasterProfile source = findProfile(tenantId, sourceAccountId);
        CustomerMasterProfile target = findProfile(tenantId, targetAccountId);
        if (source.mergedIntoAccountId() != null || target.mergedIntoAccountId() != null) {
            throw new CrmContractException(CrmErrorCode.CONFLICT, "A merged account cannot participate in another merge.");
        }
        if (source.version() != expectedSourceVersion || target.version() != expectedTargetVersion) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        Instant now = Instant.now();
        MapSqlParameterSource params = p().addValue("tenantId", tenantId).addValue("sourceId", sourceAccountId)
                .addValue("targetId", targetAccountId).addValue("actorId", actorId)
                .addValue("now", Timestamp.from(now));
        int contacts = jdbc.update(
                "UPDATE crm_contacts SET account_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND account_id=:sourceId", params);
        int opportunities = jdbc.update(
                "UPDATE crm_opportunities SET account_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND account_id=:sourceId", params);
        int activities = jdbc.update(
                "UPDATE crm_activities SET related_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND related_type='ACCOUNT' AND related_id=:sourceId", params);
        jdbc.update(
                "UPDATE crm_accounts SET parent_account_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND parent_account_id=:sourceId AND id<>:targetId", params);
        int sourceUpdated = jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status='ARCHIVED',archived_at=:now,merged_into_account_id=:targetId," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:sourceId " +
                        "AND version=:sourceVersion",
                params.addValue("sourceVersion", expectedSourceVersion));
        int targetUpdated = jdbc.update(
                "UPDATE crm_accounts SET updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:targetId AND version=:targetVersion",
                params.addValue("targetVersion", expectedTargetVersion));
        if (sourceUpdated != 1 || targetUpdated != 1) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        jdbc.update(
                "INSERT INTO crm_account_status_history (id,tenant_id,account_id,previous_status,new_status,reason," +
                        "changed_by,changed_at) VALUES (:id,:tenantId,:sourceId,:previousStatus,'ARCHIVED',:reason,:actorId,:now)",
                params.addValue("id", UUID.randomUUID()).addValue("previousStatus", source.lifecycleStatus())
                        .addValue("reason", reason));
        jdbc.update(
                "INSERT INTO crm_account_merge_history (id,tenant_id,source_account_id,target_account_id,source_version," +
                        "target_version,contacts_moved,opportunities_moved,activities_moved,reason,merged_by,merged_at) " +
                        "VALUES (:mergeId,:tenantId,:sourceId,:targetId,:sourceVersion,:targetVersion,:contacts," +
                        ":opportunities,:activities,:reason,:actorId,:now)",
                params.addValue("mergeId", UUID.randomUUID()).addValue("contacts", contacts)
                        .addValue("opportunities", opportunities).addValue("activities", activities));
        return new MergeResult(sourceAccountId, targetAccountId, expectedSourceVersion + 1,
                expectedTargetVersion + 1, contacts, opportunities, activities, now);
    }

    private AccountAddress findAddress(UUID tenantId, UUID accountId, UUID id) {
        try {
            return mapAddress(jdbc.queryForMap(
                    "SELECT * FROM crm_account_addresses WHERE tenant_id=:tenantId AND account_id=:accountId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("accountId", accountId).addValue("id", id)));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND, "Address not found.");
        }
    }

    private AccountIdentifier findIdentifier(UUID tenantId, UUID accountId, UUID id) {
        try {
            return mapIdentifier(jdbc.queryForMap(
                    "SELECT * FROM crm_account_identifiers WHERE tenant_id=:tenantId AND account_id=:accountId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("accountId", accountId).addValue("id", id)));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND, "Identifier not found.");
        }
    }

    private AccountRelationship findRelationship(UUID tenantId, UUID id) {
        try {
            return mapRelationship(jdbc.queryForMap(
                    "SELECT * FROM crm_account_relationships WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", id)));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND, "Relationship not found.");
        }
    }

    private CustomerMasterProfile mapProfile(Map<String, Object> row) {
        return new CustomerMasterProfile(
                uuid(row.get("id")), number(row.get("version")), string(row.get("display_name")),
                string(row.get("account_type")), string(row.get("lifecycle_status")),
                value(string(row.get("legal_name")), null, string(row.get("display_name"))),
                string(row.get("trading_name")), string(row.get("registration_number")),
                string(row.get("tax_number")), string(row.get("industry_code")),
                string(row.get("customer_segment")), string(row.get("customer_tier")), string(row.get("website")),
                string(row.get("primary_email")), string(row.get("primary_phone")), string(row.get("country_code")),
                value(string(row.get("risk_rating")), null, "UNASSESSED"), decimal(row.get("credit_limit")),
                integer(row.get("payment_terms_days")), integer(row.get("data_quality_score"), 0),
                uuid(row.get("merged_into_account_id")), instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private AccountAddress mapAddress(Map<String, Object> row) {
        return new AccountAddress(uuid(row.get("id")), number(row.get("version")), uuid(row.get("account_id")),
                string(row.get("address_type")), string(row.get("label")), string(row.get("line1")),
                string(row.get("line2")), string(row.get("city")), string(row.get("state_region")),
                string(row.get("postal_code")), string(row.get("country_code")), bool(row.get("primary_address")),
                bool(row.get("active")), instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private AccountIdentifier mapIdentifier(Map<String, Object> row) {
        return new AccountIdentifier(uuid(row.get("id")), uuid(row.get("account_id")),
                string(row.get("identifier_type")), string(row.get("identifier_value")),
                string(row.get("issuer_country_code")), bool(row.get("primary_identifier")),
                bool(row.get("verified")), bool(row.get("active")), instant(row.get("created_at")));
    }

    private AccountRelationship mapRelationship(Map<String, Object> row) {
        return new AccountRelationship(uuid(row.get("id")), uuid(row.get("source_account_id")),
                uuid(row.get("target_account_id")), string(row.get("relationship_type")), string(row.get("status")),
                localDate(row.get("effective_from")), localDate(row.get("effective_to")), string(row.get("notes")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static UUID uuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
    private static Integer integer(Object value) { return value == null ? null : integer(value, 0); }
    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }
    private static boolean bool(Object value) {
        if (value == null) return false;
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }
    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        return Instant.parse(String.valueOf(value));
    }
    private static LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(String.valueOf(value));
    }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static String value(String proposed, String current, String fallback) {
        if (proposed != null) return proposed.trim().isEmpty() ? null : proposed.trim();
        if (current != null) return current;
        return fallback;
    }
    private static String normalizeIdentity(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
    private static boolean same(String left, String right) {
        String a = normalizeIdentity(left);
        String b = normalizeIdentity(right);
        return !a.isBlank() && a.equals(b);
    }
    private static int qualityScore(String legalName, String registrationNumber, String taxNumber, String email,
                                    String phone, String country, String industry, String segment) {
        int score = 0;
        if (legalName != null) score += 20;
        if (registrationNumber != null) score += 15;
        if (taxNumber != null) score += 15;
        if (email != null) score += 15;
        if (phone != null) score += 10;
        if (country != null) score += 10;
        if (industry != null) score += 10;
        if (segment != null) score += 5;
        return Math.min(score, 100);
    }
}
