package com.sanad.platform.partner.api;

import com.sanad.platform.security.service.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves partner identity exclusively from the authenticated, signed JWT
 * context populated by JwtAuthenticationFilter. Request parameters are never
 * accepted as the authoritative partner scope.
 */
@Component
public class PartnerClaimResolver {

    public PartnerPrincipal resolveRequired() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw forbidden("PARTNER_CONTEXT_REQUIRED");
        }
        if (!(authentication.getDetails() instanceof Map<?, ?> details)) {
            throw forbidden("PARTNER_CONTEXT_REQUIRED");
        }

        UUID userId = parseUuid(details.get("user_id"), "PARTNER_CONTEXT_REQUIRED");
        UUID partnerId = parseUuid(details.get(JwtTokenProvider.PARTNER_ID_CLAIM), "PARTNER_CONTEXT_REQUIRED");
        return new PartnerPrincipal(userId, partnerId);
    }

    public void assertRequestedPartnerMatches(UUID requestedPartnerId, UUID authenticatedPartnerId) {
        if (requestedPartnerId != null && !requestedPartnerId.equals(authenticatedPartnerId)) {
            throw forbidden("PARTNER_SCOPE_MISMATCH");
        }
    }

    private UUID parseUuid(Object value, String reason) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw forbidden(reason);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw forbidden(reason);
        }
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    public record PartnerPrincipal(UUID userId, UUID partnerId) {}
}
