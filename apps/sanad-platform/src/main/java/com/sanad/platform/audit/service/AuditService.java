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
import java.util.UUID;

/**
 * Stage 05A.1 — Central service for recording audit events.
 *
 * <p>Tenant chain extension is serialized by a database row lock. PostgreSQL
 * writers always perform atomic initialization first and then load the chain
 * head under {@code SELECT FOR UPDATE}; no unlocked pre-read is allowed into
 * the persistence context.</p>
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
        return doRecord(ctx);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordDenied(AuditContext ctx) {
        return doRecord(ctx);
    }

    private AuditEvent doRecord(AuditContext ctx) {
        TenantContext tenantContext = contextProvider.currentContext().orElse(null);
        UUID tenantId = tenantContext != null ? tenantContext.tenantId() : ctx.tenantId();

        if (tenantId == null) {
            throw new AuditContextMissingException(
                    "Cannot record audit event: no verified tenant context for action=" + ctx.action());
        }

        Instant now = hashChainService.normalizeToDatabasePrecision(Instant.now());
        Instant occurredAt = hashChainService.normalizeToDatabasePrecision(
                ctx.occurredAt() != null ? ctx.occurredAt() : now);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        boolean isPostgres = environment.matchesProfiles("prod", "tenant-postgres-test");
        if (isPostgres) {
            // Never perform an unlocked findByTenantId() first. Such a read can
            // place stale state in Hibernate's persistence context before the
            // pessimistic lock is acquired, allowing duplicate sequences.
            chainHeadRepository.atomicInit(tenantId);
        } else if (chainHeadRepository.findByTenantId(tenantId).isEmpty()) {
            // H2 does not support the PostgreSQL ON CONFLICT statement.
            chainHeadRepository.saveAndFlush(new AuditChainHead(tenantId));
        }

        AuditChainHead chainHead = chainHeadRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "audit_chain_heads row not found for tenant " + tenantId
                                + " after initialization"));

        long nextSequence = (chainHead.getHeadSequence() == null ? 0L : chainHead.getHeadSequence()) + 1L;
        String previousHash = chainHead.getHeadHash() != null
                ? chainHead.getHeadHash()
                : hashChainService.getGenesisHash();

        AuditEvent event = new AuditEvent(
                tenantId,
                ctx.actorType() != null ? ctx.actorType() : AuditActorType.USER,
                ctx.action(),
                ctx.resourceType(),
                ctx.operation(),
                ctx.outcome() != null ? ctx.outcome() : AuditOutcome.SUCCESS,
                occurredAt,
                now,
                ""
        );
        event.setSequenceNumber(nextSequence);
        event.setPreviousHash(previousHash);

        if (tenantContext != null) {
            event.setActorUserId(tenantContext.userId());
            event.setSessionId(tenantContext.sessionId());
        }
        event.setActorService(ctx.actorService());
        event.setActorDisplayName(ctx.actorDisplayName());

        event.setRequestId(requestId);
        event.setJwtId(tenantContext != null ? tenantContext.sessionId() : null);
        event.setCorrelationId(ctx.correlationId());
        event.setTraceId(ctx.traceId());

        event.setCategory(ctx.category());
        event.setResourceId(ctx.resourceId());

        event.setHttpStatus(ctx.httpStatus());
        event.setErrorCode(ctx.errorCode());
        event.setFailureReason(ctx.failureReason());

        HttpServletRequest servletRequest = currentServletRequest();
        if (servletRequest != null) {
            event.setSourceIp(extractClientIp(servletRequest));
            event.setUserAgent(servletRequest.getHeader("User-Agent"));
            event.setChannel(determineChannel(servletRequest));
        }
        if (ctx.sourceIp() != null) event.setSourceIp(ctx.sourceIp());
        if (ctx.userAgent() != null) event.setUserAgent(ctx.userAgent());
        if (ctx.channel() != null) event.setChannel(ctx.channel());

        event.setBeforeState(redactionService.redactJson(ctx.beforeState()));
        event.setAfterState(redactionService.redactJson(ctx.afterState()));
        event.setChangedFields(redactionService.redactJson(ctx.changedFields()));
        event.setMetadata(redactionService.redactJson(ctx.metadata()));

        String eventHash = hashChainService.computeEventHash(event, previousHash);
        event.setEventHash(eventHash);

        // Flush while the chain-head lock is still held. A uniqueness failure
        // therefore rolls back the event and head mutation atomically rather
        // than surfacing only during transaction completion.
        AuditEvent saved = repository.saveAndFlush(event);

        chainHead.setHeadSequence(nextSequence);
        chainHead.setHeadHash(eventHash);
        chainHeadRepository.saveAndFlush(chainHead);

        log.debug("Audit event appended tenant={} sequence={} eventId={}",
                tenantId, nextSequence, saved.getId());
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
