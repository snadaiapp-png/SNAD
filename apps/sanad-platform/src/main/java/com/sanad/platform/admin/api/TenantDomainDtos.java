package com.sanad.platform.admin.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for the Tenant Domain Management capability (V20260815.20+).
 *
 * <p>These records model the tenant-scoped routing hostnames for the
 * application/store/website domains. No permanent public domain value
 * is hard-coded; the {@code DEFAULT_GENERATED} origin derives the
 * hostname from the configured {@code sanad.tenancy.domains.base-domain}
 * property at runtime.
 */
public final class TenantDomainDtos {

    private TenantDomainDtos() {}

    /** Domain type — which surface this hostname routes to. */
    public enum DomainType { APPLICATION, STORE, WEBSITE }

    /** Origin — whether the hostname was provided by the tenant or auto-derived. */
    public enum Origin { CUSTOM, DEFAULT_GENERATED }

    /** Lifecycle status of a tenant domain. */
    public enum Status { UNVERIFIED, VERIFIED, ACTIVE, INACTIVE }

    /** Verification challenge mechanism. */
    public enum VerificationMethod { DNS_TXT, DNS_CNAME, HTTP }

    public record CreateDomainRequest(
            String hostname,
            DomainType domainType,
            Origin origin,
            VerificationMethod verificationMethod
    ) {}

    public record UpdateDomainRequest(
            VerificationMethod verificationMethod,
            Boolean isPrimary
    ) {}

    public record VerifyDomainRequest(String verificationToken) {}

    public record DomainResponse(
            UUID id,
            UUID tenantId,
            String hostname,
            DomainType domainType,
            Origin origin,
            Status status,
            String verificationToken,
            VerificationMethod verificationMethod,
            Instant verifiedAt,
            UUID verifiedBy,
            String sslCertArn,
            boolean isPrimary,
            String failureReason,
            Instant lastVerifiedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DomainSummary(
            int totalCount,
            int activeCount,
            int verifiedCount,
            int unverifiedCount,
            int inactiveCount,
            boolean hasPrimary,
            String primaryHostname
    ) {}
}
