package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditChainHead;
import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.repository.AuditChainHeadRepository;
import com.sanad.platform.audit.repository.AuditEventRepository;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 05A.1 — Central service for recording audit events.
 *
 * <p>Key changes from Stage 05:</p>
 * <ul>
 *   <li>Fail-closed: throws {@link AuditContextMissingException} if a
 *       tenant-scoped action has no verified tenant context. Never
 *       returns null or logs a warning and continues.</li>
 *   <li>Actor trust boundary: when a verified TenantContext exists,
 *       actor identity is taken from it ONLY. Caller-supplied
 *       {@code ctx.actorUserId()} is ignored.</li>
 *   <li>Linear hash chain: uses {@code audit_chain_heads} with
 *       {@code SELECT FOR UPDATE} to serialize chain extension.</li>
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditRedactionService redactionService;
    private final AuditHashChainService hashChainService;
    private final TenantContextProvider contextProvider;
    private final Environment environment;

    public AuditService(AuditEventRepository repository,
                         AuditChainHeadRepository chainHeadRepository,
                         AuditRedactionService redactionService,
                         AuditHashChainService hashChainService,
                         TenantContextProvider contextProvider,
                         Environment environment) {
        this.repository = repository;
        this.chainHeadRepository = chainHeadRepository;
        this.redactionService = redactionService;
        this.hashChainService = hashChainService;
        this.contextProvider = contextProvider;
        this.environment = environment;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(AuditContext ctx) {
        return doRecord(ctx, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordDenied(AuditContext ctx) {
        return doRecord(ctx, true);
    }

    private AuditEvent doRecord(AuditContext ctx, boolean denied) {
        TenantContext tenantContext = contextProvider.currentContext().orElse(null);
        UUID tenantId = tenantContext != null ? tenantContext.tenantId() : ctx.tenantId();

        // Stage 05A.1 §12 — Fail-closed: no silent skip.
        if (tenantId == null) {
            throw new AuditContextMissingException(
                    "Cannot record audit event: no verified tenant context for action=" + ctx.action());
        }

        Instant now = Instant.now();
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        // Stage 05A.2.4 §3 — Chain-head initialization.
        // On PostgreSQL (prod/tenant-postgres-test), use atomic INSERT ON CONFLICT.
        // On H2 (local), use find-or-create (no ON CONFLICT — it poisons the tx).
        boolean isPostgres = environment.matchesProfiles("prod", "tenant-postgres-test");
        if (!chainHeadRepository.findByTenantId(tenantId).isPresent()) {
            if (isPostgres) {
                chainHeadRepository.atomicInit(tenantId);
            } else {
                chainHeadRepository.save(new com.sanad.platform.audit.domain.AuditChainHead(tenantId));
            }
        }
        // Now the row exists — lock it for update.
        AuditChainHead chainHead = chainHeadRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "audit_chain_heads row not found for tenant " + tenantId
                        + " after init — this should never happen"));

        long nextSequence = (chainHead.getHeadSequence() == null ? 0 : chainHead.getHeadSequence()) + 1;
        String previousHash = chainHead.getHeadHash() != null ? chainHead.getHeadHash() : hashChainService.getGenesisHash();

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
        event.setSequenceNumber(nextSequence);
        event.setPreviousHash(previousHash);

        // Stage 05A.1 §13 — Actor attribution trust boundary.
        // When verified TenantContext exists, take identity from it ONLY.
        if (tenantContext != null) {
            event.setActorUserId(tenantContext.userId());
            event.setSessionId(tenantContext.sessionId());
            // Ignore ctx.actorUserId() — verified context wins.
        }
        // actorService and actorDisplayName are service-level metadata,
        // safe to take from ctx (they describe the calling service, not the user).
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

        AuditEvent saved = repository.save(event);

        // Update chain head
        chainHead.setHeadSequence(nextSequence);
        chainHead.setHeadHash(eventHash);
        chainHeadRepository.save(chainHead);

        return saved;
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
