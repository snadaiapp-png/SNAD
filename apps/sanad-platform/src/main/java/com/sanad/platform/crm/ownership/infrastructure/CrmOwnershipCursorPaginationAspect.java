package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.ownership.application.TransferQueryUseCases;
import com.sanad.platform.crm.ownership.domain.AssignmentRule;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import com.sanad.platform.crm.ownership.domain.RuleStatus;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import com.sanad.platform.crm.ownership.domain.TransferRequest;
import com.sanad.platform.crm.ownership.domain.TransferState;
import com.sanad.platform.crm.pagination.CursorCodec;
import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Bounded PostgreSQL keyset pagination for CRM-008 ownership collections. */
@Aspect
@Component
public class CrmOwnershipCursorPaginationAspect {

    private final NamedParameterJdbcTemplate jdbc;
    private final CursorCodec cursors;
    private final CapabilityAuthorizationAspect authorization;

    public CrmOwnershipCursorPaginationAspect(
            NamedParameterJdbcTemplate jdbc,
            CursorCodec cursors,
            CapabilityAuthorizationAspect authorization) {
        this.jdbc = jdbc;
        this.cursors = cursors;
        this.authorization = authorization;
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.listTeams(..))")
    public Object pageTeams(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        UUID tenantId = contextUuid(invocation.authentication(), "tenant_id");
        TeamStatus status = enumParam(invocation.request(), "status", TeamStatus.class, TeamStatus.ACTIVE);
        int pageSize = pageSize(invocation.request());
        String filterKey = "crm008:teams:" + status.name() + ":id";
        UUID afterId = afterId(invocation.request(), tenantId, filterKey);
        MapSqlParameterSource parameters = baseParameters(tenantId, pageSize);
        String cursorClause = cursorClause(afterId, parameters);

        List<SalesTeam> rows = jdbc.query(
                "SELECT * FROM crm_sales_teams "
                        + "WHERE tenant_id=:tenantId AND status=:status "
                        + cursorClause
                        + "ORDER BY id ASC LIMIT :limit",
                parameters.addValue("status", status.name()),
                OwnershipJdbcSupport.salesTeamMapper());
        return response(rows, pageSize, tenantId, filterKey, SalesTeam::id, invocation.request());
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.listQueues(..))")
    public Object pageQueues(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        UUID tenantId = contextUuid(invocation.authentication(), "tenant_id");
        QueueStatus status = enumParam(invocation.request(), "status", QueueStatus.class, null);
        int pageSize = pageSize(invocation.request());
        String filterKey = "crm008:queues:" + (status == null ? "ALL" : status.name()) + ":id";
        UUID afterId = afterId(invocation.request(), tenantId, filterKey);
        String statusClause = status == null ? "" : "AND status=:status ";
        MapSqlParameterSource parameters = baseParameters(tenantId, pageSize);
        String cursorClause = cursorClause(afterId, parameters);
        if (status != null) parameters.addValue("status", status.name());

        List<Queue> rows = jdbc.query(
                "SELECT * FROM crm_queues WHERE tenant_id=:tenantId "
                        + statusClause
                        + cursorClause
                        + "ORDER BY id ASC LIMIT :limit",
                parameters,
                OwnershipJdbcSupport.queueMapper());
        return response(rows, pageSize, tenantId, filterKey, Queue::id, invocation.request());
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipTransferController.listTransfers(..))")
    public Object pageTransfers(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        UUID tenantId = contextUuid(invocation.authentication(), "tenant_id");
        UUID userId = contextUuid(invocation.authentication(), "user_id");
        TransferQueryUseCases.Direction direction = enumParam(
                invocation.request(), "direction", TransferQueryUseCases.Direction.class,
                TransferQueryUseCases.Direction.ALL);
        TransferState state = enumParam(invocation.request(), "state", TransferState.class, null);
        int pageSize = pageSize(invocation.request());
        String filterKey = "crm008:transfers:" + direction.name() + ":"
                + (state == null ? "ALL" : state.name()) + ":id";
        UUID afterId = afterId(invocation.request(), tenantId, filterKey);
        String stateClause = state == null ? "" : "AND state=:state ";
        MapSqlParameterSource parameters = baseParameters(tenantId, pageSize)
                .addValue("userId", userId)
                .addValue("direction", direction.name());
        String cursorClause = cursorClause(afterId, parameters);
        if (state != null) parameters.addValue("state", state.name());

        List<TransferRequest> rows = jdbc.query(
                "SELECT * FROM crm_transfer_requests "
                        + "WHERE tenant_id=:tenantId "
                        + "AND ((:direction='OUTGOING' AND requester_user_id=:userId) "
                        + " OR (:direction='INCOMING' AND proposed_owner_user_id=:userId) "
                        + " OR (:direction='ALL' AND (requester_user_id=:userId OR proposed_owner_user_id=:userId))) "
                        + stateClause
                        + cursorClause
                        + "ORDER BY id ASC LIMIT :limit",
                parameters,
                OwnershipJdbcSupport.transferRequestMapper());
        return response(rows, pageSize, tenantId, filterKey, TransferRequest::id, invocation.request());
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipAssignmentController.listRules(..))")
    public Object pageRules(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        UUID tenantId = contextUuid(invocation.authentication(), "tenant_id");
        RuleStatus status = enumParam(invocation.request(), "status", RuleStatus.class, null);
        int pageSize = pageSize(invocation.request());
        String filterKey = "crm008:assignment-rules:"
                + (status == null ? "ALL" : status.name()) + ":id";
        UUID afterId = afterId(invocation.request(), tenantId, filterKey);
        String statusClause = status == null ? "" : "AND status=:status ";
        MapSqlParameterSource parameters = baseParameters(tenantId, pageSize);
        String cursorClause = cursorClause(afterId, parameters);
        if (status != null) parameters.addValue("status", status.name());

        List<AssignmentRule> rows = jdbc.query(
                "SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId "
                        + statusClause
                        + cursorClause
                        + "ORDER BY id ASC LIMIT :limit",
                parameters,
                OwnershipJdbcSupport.assignmentRuleMapper());
        return response(rows, pageSize, tenantId, filterKey, AssignmentRule::id, invocation.request());
    }

