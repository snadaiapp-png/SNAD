package com.sanad.platform.crm.portal.infrastructure;

import com.sanad.platform.crm.portal.domain.CustomerPortalProfile;
import com.sanad.platform.crm.portal.domain.CustomerPortalTicket;
import com.sanad.platform.crm.portal.domain.PortalRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of the PortalRepository port.
 * Provides tenant-isolated customer portal queries.
 */
@Repository
public class JdbcPortalRepository implements PortalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPortalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CustomerPortalProfile> getProfile(UUID tenantId, UUID customerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("c", customerId);
        List<CustomerPortalProfile> results = jdbc.query(
                "SELECT id, tenant_id, display_name, primary_email, primary_phone, created_at, updated_at " +
                "FROM crm_contacts WHERE tenant_id = :t AND id = :c",
                params,
                (rs, rowNum) -> new CustomerPortalProfile(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("primary_email"),
                        null,
                        rs.getString("primary_phone"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()));
        return results.stream().findFirst();
    }

    @Override
    public CustomerPortalProfile updateProfile(UUID tenantId, UUID customerId, CustomerPortalProfile profile) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("c", customerId)
                .addValue("name", profile.displayName())
                .addValue("phone", profile.phone())
                .addValue("now", Instant.now());
        jdbc.update(
                "UPDATE crm_contacts SET display_name = :name, primary_phone = :phone, updated_at = :now " +
                "WHERE tenant_id = :t AND id = :c", params);
        return getProfile(tenantId, customerId).orElse(profile);
    }

    @Override
    public List<CustomerPortalTicket> getTickets(UUID tenantId, UUID customerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("c", customerId);
        return jdbc.query(
                "SELECT id, customer_id, tenant_id, subject, description, status, priority, created_at, updated_at " +
                "FROM crm_cases WHERE tenant_id = :t AND customer_id = :c ORDER BY created_at DESC",
                params,
                (rs, rowNum) -> new CustomerPortalTicket(
                        rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("subject"),
                        rs.getString("description"),
                        rs.getString("status"),
                        rs.getString("priority"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public CustomerPortalTicket createTicket(UUID tenantId, UUID customerId, CustomerPortalTicket ticket) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", tenantId)
                .addValue("c", customerId)
                .addValue("subject", ticket.subject())
                .addValue("description", ticket.description())
                .addValue("status", "OPEN")
                .addValue("priority", ticket.priority())
                .addValue("now", now);
        jdbc.update(
                "INSERT INTO crm_cases (id, tenant_id, customer_id, subject, description, status, priority, created_at, updated_at) " +
                "VALUES (:id, :t, :c, :subject, :description, :status, :priority, :now, :now)", params);
        return getTicket(tenantId, customerId, id).orElse(ticket);
    }

    @Override
    public Optional<CustomerPortalTicket> getTicket(UUID tenantId, UUID customerId, UUID ticketId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("c", customerId)
                .addValue("id", ticketId);
        List<CustomerPortalTicket> results = jdbc.query(
                "SELECT id, customer_id, tenant_id, subject, description, status, priority, created_at, updated_at " +
                "FROM crm_cases WHERE tenant_id = :t AND customer_id = :c AND id = :id",
                params,
                (rs, rowNum) -> new CustomerPortalTicket(
                        rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("subject"),
                        rs.getString("description"),
                        rs.getString("status"),
                        rs.getString("priority"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()));
        return results.stream().findFirst();
    }

    @Override
    public List<Map<String, Object>> getCustomerOpportunities(UUID tenantId, UUID customerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("c", customerId);
        return jdbc.queryForList(
                "SELECT o.id, o.name, o.amount, o.currency_code, o.status, o.probability, " +
                "ps.name as stage_name, o.expected_close_date " +
                "FROM crm_opportunities o " +
                "JOIN crm_pipeline_stages ps ON o.tenant_id = ps.tenant_id AND o.stage_id = ps.id " +
                "WHERE o.tenant_id = :t AND o.contact_id = :c AND o.status = 'OPEN' " +
                "ORDER BY o.expected_close_date", params);
    }
}
