package com.sanad.platform.crm.query.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.query.domain.Customer360QueryPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcCustomer360QueryAdapter implements Customer360QueryPort {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCustomer360QueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Customer360View getCustomer360(UUID tenantId, UUID accountId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("accountId", accountId);
        Map<String, Object> account;
        try {
            account = jdbc.queryForMap(
                    """
                    SELECT id, display_name, account_type, lifecycle_status
                    FROM crm_accounts
                    WHERE tenant_id = :tenantId AND id = :accountId
                    """, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND);
        }

        List<ContactSummary> contacts = jdbc.queryForList(
                        """
                        SELECT c.id, c.display_name, c.primary_email, c.lifecycle_status,
                               r.id AS relationship_id, r.role_code, r.status AS relationship_status,
                               r.primary_relationship, r.valid_from, r.valid_to,
                               r.job_title, r.department
                        FROM crm_contact_account_relationships r
                        JOIN crm_contacts c
                          ON c.tenant_id = r.tenant_id AND c.id = r.contact_id
                        WHERE r.tenant_id = :tenantId
                          AND r.account_id = :accountId
                          AND r.status <> 'ARCHIVED'
                        ORDER BY r.primary_relationship DESC, c.display_name ASC, r.id ASC
                        """, parameters)
                .stream().map(this::contact).toList();
        List<OpportunitySummary> opportunities = jdbc.queryForList(
                        """
                        SELECT id, name, amount, currency_code, status
                        FROM crm_opportunities
                        WHERE tenant_id = :tenantId AND account_id = :accountId
                        ORDER BY updated_at DESC, id DESC
                        """, parameters)
                .stream().map(row -> new OpportunitySummary(
                        uuid(row.get("id")), string(row.get("name")),
                        (BigDecimal) row.get("amount"), string(row.get("currency_code")),
                        string(row.get("status")))).toList();
        List<ActivitySummary> activities = jdbc.queryForList(
                        """
                        SELECT id, activity_type, subject, status
                        FROM crm_activities
                        WHERE tenant_id = :tenantId
                          AND related_type = 'ACCOUNT'
                          AND related_id = :accountId
                        ORDER BY updated_at DESC, id DESC
                        """, parameters)
                .stream().map(row -> new ActivitySummary(
                        uuid(row.get("id")), string(row.get("activity_type")),
                        string(row.get("subject")), string(row.get("status")))).toList();
        Integer timelineCount = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM crm_timeline_events
                WHERE tenant_id = :tenantId AND subject_type = 'ACCOUNT' AND subject_id = :accountId
                """, parameters, Integer.class);
        return new Customer360View(
                accountId,
                string(account.get("display_name")),
                string(account.get("account_type")),
                string(account.get("lifecycle_status")),
                contacts.size(), opportunities.size(), activities.size(),
                timelineCount == null ? 0 : timelineCount,
                contacts, opportunities, activities);
    }

    private ContactSummary contact(Map<String, Object> row) {
        return new ContactSummary(
                uuid(row.get("id")), string(row.get("display_name")),
                string(row.get("primary_email")), string(row.get("lifecycle_status")),
                uuid(row.get("relationship_id")), string(row.get("role_code")),
                string(row.get("relationship_status")), Boolean.TRUE.equals(row.get("primary_relationship")),
                localDate(row.get("valid_from")), localDate(row.get("valid_to")),
                string(row.get("job_title")), string(row.get("department")));
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(value.toString());
    }
}
