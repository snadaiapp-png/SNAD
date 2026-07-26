package com.sanad.platform.crm.pagination;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.dto.CrmDtos.AccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.ActivityResponse;
import com.sanad.platform.crm.dto.CrmDtos.ContactResponse;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportErrorResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportJobResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadResponse;
import com.sanad.platform.crm.dto.CrmDtos.OpportunityResponse;
import com.sanad.platform.crm.dto.CrmDtos.TimelineEventResponse;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * CRM-003R corrective database keyset pagination.
 *
 * <p>The original CRM v2 controller validated opaque cursors but discarded the
 * decoded position. This aspect replaces only the affected collection methods,
 * re-applies their capability policy, and executes tenant-scoped PostgreSQL
 * keyset queries using the decoded (sort value, id) boundary.</p>
 */
@Aspect
@Component
public class CrmCoreCursorPaginationAspect {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final CursorCodec cursors;
    private final CrmDtoMapper mapper;
    private final ObjectMapper objectMapper;
    private final CapabilityAuthorizationAspect authorization;

    public CrmCoreCursorPaginationAspect(
            NamedParameterJdbcTemplate jdbc,
            CursorCodec cursors,
            CrmDtoMapper mapper,
            ObjectMapper objectMapper,
            CapabilityAuthorizationAspect authorization) {
        this.jdbc = jdbc;
        this.cursors = cursors;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.authorization = authorization;
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listAccounts(..))")
    public Object pageAccounts(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        String search = normalized(invocation.request().getParameter("search"));
        MapSqlParameterSource parameters = base(tenantId, page);
        StringBuilder where = new StringBuilder("source.lifecycle_status <> 'ARCHIVED'");
        if (search != null) {
            where.append(" AND (LOWER(source.display_name) LIKE LOWER(:search) OR LOWER(source.normalized_name) LIKE LOWER(:search))");
            parameters.addValue("search", "%" + search + "%");
        }
        String scope = scope("accounts", page, "search=" + safe(search));
        List<Map<String, Object>> rows = query(
                "crm_accounts source", "source.*", where.toString(), parameters, page, scope,
                accountSort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toAccountResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listContacts(..))")
    public Object pageContacts(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        UUID accountId = uuidParameter(invocation.request(), "accountId");
        String search = normalized(invocation.request().getParameter("search"));
        MapSqlParameterSource parameters = base(tenantId, page);
        StringBuilder where = new StringBuilder("source.lifecycle_status <> 'ARCHIVED'");
        if (accountId != null) {
            where.append(" AND source.account_id=:accountId");
            parameters.addValue("accountId", accountId);
        }
        if (search != null) {
            where.append(" AND (LOWER(source.display_name) LIKE LOWER(:search) OR LOWER(source.normalized_name) LIKE LOWER(:search) OR LOWER(source.normalized_email) LIKE LOWER(:search))");
            parameters.addValue("search", "%" + search + "%");
        }
        String scope = scope("contacts", page,
                "accountId=" + safe(accountId) + ";search=" + safe(search));
        List<Map<String, Object>> rows = query(
                "crm_contacts source", "source.*", where.toString(), parameters, page, scope,
                contactSort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toContactResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listLeads(..))")
    public Object pageLeads(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        String status = upper(invocation.request().getParameter("status"));
        MapSqlParameterSource parameters = base(tenantId, page);
        String where = "TRUE";
        if (status != null) {
            where += " AND source.status=:status";
            parameters.addValue("status", status);
        }
        String scope = scope("leads", page, "status=" + safe(status));
        List<Map<String, Object>> rows = query(
                "crm_leads source", "source.*", where, parameters, page, scope,
                leadSort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toLeadResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listOpportunities(..))")
    public Object pageOpportunities(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        UUID accountId = uuidParameter(invocation.request(), "accountId");
        MapSqlParameterSource parameters = base(tenantId, page);
        String where = "TRUE";
        if (accountId != null) {
            where += " AND source.account_id=:accountId";
            parameters.addValue("accountId", accountId);
        }
        String scope = scope("opportunities", page, "accountId=" + safe(accountId));
        List<Map<String, Object>> rows = query(
                "crm_opportunities source", "source.*", where, parameters, page, scope,
                opportunitySort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toOpportunityResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listActivities(..))")
    public Object pageActivities(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        String relatedType = upper(invocation.request().getParameter("relatedType"));
        UUID relatedId = uuidParameter(invocation.request(), "relatedId");
        String status = upper(invocation.request().getParameter("status"));
        MapSqlParameterSource parameters = base(tenantId, page);
        StringBuilder where = new StringBuilder("TRUE");
        if (relatedType != null) {
            where.append(" AND source.related_type=:relatedType");
            parameters.addValue("relatedType", relatedType);
        }
        if (relatedId != null) {
            where.append(" AND source.related_id=:relatedId");
            parameters.addValue("relatedId", relatedId);
        }
        if (status != null) {
            where.append(" AND source.status=:status");
            parameters.addValue("status", status);
        }
        String scope = scope("activities", page,
                "relatedType=" + safe(relatedType) + ";relatedId=" + safe(relatedId) + ";status=" + safe(status));
        List<Map<String, Object>> rows = query(
                "crm_activities source", "source.*", where.toString(), parameters, page, scope,
                activitySort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toActivityResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.timeline(..))")
    public Object pageTimeline(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        String subjectType = firstArgument(joinPoint, String.class);
        UUID subjectId = firstArgument(joinPoint, UUID.class);
        if (subjectType == null || subjectId == null) return joinPoint.proceed();
        subjectType = subjectType.trim().toUpperCase(Locale.ROOT);
        MapSqlParameterSource parameters = base(tenantId, page)
                .addValue("subjectType", subjectType)
                .addValue("subjectId", subjectId);
        String scope = scope("timeline", page,
                "subjectType=" + subjectType + ";subjectId=" + subjectId);
        List<Map<String, Object>> rows = query(
                "crm_timeline_events source", "source.*",
                "source.subject_type=:subjectType AND source.subject_id=:subjectId",
                parameters, page, scope, timelineSort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toTimelineEventResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listImportJobs(..))")
    public Object pageImportJobs(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        MapSqlParameterSource parameters = base(tenantId, page);
        String scope = scope("imports", page, "all");
        List<Map<String, Object>> rows = query(
                "crm_import_jobs source",
                "source.*, source.original_filename AS file_name, source.succeeded_rows AS successful_rows",
                "TRUE", parameters, page, scope, importJobSort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toImportJobResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listImportErrors(..))")
    public Object pageImportErrors(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        UUID jobId = firstArgument(joinPoint, UUID.class);
        if (jobId == null) return joinPoint.proceed();
        ensureImportJob(tenantId, jobId);
        MapSqlParameterSource parameters = base(tenantId, page).addValue("jobId", jobId);
        String scope = scope("import-errors", page, "jobId=" + jobId);
        List<Map<String, Object>> rows = query(
                "crm_import_errors source",
                "source.*, source.import_job_id AS job_id, source.message AS error_message",
                "source.import_job_id=:jobId", parameters, page, scope, importErrorSort(page.sort()));
        normalizeImportErrors(rows);
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toImportErrorResponse);
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmContractController.listCustomFields(..))")
    public Object pageCustomFields(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = invocation(joinPoint);
        if (invocation == null) return joinPoint.proceed();
        authorize(joinPoint);
        PageRequest page = page(invocation.request());
        UUID tenantId = tenantId(invocation.authentication());
        String entityType = upper(invocation.request().getParameter("entityType"));
        MapSqlParameterSource parameters = base(tenantId, page);
        String where = "TRUE";
        if (entityType != null) {
            where += " AND source.entity_type=:entityType";
            parameters.addValue("entityType", entityType);
        }
        String scope = scope("custom-fields", page, "entityType=" + safe(entityType));
        List<Map<String, Object>> rows = query(
                "crm_custom_field_definitions source", "source.*", where, parameters, page, scope,
                customFieldSort(page.sort()));
        return response(rows, page, tenantId, scope, invocation.request(), mapper::toCustomFieldResponse);
    }

    private List<Map<String, Object>> query(
            String from,
            String select,
            String where,
            MapSqlParameterSource parameters,
            PageRequest page,
            String cursorScope,
            SortColumn sortColumn) {
        parameters.addValue("sortNull", sortColumn.nullSentinel());
        String expression = "COALESCE(" + sortColumn.expression() + ", :sortNull)";
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(select).append(", ").append(expression).append(" AS __cursor_sort FROM ")
                .append(from).append(" WHERE source.tenant_id=:tenantId AND (").append(where).append(")");

        if (page.hasCursor()) {
            CursorCodec.DecodedCursor decoded = cursors.decode(
                    page.cursor(), parameters.getValue("tenantId") instanceof UUID value ? value : null,
                    cursorScope, page.direction());
            if (decoded.sortValue() == null || decoded.tieBreakerId() == null) {
                throw validation("Cursor is missing its keyset boundary.");
            }
            String operator = "asc".equals(page.direction()) ? ">" : "<";
            sql.append(" AND (").append(expression).append(' ').append(operator).append(" :cursorValue")
                    .append(" OR (").append(expression).append(" = :cursorValue AND source.id ")
                    .append(operator).append(" :cursorId))");
            parameters.addValue("cursorValue", sortColumn.parse(decoded.sortValue()));
            parameters.addValue("cursorId", decoded.tieBreakerId());
        }

        String direction = "asc".equals(page.direction()) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(expression).append(' ').append(direction)
                .append(", source.id ").append(direction).append(" LIMIT :limitPlusOne");
        return jdbc.queryForList(sql.toString(), parameters);
    }

    private <T> ListResponse<T> response(
            List<Map<String, Object>> rows,
            PageRequest page,
            UUID tenantId,
            String cursorScope,
            HttpServletRequest request,
            Function<Map<String, Object>, T> mapping) {
        boolean hasMore = rows.size() > page.limit();
        List<Map<String, Object>> pageRows = hasMore
                ? new ArrayList<>(rows.subList(0, page.limit()))
                : new ArrayList<>(rows);
        List<T> data = pageRows.stream().map(mapping).toList();
        CrmEnvelopes.Page pageInfo = CrmEnvelopes.Page.empty(page.limit());
        if (hasMore && !pageRows.isEmpty()) {
            Map<String, Object> last = pageRows.get(pageRows.size() - 1);
            UUID id = uuid(last.get("id"));
            Object rawSort = last.get("__cursor_sort");
            if (id == null || rawSort == null) {
                throw new IllegalStateException("CRM keyset query did not return a complete cursor boundary");
            }
            String next = cursors.encode(
                    tenantId, cursorScope, page.direction(), serialize(rawSort), id);
            pageInfo = CrmEnvelopes.Page.of(next, true, page.limit());
        }
        return ListResponse.of(data, pageInfo, requestId(request));
    }

    private void authorize(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            throw new IllegalStateException("CRM pagination requires a method signature");
        }
        RequireCapability policy = signature.getMethod().getAnnotation(RequireCapability.class);
        if (policy == null) {
            throw new IllegalStateException("CRM paginated endpoint lacks RequireCapability");
        }
        authorization.checkCapability(joinPoint, policy);
    }

    private Invocation invocation(ProceedingJoinPoint joinPoint) {
        Authentication authentication = null;
        HttpServletRequest request = null;
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof Authentication value) authentication = value;
            if (argument instanceof HttpServletRequest value) request = value;
        }
        return authentication == null || request == null ? null : new Invocation(authentication, request);
    }

    private PageRequest page(HttpServletRequest request) {
        Integer limit = null;
        String rawLimit = request.getParameter("limit");
        if (rawLimit != null && !rawLimit.isBlank()) {
            try {
                limit = Integer.valueOf(rawLimit);
            } catch (NumberFormatException invalid) {
                throw validation("limit must be an integer.");
            }
        }
        return new PageRequest(
                limit,
                request.getParameter("cursor"),
                request.getParameter("sort"),
                request.getParameter("direction"));
    }

    private MapSqlParameterSource base(UUID tenantId, PageRequest page) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limitPlusOne", page.limit() + 1);
    }

    private void ensureImportJob(UUID tenantId, UUID jobId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_import_jobs WHERE tenant_id=:tenantId AND id=:jobId",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("jobId", jobId),
                Long.class);
        if (count == null || count != 1L) {
            throw new CrmContractException(CrmErrorCode.CRM_IMPORT_NOT_FOUND);
        }
    }

    private void normalizeImportErrors(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object raw = row.get("raw_row");
            if (raw == null) {
                row.put("row_data", Map.of());
                continue;
            }
            try {
                row.put("row_data", objectMapper.readValue(String.valueOf(raw), MAP_TYPE));
            } catch (Exception invalidJson) {
                row.put("row_data", Map.of("raw", String.valueOf(raw)));
            }
        }
    }

