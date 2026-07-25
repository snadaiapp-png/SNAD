package com.sanad.platform.crm.web;

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
import com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport;
import com.sanad.platform.crm.pagination.CursorCodec;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Database-backed cursor pagination for the CRM-008 collection endpoints that
 * previously materialized an unbounded tenant result and truncated it in Java.
 *
 * <p>The cursor is opaque, tenant-bound and filter-bound. Queries use a stable
 * UUID keyset and {@code LIMIT pageSize + 1}; therefore response size and JDBC
 * materialization are bounded independently of tenant cardinality.</p>
 */
@Aspect
@Component
public class CrmOwnershipCursorPaginationAspect {

    private final NamedParameterJdbcTemplate jdbc;
    private final CursorCodec cursors;
    private final CrmOwnershipHttpSupport http;

    public CrmOwnershipCursorPaginationAspect(
            NamedParameterJdbcTemplate jdbc,
            CursorCodec cursors,
            CrmOwnershipHttpSupport http) {
        this.jdbc = jdbc;
        this.cursors = cursors;
        this.http = http;
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.listTeams(..))")
    public Object pageTeams(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = requestArg(joinPoint);
        Authentication authentication = authenticationArg(joinPoint);
        if (request == null || authentication == null) return joinPoint.proceed();
        UUID tenantId = http.context(authentication).tenantId();
        TeamStatus status = enumParam(request, "status", TeamStatus.class, TeamStatus.ACTIVE);
        int pageSize = pageSize(request, 100);
        String filterKey = "crm008:teams:" + status.name() + ":id";
        UUID afterId = afterId(request, tenantId, filterKey);

        List<SalesTeam> rows = queryPage(
                "SELECT * FROM crm_sales_teams "
                        + "WHERE tenant_id=:tenantId AND status=:status "
                        + "AND (:afterId IS NULL OR id > :afterId) "
                        + "ORDER BY id ASC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("status", status.name())
                        .addValue("afterId", afterId)
                        .addValue("limit", pageSize + 1),
                OwnershipJdbcSupport.salesTeamMapper());
        return response(rows, pageSize, tenantId, filterKey, SalesTeam::id, request);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.listQueues(..))")
    public Object pageQueues(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = requestArg(joinPoint);
        Authentication authentication = authenticationArg(joinPoint);
        if (request == null || authentication == null) return joinPoint.proceed();
        UUID tenantId = http.context(authentication).tenantId();
        QueueStatus status = enumParam(request, "status", QueueStatus.class, null);
        int pageSize = pageSize(request, 100);
        String filterKey = "crm008:queues:" + (status == null ? "ALL" : status.name()) + ":id";
        UUID afterId = afterId(request, tenantId, filterKey);

        List<Queue> rows = queryPage(
                "SELECT * FROM crm_queues "
                        + "WHERE tenant_id=:tenantId "
                        + "AND (:status IS NULL OR status=:status) "
                        + "AND (:afterId IS NULL OR id > :afterId) "
                        + "ORDER BY id ASC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("status", status == null ? null : status.name())
                        .addValue("afterId", afterId)
                        .addValue("limit", pageSize + 1),
                OwnershipJdbcSupport.queueMapper());
        return response(rows, pageSize, tenantId, filterKey, Queue::id, request);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipTransferController.listTransfers(..))")
    public Object pageTransfers(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = requestArg(joinPoint);
        Authentication authentication = authenticationArg(joinPoint);
        if (request == null || authentication == null) return joinPoint.proceed();
        var context = http.context(authentication);
        TransferQueryUseCases.Direction direction = enumParam(
                request, "direction", TransferQueryUseCases.Direction.class,
                TransferQueryUseCases.Direction.ALL);
        TransferState state = enumParam(request, "state", TransferState.class, null);
        int pageSize = pageSize(request, 100);
        String filterKey = "crm008:transfers:" + direction.name() + ":"
                + (state == null ? "ALL" : state.name()) + ":id";
        UUID afterId = afterId(request, context.tenantId(), filterKey);

        List<TransferRequest> rows = queryPage(
                "SELECT * FROM crm_transfer_requests "
                        + "WHERE tenant_id=:tenantId "
                        + "AND ((:direction='OUTGOING' AND requester_user_id=:userId) "
                        + " OR (:direction='INCOMING' AND proposed_owner_user_id=:userId) "
                        + " OR (:direction='ALL' AND (requester_user_id=:userId OR proposed_owner_user_id=:userId))) "
                        + "AND (:state IS NULL OR state=:state) "
                        + "AND (:afterId IS NULL OR id > :afterId) "
                        + "ORDER BY id ASC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("tenantId", context.tenantId())
                        .addValue("userId", context.userId())
                        .addValue("direction", direction.name())
                        .addValue("state", state == null ? null : state.name())
                        .addValue("afterId", afterId)
                        .addValue("limit", pageSize + 1),
                OwnershipJdbcSupport.transferRequestMapper());
        return response(rows, pageSize, context.tenantId(), filterKey, TransferRequest::id, request);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipAssignmentController.listRules(..))")
    public Object pageRules(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = requestArg(joinPoint);
        Authentication authentication = authenticationArg(joinPoint);
        if (request == null || authentication == null) return joinPoint.proceed();
        UUID tenantId = http.context(authentication).tenantId();
        RuleStatus status = enumParam(request, "status", RuleStatus.class, null);
        int pageSize = pageSize(request, 100);
        String filterKey = "crm008:assignment-rules:"
                + (status == null ? "ALL" : status.name()) + ":id";
        UUID afterId = afterId(request, tenantId, filterKey);

        List<AssignmentRule> rows = queryPage(
                "SELECT * FROM crm_assignment_rules "
                        + "WHERE tenant_id=:tenantId "
                        + "AND (:status IS NULL OR status=:status) "
                        + "AND (:afterId IS NULL OR id > :afterId) "
                        + "ORDER BY id ASC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("status", status == null ? null : status.name())
                        .addValue("afterId", afterId)
                        .addValue("limit", pageSize + 1),
                OwnershipJdbcSupport.assignmentRuleMapper());
        return response(rows, pageSize, tenantId, filterKey, AssignmentRule::id, request);
    }

    private <T> Object response(
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
        return http.list(data, nextCursor, hasMore, pageSize, http.trace(request));
    }

    private <T> List<T> queryPage(
            String sql,
            MapSqlParameterSource parameters,
            RowMapper<T> mapper) {
        return jdbc.query(sql, parameters, mapper);
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

    private int pageSize(HttpServletRequest request, int defaultValue) {
        String raw = request.getParameter("pageSize");
        if (raw == null || raw.isBlank()) return defaultValue;
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

    private HttpServletRequest requestArg(ProceedingJoinPoint joinPoint) {
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof HttpServletRequest request) return request;
        }
        return null;
    }

    private Authentication authenticationArg(ProceedingJoinPoint joinPoint) {
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof Authentication authentication) return authentication;
        }
        return null;
    }
}