    private MapSqlParameterSource baseParameters(UUID tenantId, int pageSize) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", pageSize + 1);
    }

    private String cursorClause(UUID afterId, MapSqlParameterSource parameters) {
        if (afterId == null) return "";
        parameters.addValue("afterId", afterId);
        return "AND id > :afterId ";
    }

    private void authorize(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            throw new IllegalStateException("Ownership pagination requires a method signature");
        }
        RequireCapability policy = signature.getMethod().getAnnotation(RequireCapability.class);
        if (policy == null) {
            throw new IllegalStateException("Ownership pagination endpoint lacks RequireCapability");
        }
        authorization.checkCapability(joinPoint, policy);
    }

    private <T> ResponseEntity<Map<String, Object>> response(
            List<T> rows,
            int pageSize,
            UUID tenantId,
            String filterKey,
            Function<T, UUID> id,
            HttpServletRequest request) {
        boolean hasMore = rows.size() > pageSize;
        List<T> data = hasMore
                ? List.copyOf(new ArrayList<>(rows.subList(0, pageSize)))
                : List.copyOf(rows);
        String nextCursor = null;
        if (hasMore && !data.isEmpty()) {
            nextCursor = cursors.encode(
                    tenantId, filterKey, "asc", null, id.apply(data.get(data.size() - 1)));
        }

        UUID requestId = headerUuid(request, "X-Request-ID");
        UUID correlationId = headerUuid(request, "X-Correlation-ID");
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("nextCursor", nextCursor);
        page.put("hasMore", hasMore);
        page.put("limit", pageSize);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", requestId);
        meta.put("correlationId", correlationId);
        meta.put("timestamp", Instant.now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("page", page);
        body.put("meta", meta);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", requestId.toString());
        headers.set("X-Correlation-ID", correlationId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private UUID afterId(HttpServletRequest request, UUID tenantId, String filterKey) {
        String cursor = request.getParameter("cursor");
        if (cursor == null || cursor.isBlank()) return null;
        CursorCodec.DecodedCursor decoded = cursors.decode(cursor, tenantId, filterKey, "asc");
        if (decoded.tieBreakerId() == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Cursor is malformed.");
        }
        return decoded.tieBreakerId();
    }

    private int pageSize(HttpServletRequest request) {
        String raw = request.getParameter("pageSize");
        if (raw == null || raw.isBlank()) return 100;
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > 100) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new CrmContractException(
                    CrmErrorCode.VALIDATION_ERROR,
                    "pageSize must be between 1 and 100.");
        }
    }

    private <E extends Enum<E>> E enumParam(
            HttpServletRequest request,
            String name,
            Class<E> enumType,
            E defaultValue) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new CrmContractException(
                    CrmErrorCode.VALIDATION_ERROR,
                    name + " has an unsupported value.");
        }
    }

    private UUID contextUuid(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException invalid) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
    }

    private UUID headerUuid(HttpServletRequest request, String name) {
        String value = request == null ? null : request.getHeader(name);
        if (value == null || value.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new CrmContractException(
                    CrmErrorCode.VALIDATION_ERROR,
                    name + " must be a UUID.");
        }
    }

    private Invocation invocation(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = null;
        Authentication authentication = null;
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof HttpServletRequest value) request = value;
            if (argument instanceof Authentication value) authentication = value;
        }
        return request == null || authentication == null
                ? null : new Invocation(request, authentication);
    }

    record Invocation(HttpServletRequest request, Authentication authentication) { }
}