    private SortColumn accountSort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName", "name" -> SortColumn.text("source.display_name");
            case "status" -> SortColumn.text("source.lifecycle_status");
            default -> throw unsupported(sort, "accounts");
        };
    }

    private SortColumn contactSort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName", "name" -> SortColumn.text("source.display_name");
            case "status" -> SortColumn.text("source.lifecycle_status");
            default -> throw unsupported(sort, "contacts");
        };
    }

    private SortColumn leadSort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName", "name" -> SortColumn.text("source.display_name");
            case "status" -> SortColumn.text("source.status");
            default -> throw unsupported(sort, "leads");
        };
    }

    private SortColumn opportunitySort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName", "name" -> SortColumn.text("source.name");
            case "status" -> SortColumn.text("source.status");
            case "amount" -> SortColumn.decimal("source.amount");
            default -> throw unsupported(sort, "opportunities");
        };
    }

    private SortColumn activitySort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName", "name" -> SortColumn.text("source.subject");
            case "status" -> SortColumn.text("source.status");
            case "priority" -> SortColumn.integer("source.priority");
            default -> throw unsupported(sort, "activities");
        };
    }

    private SortColumn timelineSort(String sort) {
        return switch (sort) {
            case "updatedAt", "createdAt" -> SortColumn.timestamp("source.occurred_at");
            case "displayName", "name" -> SortColumn.text("source.summary");
            default -> throw unsupported(sort, "timeline");
        };
    }

    private SortColumn importJobSort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName", "name" -> SortColumn.text("source.original_filename");
            case "status" -> SortColumn.text("source.status");
            default -> throw unsupported(sort, "imports");
        };
    }

    private SortColumn importErrorSort(String sort) {
        return switch (sort) {
            case "updatedAt", "createdAt" -> SortColumn.timestamp("source.created_at");
            case "status" -> SortColumn.text("source.error_code");
            case "priority" -> SortColumn.longNumber("source.row_number");
            default -> throw unsupported(sort, "import errors");
        };
    }

    private SortColumn customFieldSort(String sort) {
        return switch (sort) {
            case "updatedAt" -> SortColumn.timestamp("source.updated_at");
            case "createdAt" -> SortColumn.timestamp("source.created_at");
            case "displayName" -> SortColumn.text("source.label_en");
            case "name" -> SortColumn.text("source.field_key");
            case "status" -> SortColumn.text("source.active");
            default -> throw unsupported(sort, "custom fields");
        };
    }

    private CrmContractException unsupported(String sort, String endpoint) {
        return validation("Sort field '" + sort + "' is not supported for " + endpoint + ".");
    }

    private String scope(String endpoint, PageRequest page, String filters) {
        return "crm003:" + endpoint + ":" + page.sort() + ":" + filters;
    }

    private UUID tenantId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get("tenant_id") == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(details.get("tenant_id").toString());
        } catch (IllegalArgumentException invalid) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
    }

    private UUID requestId(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("X-Request-ID");
        if (value == null || value.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw validation("X-Request-ID must be a UUID.");
        }
    }

    private UUID uuidParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw validation(name + " must be a UUID.");
        }
    }

    private <T> T firstArgument(ProceedingJoinPoint joinPoint, Class<T> type) {
        for (Object argument : joinPoint.getArgs()) {
            if (type.isInstance(argument)) return type.cast(argument);
        }
        return null;
    }

    private UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID result) return result;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private String serialize(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant().toString();
        if (value instanceof Instant instant) return instant.toString();
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return String.valueOf(value);
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String normalized = normalized(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private CrmContractException validation(String message) {
        return new CrmContractException(CrmErrorCode.VALIDATION_ERROR, message);
    }

    private record Invocation(Authentication authentication, HttpServletRequest request) { }

    private record SortColumn(String expression, CursorType type, Object nullSentinel) {
        static SortColumn timestamp(String expression) {
            return new SortColumn(expression, CursorType.TIMESTAMP, Timestamp.from(Instant.EPOCH));
        }

        static SortColumn text(String expression) {
            return new SortColumn(expression, CursorType.TEXT, "");
        }

        static SortColumn decimal(String expression) {
            return new SortColumn(expression, CursorType.DECIMAL, BigDecimal.ZERO);
        }

        static SortColumn integer(String expression) {
            return new SortColumn(expression, CursorType.INTEGER, 0);
        }

        static SortColumn longNumber(String expression) {
            return new SortColumn(expression, CursorType.LONG, 0L);
        }

        Object parse(String value) {
            try {
                return switch (type) {
                    case TIMESTAMP -> Timestamp.from(OffsetDateTime.parse(value).toInstant());
                    case TEXT -> value;
                    case DECIMAL -> new BigDecimal(value);
                    case INTEGER -> Integer.valueOf(value);
                    case LONG -> Long.valueOf(value);
                };
            } catch (RuntimeException invalid) {
                throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                        "Cursor contains an invalid sort value.");
            }
        }
    }

    private enum CursorType { TIMESTAMP, TEXT, DECIMAL, INTEGER, LONG }
}
