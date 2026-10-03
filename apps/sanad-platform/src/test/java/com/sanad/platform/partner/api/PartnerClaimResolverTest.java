package com.sanad.platform.partner.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerClaimResolverTest {

    private final PartnerClaimResolver resolver = new PartnerClaimResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesPartnerOnlyFromAuthenticatedDetails() {
        UUID user = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        authenticate(user, partner);

        PartnerClaimResolver.PartnerPrincipal principal = resolver.resolveRequired();

        assertThat(principal.userId()).isEqualTo(user);
        assertThat(principal.partnerId()).isEqualTo(partner);
    }

    @Test
    void missingPartnerClaimFailsClosed() {
        UUID user = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.toString(), null, java.util.List.of());
        auth.setDetails(Map.of("user_id", user.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(resolver::resolveRequired)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("PARTNER_CONTEXT_REQUIRED");
                });
    }

    @Test
    void forgedPartnerParameterIsRejected() {
        UUID partnerA = UUID.randomUUID();
        UUID partnerB = UUID.randomUUID();

        assertThatThrownBy(() -> resolver.assertRequestedPartnerMatches(partnerB, partnerA))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("PARTNER_SCOPE_MISMATCH");
                });
    }

    @Test
    void matchingOrAbsentPartnerParameterCannotOverrideClaim() {
        UUID partner = UUID.randomUUID();
        resolver.assertRequestedPartnerMatches(null, partner);
        resolver.assertRequestedPartnerMatches(partner, partner);
    }

    private void authenticate(UUID userId, UUID partnerId) {
        Map<String, Object> details = new HashMap<>();
        details.put("user_id", userId.toString());
        details.put("partner_id", partnerId.toString());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId.toString(), null, java.util.List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
