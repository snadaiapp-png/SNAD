package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.CompleteActivityRequest;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyActivityService {

    private final LegacySupport support;

    public LegacyActivityService(LegacySupport support) {
        this.support = support;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listActivities(
            Authentication authentication, int requestedLimit,
            String relatedType, UUID relatedId, String status) {
        UUID tenantId = support.tenantId(authentication);
        StringBuilder sql =
                new StringBuilder("SELECT * FROM crm_activities WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        if (relatedType != null && !relatedType.isBlank()) {
            sql.append(" AND related_type=:relatedType");
            params.addValue("relatedType", relatedType.trim().toUpperCase(Locale.ROOT));
        }
        if (relatedId != null) {
            sql.append(" AND related_id=:relatedId");
            params.addValue("relatedId", relatedId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=:status");
            params.addValue("status", status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY updated_at DESC,id LIMIT :limit");
        params.addValue("limit", limit(requestedLimit));
        return support.jdbc.queryForList(sql.toString(), params);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getActivity(Authentication authentication, UUID activityId) {
        UUID tenantId = support.tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(support.one("crm_activities", tenantId, activityId,
                        "CRM activity not found"));
        return result;
    }

    @Transactional
    public Map<String, Object> completeActivity(
            Authentication authentication, UUID activityId, CompleteActivityRequest request) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Map<String, Object> activity =
                support.one("crm_activities", tenantId, activityId, "CRM activity not found");
        String status = String.valueOf(activity.get("status"));
        if (!Set.of("OPEN", "IN_PROGRESS").contains(status)) {
            throw conflict("CRM activity cannot be completed from status " + status);
        }
        Instant now = Instant.now();
        support.jdbc.update(
                "UPDATE crm_activities SET status='COMPLETED',completed_at=:now,body=COALESCE(:result,body)," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                support.context(tenantId, actorId, activityId, now)
                        .addValue("result", optional(request.result(), 4000, "result")));
        Object relatedType = activity.get("related_type");
        Object relatedId = activity.get("related_id");
        if (relatedType != null && relatedId != null) {
            support.timeline(tenantId, relatedType.toString(), asUuid(relatedId),
                    "crm.activity.completed", "Activity completed",
                    "CRM_ACTIVITY", activityId, actorId, now);
        }
        return getActivity(authentication, activityId);
    }

    @Transactional
    public Map<String, Object> updateActivity(
            Authentication authentication, UUID activityId,
            String subject, String body, Integer priority, long expectedVersion) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", activityId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(now))
                .addValue("subject", subject)
                .addValue("body", body)
                .addValue("priority", priority);
        StringBuilder sql = new StringBuilder("UPDATE crm_activities SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (subject != null) { sql.append(", subject = :subject"); }
        if (body != null) { sql.append(", body = :body"); }
        if (priority != null) { sql.append(", priority = :priority"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = support.jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return support.one("crm_activities", tenantId, activityId, "CRM activity not found");
    }
}
