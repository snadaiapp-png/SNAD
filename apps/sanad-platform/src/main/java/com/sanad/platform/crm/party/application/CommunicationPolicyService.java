package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CommunicationPolicyService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditPort audit;
    private final ObjectMapper mapper;

    public CommunicationPolicyService(
            NamedParameterJdbcTemplate jdbc,
            AuditPort audit,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.mapper = mapper;
    }

    public CommunicationPolicy policy(UUID tenantId) {
        List<CommunicationPolicy> rows = jdbc.query(
                "SELECT email_unique_within_owner,phone_unique_within_owner,single_preferred_per_type " +
                        "FROM crm_communication_policies WHERE tenant_id=:tenantId",
                new MapSqlParameterSource("tenantId", tenantId),
                (rs, rowNum) -> new CommunicationPolicy(
                        rs.getBoolean("email_unique_within_owner"),
                        rs.getBoolean("phone_unique_within_owner"),
                        rs.getBoolean("single_preferred_per_type")));
        return rows.isEmpty() ? new CommunicationPolicy(true, true, true) : rows.get(0);
    }

    @Transactional
    public CommunicationPolicy update(
            UUID tenantId,
            UUID actorId,
            CommunicationPolicy requested) {
        CommunicationPolicy before = policy(tenantId);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("emailUnique", requested.emailUniqueWithinOwner())
                .addValue("phoneUnique", requested.phoneUniqueWithinOwner())
                .addValue("singlePreferred", requested.singlePreferredPerType())
                .addValue("actorId", actorId)
                .addValue("now", java.sql.Timestamp.from(now));
        int updated = jdbc.update(
                "UPDATE crm_communication_policies SET email_unique_within_owner=:emailUnique," +
                        "phone_unique_within_owner=:phoneUnique,single_preferred_per_type=:singlePreferred," +
                        "updated_by=:actorId,updated_at=:now WHERE tenant_id=:tenantId",
                params);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO crm_communication_policies (tenant_id,email_unique_within_owner," +
                            "phone_unique_within_owner,single_preferred_per_type,created_by,updated_by," +
                            "created_at,updated_at) VALUES (:tenantId,:emailUnique,:phoneUnique,:singlePreferred," +
                            ":actorId,:actorId,:now,:now)",
                    params);
        }
        CommunicationPolicy after = policy(tenantId);
        audit.record(tenantId, actorId, "UPDATE_COMMUNICATION_POLICY", "CRM_COMMUNICATION_POLICY",
                tenantId, new AuditChange(mapper.valueToTree(before), mapper.valueToTree(after)), now);
        return after;
    }

    public record CommunicationPolicy(
            boolean emailUniqueWithinOwner,
            boolean phoneUniqueWithinOwner,
            boolean singlePreferredPerType) {}
}
