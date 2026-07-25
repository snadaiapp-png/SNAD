package com.sanad.platform.crm.web;

import com.sanad.platform.crm.concurrency.ETagService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces CRM-008 optimistic concurrency atomically.
 *
 * <p>The controllers still perform their normal pre-read ETag validation for a
 * fast rejection and contract clarity. This aspect is the concurrency
 * authority: it locks the target PostgreSQL row, validates the same If-Match
 * value while the lock is held, and keeps the transaction open through the
 * controller and application-service mutation. A second request carrying the
 * same ETag therefore observes the committed new timestamp after it acquires
 * the lock and fails with HTTP 412 before executing its mutation.</p>
 */
@Aspect
@Component
public class CrmOwnershipAtomicIfMatchAspect {

    private static final Pattern TEAM_MEMBERSHIP = Pattern.compile(
            "^/api/v2/crm/teams/[0-9a-fA-F-]{36}/memberships/([0-9a-fA-F-]{36})$");
    private static final Pattern TEAM = Pattern.compile(
            "^/api/v2/crm/teams/([0-9a-fA-F-]{36})$");
    private static final Pattern QUEUE = Pattern.compile(
            "^/api/v2/crm/queues/([0-9a-fA-F-]{36})$");
    private static final Pattern TERRITORY_ASSIGNMENT = Pattern.compile(
            "^/api/v2/crm/territories/[0-9a-fA-F-]{36}/assignments/([0-9a-fA-F-]{36})$");
    private static final Pattern TERRITORY = Pattern.compile(
            "^/api/v2/crm/territories/([0-9a-fA-F-]{36})$");
    private static final Pattern RULE_ACTIVATION = Pattern.compile(
            "^/api/v2/crm/assignment-rules/([0-9a-fA-F-]{36})/versions/[0-9]+/activate$");
    private static final Pattern TRANSFER = Pattern.compile(
            "^/api/v2/crm/transfers/([0-9a-fA-F-]{36})/(submit|approve|cancel)$");

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ETagService etags;

    public CrmOwnershipAtomicIfMatchAspect(
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transactions,
            ETagService etags) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.etags = etags;
    }

    @Around("execution(* com.sanad.platform.crm.web.CrmOwnershipResourceController.*(..))"
            + " || execution(* com.sanad.platform.crm.web.CrmOwnershipAssignmentController.*(..))"
            + " || execution(* com.sanad.platform.crm.web.CrmOwnershipTransferController.*(..))")
    public Object enforceAtomicIfMatch(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = request();
        LockTarget target = resolveTarget(request);
        if (target == null) {
            return joinPoint.proceed();
        }
        UUID tenantId = authenticatedTenantId();
        if (tenantId == null) {
            // Authentication/capability enforcement remains authoritative.
            return joinPoint.proceed();
        }

        try {
            return transactions.execute(status -> {
                Instant updatedAt = lockUpdatedAt(target, tenantId);
                if (updatedAt == null) {
                    // Preserve the controller/use-case not-found contract.
                    return proceed(joinPoint);
                }
                etags.validateIfMatch(
                        request.getHeader("If-Match"),
                        target.entityType(),
                        target.id(),
                        timestampVersion(updatedAt));
                return proceed(joinPoint);
            });
        } catch (ProceedingFailure failure) {
            throw failure.getCause();
        }
    }

    private Instant lockUpdatedAt(LockTarget target, UUID tenantId) {
        String sql = "SELECT updated_at FROM " + target.table()
                + " WHERE tenant_id=:tenantId AND id=:id FOR UPDATE";
        try {
            Timestamp value = jdbc.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("id", target.id()),
                    Timestamp.class);
            return value == null ? null : value.toInstant();
        } catch (EmptyResultDataAccessException missing) {
            return null;
        }
    }

    static long timestampVersion(Instant value) {
        if (value == null) return 0L;
        Instant micros = value.truncatedTo(ChronoUnit.MICROS);
        return Math.addExact(
                Math.multiplyExact(micros.getEpochSecond(), 1_000_000L),
                micros.getNano() / 1_000L);
    }

    static LockTarget resolveTarget(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null || request.getMethod() == null) {
            return null;
        }
        String method = request.getMethod();
        if (!"PATCH".equals(method) && !"DELETE".equals(method) && !"POST".equals(method)) {
            return null;
        }
        String path = request.getRequestURI();

        LockTarget target = match(TEAM_MEMBERSHIP, path, "crm_team_memberships", "team-membership");
        if (target != null) return target;
        target = match(TEAM, path, "crm_sales_teams", "sales-team");
        if (target != null && "PATCH".equals(method)) return target;
        target = match(QUEUE, path, "crm_queues", "queue");
        if (target != null && "PATCH".equals(method)) return target;
        target = match(TERRITORY_ASSIGNMENT, path, "crm_territory_assignments", "territory-assignment");
        if (target != null && "DELETE".equals(method)) return target;
        target = match(TERRITORY, path, "crm_territories", "territory");
        if (target != null && "PATCH".equals(method)) return target;
        target = match(RULE_ACTIVATION, path, "crm_assignment_rules", "assignment-rule");
        if (target != null && "PATCH".equals(method)) return target;
        target = match(TRANSFER, path, "crm_transfer_requests", "transfer-request");
        if (target != null && "POST".equals(method)) return target;
        return null;
    }

    private static LockTarget match(Pattern pattern, String path, String table, String entityType) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) return null;
        try {
            return new LockTarget(table, entityType, UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private UUID authenticatedTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get("tenant_id") == null) {
            return null;
        }
        try {
            return UUID.fromString(details.get("tenant_id").toString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private HttpServletRequest request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable checked) {
            throw new ProceedingFailure(checked);
        }
    }

    record LockTarget(String table, String entityType, UUID id) { }

    private static final class ProceedingFailure extends RuntimeException {
        private ProceedingFailure(Throwable cause) {
            super(cause);
        }
    }
}
