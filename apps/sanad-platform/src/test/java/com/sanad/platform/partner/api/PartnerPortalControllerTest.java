package com.sanad.platform.partner.api;

import com.sanad.platform.partner.delegation.PartnerDelegationDecision;
import com.sanad.platform.partner.delegation.PartnerDelegationGate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PartnerPortalControllerTest {

    private final PartnerClaimResolver resolver = mock(PartnerClaimResolver.class);
    private final PartnerPortalService service = mock(PartnerPortalService.class);
    private final PartnerDelegationGate gate = mock(PartnerDelegationGate.class);
    private final PartnerPortalController controller =
            new PartnerPortalController(resolver, service, gate);

    @Test
    void partnerAListsOnlyClaimResolvedScope() {
        UUID user = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        var principal = new PartnerClaimResolver.PartnerPrincipal(user, partnerA);
        var row = new PartnerPortalService.PartnerTenantBindingView(
                UUID.randomUUID(), tenant, "ACTIVE", null, null);

        when(resolver.resolveRequired()).thenReturn(principal);
        when(service.listOwnTenants(user, partnerA)).thenReturn(List.of(row));

        var response = controller.tenants(null);

        assertThat(response.getBody()).containsExactly(row);
        verify(service).listOwnTenants(user, partnerA);
        verifyNoMoreInteractions(gate);
    }

    @Test
    void callerSuppliedPartnerBIsRejectedBeforeServiceAccess() {
        UUID user = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID partnerB = UUID.randomUUID();
        var principal = new PartnerClaimResolver.PartnerPrincipal(user, partnerA);

        when(resolver.resolveRequired()).thenReturn(principal);
        doThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "PARTNER_SCOPE_MISMATCH"))
                .when(resolver).assertRequestedPartnerMatches(partnerB, partnerA);

        assertThatThrownBy(() -> controller.tenants(partnerB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_SCOPE_MISMATCH"));

        verifyNoInteractions(service);
        verifyNoInteractions(gate);
    }

    @Test
    void missingDelegationFailsClosed() {
        UUID user = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        var principal = new PartnerClaimResolver.PartnerPrincipal(user, partnerA);

        when(resolver.resolveRequired()).thenReturn(principal);
        when(service.requireActiveMembership(user, partnerA))
                .thenReturn(new PartnerPortalService.PartnerSummary(
                        partnerA, user, "PARTNER_USER", "ACTIVE"));
        when(gate.check(partnerA, tenant, "TENANT.SUSPEND"))
                .thenReturn(PartnerDelegationDecision.deny(
                        "DELEGATION_MISSING", "TENANT.SUSPEND", partnerA, tenant));

        assertThatThrownBy(() ->
                controller.authorize(tenant, "TENANT.SUSPEND", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("DELEGATION_MISSING");
                });
    }

    @Test
    void foreignTenantDecisionIsDenied() {
        UUID user = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID foreignTenant = UUID.randomUUID();
        var principal = new PartnerClaimResolver.PartnerPrincipal(user, partnerA);

        when(resolver.resolveRequired()).thenReturn(principal);
        when(service.requireActiveMembership(user, partnerA))
                .thenReturn(new PartnerPortalService.PartnerSummary(
                        partnerA, user, "PARTNER_USER", "ACTIVE"));
        when(gate.check(partnerA, foreignTenant, "TENANT.SUSPEND"))
                .thenReturn(PartnerDelegationDecision.deny(
                        "PARTNER_MISMATCH", "TENANT.SUSPEND", partnerA, foreignTenant));

        assertThatThrownBy(() ->
                controller.authorize(foreignTenant, "TENANT.SUSPEND", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_MISMATCH"));
    }
}
