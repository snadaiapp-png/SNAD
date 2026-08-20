package com.sanad.platform.crm.caller.infrastructure;

import com.sanad.platform.crm.caller.domain.CallerCandidate;
import com.sanad.platform.crm.caller.domain.CallerIdentificationRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of the caller-candidate contract (G8-02 §6, §33).
 *
 * <p>The canonical query is an exact point lookup over
 * {@code idx_crm_communication_methods_lookup (tenant_id, method_type, normalized_value, status)}
 * — the index exists in the schema (V20260717_100) and has no consumer today;
 * G8 is that consumer. Candidate rows are bounded ({@value #FETCH_LIMIT}) and
 * every predicate stays tenant-scoped; no full-table scans under the expected
 * predicate.
 */
@Repository
public class JdbcCallerIdentificationRepository implements CallerIdentificationRepository {

    /** Fetch {@value #CANDIDATE_LOOKUP_LIMIT} + 1 rows to detect overflow (=> AMBIGUOUS). */
    private static final int FETCH_LIMIT = CANDIDATE_LOOKUP_LIMIT + 1;

    private static final String CANONICAL_SQL = """
            SELECT cm.id                       AS method_id,
                   cm.owner_type,
                   cm.owner_id,
                   cm.normalized_value,
                   cm.label                    AS phone_label,
                   cm.preferred,
                   cm.verified,
                   cm.verification_status,
                   cm.privacy_classification,
                   cm.updated_at,
                   c.id                        AS contact_id,
                   c.display_name              AS contact_name,
                   c.owner_user_id             AS contact_owner,
                   a.id                        AS account_id,
                   a.display_name              AS account_name,
                   a.owner_user_id             AS account_owner,
                   contact_account.display_name AS contact_account_name
            FROM crm_communication_methods cm
            LEFT JOIN crm_contacts c      ON c.tenant_id = cm.tenant_id AND c.id = cm.contact_id
            LEFT JOIN crm_accounts a      ON a.tenant_id = cm.tenant_id AND a.id = cm.account_id
            LEFT JOIN crm_accounts contact_account
                                          ON contact_account.tenant_id = cm.tenant_id
                                         AND contact_account.id = c.account_id
            WHERE cm.tenant_id = :tenantId
              AND cm.method_type IN ('PHONE', 'MOBILE')
              AND cm.normalized_value = :phone
              AND cm.status = 'ACTIVE'
              AND ((cm.owner_type = 'PERSON' AND c.id IS NOT NULL AND c.lifecycle_status = 'ACTIVE')
                   OR (cm.owner_type = 'ACCOUNT' AND a.id IS NOT NULL AND a.lifecycle_status = 'ACTIVE'))
            ORDER BY cm.verified DESC, cm.preferred DESC, cm.updated_at ASC, cm.id ASC
            LIMIT :limit
            """;

    private static final String LEAD_FALLBACK_SQL = """
            SELECT id,
                   display_name,
                   company_name,
                   phone,
                   owner_user_id,
                   status,
                   updated_at
            FROM crm_leads
            WHERE tenant_id = :tenantId
              AND status IN ('NEW', 'ASSIGNED', 'CONTACTED', 'QUALIFIED')
              AND phone IN (:form1, :form2, :form3, :form4)
            ORDER BY updated_at ASC, id ASC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCallerIdentificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CallerCandidate> findActiveCallerCandidates(UUID tenantId, String normalizedPhone) {
        return jdbc.query(CANONICAL_SQL, new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("phone", normalizedPhone)
                        .addValue("limit", FETCH_LIMIT),
                canonicalRowMapper);
    }

    @Override
    public List<CallerCandidate> findActiveLeadCandidates(UUID tenantId, String normalizedPhone) {
        List<String> forms = CallerIdentificationRepository.legacyLeadPhoneForms(normalizedPhone);
        if (forms.isEmpty()) return List.of();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", FETCH_LIMIT);
        for (int i = 0; i < forms.size(); i++) {
            params.addValue("form" + (i + 1), forms.get(i));
        }
        for (int i = forms.size(); i < 4; i++) {
            params.addValue("form" + (i + 1), "__no_match__");
        }
        return jdbc.query(LEAD_FALLBACK_SQL, params, leadRowMapper);
    }

    private static final RowMapper<CallerCandidate> canonicalRowMapper = (rs, rowNum) -> {
        String ownerType = rs.getString("owner_type");
        String accountName = "ACCOUNT".equals(ownerType)
                ? rs.getString("account_name")
                : rs.getString("contact_account_name");
        String displayName = "ACCOUNT".equals(ownerType)
                ? rs.getString("account_name")
                : rs.getString("contact_name");
        UUID accountId = "ACCOUNT".equals(ownerType)
                ? uuid(rs, "account_id")
                : null;
        UUID contactId = "PERSON".equals(ownerType)
                ? uuid(rs, "contact_id")
                : null;
        UUID ownerUserId = "ACCOUNT".equals(ownerType)
                ? uuid(rs, "account_owner")
                : uuid(rs, "contact_owner");
        return new CallerCandidate(
                uuid(rs, "method_id"),
                ownerType,
                uuid(rs, "owner_id"),
                rs.getString("normalized_value"),
                rs.getString("phone_label"),
                rs.getBoolean("preferred"),
                rs.getBoolean("verified"),
                rs.getString("verification_status"),
                rs.getString("privacy_classification"),
                "ACTIVE",
                contactId,
                accountId,
                null,
                displayName,
                accountName,
                ownerUserId,
                rs.getTimestamp("updated_at") == null ? null
                        : rs.getTimestamp("updated_at").toInstant(),
                CallerCandidate.SOURCE_CANONICAL);
    };

    private static final RowMapper<CallerCandidate> leadRowMapper = (rs, rowNum) -> new CallerCandidate(
            null,
            "LEAD",
            uuid(rs, "id"),
            rs.getString("phone"),
            null,
            false,
            false,
            "UNVERIFIED",
            "INTERNAL",
            "ACTIVE",
            null,
            null,
            uuid(rs, "id"),
            rs.getString("display_name"),
            rs.getString("company_name"),
            uuid(rs, "owner_user_id"),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant(),
            CallerCandidate.SOURCE_LEGACY_LEAD_PHONE);

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : UUID.fromString(value.toString());
    }
}
