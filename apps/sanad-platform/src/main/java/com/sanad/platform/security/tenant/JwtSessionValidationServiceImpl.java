package com.sanad.platform.security.tenant;

import com.sanad.platform.security.denial.SecurityDenialCategory;
import com.sanad.platform.security.denial.SessionValidationResult;
import com.sanad.platform.security.tenant.JwtSessionValidationService.VerifiedJwtClaims;
import com.sanad.platform.security.tenant.JwtSessionValidationService.ValidatedSession;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stage 04A.1 §4 — Implementation of {@link JwtSessionValidationService}.
 *
 * <p>Runs inside a read-only transaction with a provisional TenantContext
 * established from the JWT claims. The session-version query against the
 * RLS-protected {@code users} table executes with the correct tenant
 * setting.</p>
 *
 * <p>This breaks the authentication/RLS cycle: previously, the JWT filter
 * queried the users table BEFORE establishing TenantContext, which would
 * fail under RLS (no tenant setting → 0 rows returned).</p>
 */
@Service
public class JwtSessionValidationServiceImpl implements JwtSessionValidationService {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionValidationServiceImpl.class);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantContextProvider contextProvider;
    private final TenantRlsBinder rlsBinder;
    private final com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository membershipRepository;

    public JwtSessionValidationServiceImpl(UserRepository userRepository,
                                            TenantRepository tenantRepository,
                                            TenantContextProvider contextProvider,
                                            TenantRlsBinder rlsBinder,
                                            com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.contextProvider = contextProvider;
        this.rlsBinder = rlsBinder;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public SessionValidationResult validateAndClassify(VerifiedJwtClaims claims) {
        // Establish provisional TenantContext from verified JWT claims
        TenantContext provisionalContext = new TenantContext(
                claims.tenantId(),
                claims.userId(),
                claims.tokenId(),
                claims.sessionVersion(),
                java.util.Set.of(),
                TenantContext.TenantContextSource.PROVISIONAL_TOKEN_VALIDATION,
                null
        );
        contextProvider.setContext(provisionalContext);

        try {
            // Bind RLS tenant setting to this transaction's connection
            rlsBinder.bindTenantToCurrentTransaction();

            // Now query the RLS-protected users table — the tenant setting
            // is active, so the query will find the user if they belong
            // to the JWT's tenant.
            Long currentSessionVersion = userRepository
                    .findSessionVersionByTenantIdAndId(claims.tenantId(), claims.userId());

            if (currentSessionVersion == null) {
                log.debug("Session validation failed: user not found userId={} tenantId={}",
                        claims.userId(), claims.tenantId());
                return new SessionValidationResult.Invalid(
                        SecurityDenialCategory.UNKNOWN_SESSION);
            }

            // Check session version — a mismatch means the session was
            // revoked server-side (logout, password change, admin revoke).
            if (claims.sessionVersion() != currentSessionVersion) {
                log.debug("Session version mismatch: JWT={} DB={} userId={}",
                        claims.sessionVersion(), currentSessionVersion, claims.userId());
                return new SessionValidationResult.Invalid(
                        SecurityDenialCategory.REVOKED_SESSION);
            }

            // Load user to check status
            User user = userRepository
                    .findByTenantIdAndId(claims.tenantId(), claims.userId())
                    .orElse(null);

            if (user == null) {
                return new SessionValidationResult.Invalid(
                        SecurityDenialCategory.UNKNOWN_SESSION);
            }

            boolean userActive = user.getStatus() == UserStatus.ACTIVE;

            // Check tenant status
            Tenant tenant = tenantRepository.findById(claims.tenantId()).orElse(null);
            boolean tenantActive = tenant != null && tenant.getStatus() == TenantStatus.ACTIVE;

            // Stage 04A.3.5: reject suspended users and archived tenants.
            // A suspended user is treated as REVOKED_SESSION (the session
            // is no longer authoritative); an archived tenant is
            // UNVERIFIED_TENANT (the tenant identity is no longer valid).
            if (!userActive) {
                log.debug("Session validation failed: user not active userId={} status={}",
                        claims.userId(), user.getStatus());
                return new SessionValidationResult.Invalid(
                        SecurityDenialCategory.REVOKED_SESSION);
            }
            if (!tenantActive) {
                log.debug("Session validation failed: tenant not active tenantId={} status={}",
                        claims.tenantId(), tenant != null ? tenant.getStatus() : "null");
                return new SessionValidationResult.Invalid(
                        SecurityDenialCategory.UNVERIFIED_TENANT);
            }

            // Stage 04A.3.6.2: STRICT membership entity-match enforcement.
            // The user MUST have at least one ACTIVE membership whose
            // (tenantId, userId) pair exactly matches the verified JWT claims.
            // No fail-open: if no such membership exists, the user is denied.
            var memberships = membershipRepository.findByTenantIdAndUserId(
                    claims.tenantId(), claims.userId());
            boolean hasActiveMembership = memberships.stream()
                    .anyMatch(membership ->
                            claims.tenantId().equals(membership.getTenantId())
                            && claims.userId().equals(membership.getUserId())
                            && membership.getStatus() == com.sanad.platform.organization.membership.domain.MembershipStatus.ACTIVE
                    );
            if (!hasActiveMembership) {
                log.debug("Session validation failed: no active membership userId={} tenantId={}",
                        claims.userId(), claims.tenantId());
                return new SessionValidationResult.Invalid(
                        SecurityDenialCategory.UNKNOWN_SESSION);
            }

            return new SessionValidationResult.Valid(
                    new ValidatedSession(
                            claims.tenantId(),
                            claims.userId(),
                            claims.tokenId(),
                            claims.email(),
                            currentSessionVersion,
                            claims.rotationRequired(),
                            userActive,
                            tenantActive
                    )
            );
        } finally {
            // Clear provisional context — the JwtAuthenticationFilter will
            // establish the final TenantContext via TenantContextFilter
            contextProvider.clear();
        }
    }
}
