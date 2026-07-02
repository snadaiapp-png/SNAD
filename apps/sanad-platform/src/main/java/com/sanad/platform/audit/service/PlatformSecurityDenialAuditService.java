package com.sanad.platform.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05A.2.8 §6 — Platform-level security denial audit.
 *
 * <p>Records pre-authentication security failures (missing JWT,
 * malformed JWT, expired JWT, etc.) to
 * {@code platform_security_audit_events}.</p>
 *
 * <p>Uses REQUIRES_NEW so the audit survives any business
 * transaction rollback. Never stores raw JWT or Authorization
 * headers — only a redacted token fingerprint.</p>
 */
@Component
public class PlatformSecurityDenialAuditService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSecurityDenialAuditService.class);

    private final JdbcTemplate jdbc;

    public PlatformSecurityDenialAuditService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(
            String failureCategory,
            String errorCode,
            String requestId,
            String path,
            String httpMethod,
            String sourceIp,
            String userAgent,
            String tokenFingerprint,
            String metadata) {

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update(
                "INSERT INTO platform_security_audit_events " +
                "(id, occurred_at, recorded_at, request_id, path, http_method, " +
                " source_ip, user_agent, failure_category, error_code, " +
                " token_fingerprint, metadata, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                requestId,
                path,
                httpMethod,
                sourceIp,
                userAgent,
                failureCategory,
                errorCode,
                tokenFingerprint,
                metadata,
                java.sql.Timestamp.from(now));

        log.debug("Platform security denial recorded: category={} path={} requestId={}",
                failureCategory, path, requestId);
    }
}
