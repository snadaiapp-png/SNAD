package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.repository.AuditEventRepository;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05 §8-9 — Central service for recording audit events.
 *
 * <p>Every auditable action calls {@link #record(AuditContext)} to
 * persist exactly one {@link AuditEvent}. The service:</p>
 * <ol>
 *   <li>Extracts actor, session, and request identity from verified
 *       sources (TenantContext, Authentication, MDC, HTTP request) —
 *       NEVER from client-supplied request body fields.</li>
 *   <li>Redacts sensitive fields from before/after/metadata JSON.</li>
 *   <li>Computes the event hash (linked to the previous event in the
 *       same tenant's chain).</li>
 *   <li>Persists the event via INSERT (UPDATE/DELETE blocked by DB
 *       triggers).</li>
 * </ol>
 *
 * <h2>Transaction boundary</h2>
 * <p>For successful business mutations, {@link #record(AuditContext)}
 * runs in {@code Propagation.REQUIRED} — it joins the caller's
 * transaction. If the business mutation commits, the audit event
 * commits. If the business mutation rolls back, the audit event
 * rolls back too (no false success audit).</p>
 *
 * <p>For denied requests (authentication/authorization failures),
 * there is no business transaction to join. The caller should use
 * {@link #recordDenied(AuditContext)} which runs in
 * {@code Propagation.REQUIRES_NEW} to ensure the denial audit is
 * persisted even if the caller's transaction is marked for rollback.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final AuditRedactionService redactionService;
    private final AuditHashChainService hashChainService;
    private final TenantContextProvider contextProvider;

    public AuditService(AuditEventRepository repository,
                         AuditRedactionService redactionService,
                         AuditHashChainService hashChainService,
                         TenantContextProvider contextProvider) {
        this.repository = repository;
        this.redactionService = redactionService;
        this.hashChainService = hashChainService;
        this.contextProvider = contextProvider;
    }

    /**
     * Records an audit event in the caller's transaction.
     * Use for successful business mutations.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(AuditContext ctx) {
        return doRecord(ctx, false);
    }

    /**
     * Records an audit event in a new independent transaction.
     * Use for denied requests where the caller's transaction may
     * roll back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordDenied(AuditContext ctx) {
        return doRecord(ctx, true);
    }

    private AuditEvent doRecord(AuditContext ctx, boolean denied) {
        TenantContext tenantContext = contextProvider.currentContext().orElse(null);
        UUID tenantId = tenantContext != null ? tenantContext.tenantId() : ctx.tenantId();
        if (tenantId == null) {
            log.warn("Audit record skipped: no tenant context and no explicit tenantId. action={}", ctx.action());
            return null;
        }

        Instant now = Instant.now();
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        String previousHash = repository.findLatestByTenantId(tenantId)
                .map(AuditEvent::getEventHash)
                .orElse(hashChainService.getGenesisHash());

        AuditEvent event = new AuditEvent(
                tenantId,
                ctx.actorType() != null ? ctx.actorType() : AuditActorType.USER,
                ctx.action(),
                ctx.resourceType(),
                ctx.operation(),
                ctx.outcome() != null ? ctx.outcome() : AuditOutcome.SUCCESS,
                ctx.occurredAt() != null ? ctx.occurredAt() : now,
                now,
                "" // placeholder — set below
        );
        event.setPreviousHash(previousHash);

        // Actor attribution
        if (tenantContext != null) {
            event.setActorUserId(tenantContext.userId());
            event.setSessionId(tenantContext.sessionId());
        }
        if (ctx.actorUserId() != null) {
            event.setActorUserId(ctx.actorUserId());
        }
        event.setActorService(ctx.actorService());
        event.setActorDisplayName(ctx.actorDisplayName());

        // Request/correlation identity (from verified sources only)
        event.setRequestId(requestId);
        event.setJwtId(tenantContext != null ? tenantContext.sessionId() : null);
        event.setCorrelationId(ctx.correlationId());
        event.setTraceId(ctx.traceId());

        // Action/resource detail
        event.setCategory(ctx.category());
        event.setResourceId(ctx.resourceId());

        // Outcome
        event.setHttpStatus(ctx.httpStatus());
        event.setErrorCode(ctx.errorCode());
        event.setFailureReason(ctx.failureReason());

        // Source
        HttpServletRequest servletRequest = currentServletRequest();
        if (servletRequest != null) {
            event.setSourceIp(extractClientIp(servletRequest));
            event.setUserAgent(servletRequest.getHeader("User-Agent"));
            event.setChannel(determineChannel(servletRequest));
        }
        if (ctx.sourceIp() != null) event.setSourceIp(ctx.sourceIp());
        if (ctx.userAgent() != null) event.setUserAgent(ctx.userAgent());
        if (ctx.channel() != null) event.setChannel(ctx.channel());

        // State change (redacted)
        event.setBeforeState(redactionService.redactJson(ctx.beforeState()));
        event.setAfterState(redactionService.redactJson(ctx.afterState()));
        event.setChangedFields(redactionService.redactJson(ctx.changedFields()));
        event.setMetadata(redactionService.redactJson(ctx.metadata()));

        // Hash chain
        String eventHash = hashChainService.computeEventHash(event, previousHash);
        event.setEventHash(eventHash);

        return repository.save(event);
    }

    private HttpServletRequest currentServletRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String determineChannel(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "API";
        if (ua.contains("Mozilla")) return "WEB";
        return "API";
    }
}
