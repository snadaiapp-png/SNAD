package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrmOwnershipHttpSupportTest {

    private final CrmOwnershipHttpSupport support = new CrmOwnershipHttpSupport(
            new IdempotencyService.InMemoryIdempotencyService(),
            new ObjectMapper().findAndRegisterModules(),
            new ETagService());

    @Test
    void trustedContextComesOnlyFromAuthenticationDetails() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var context = support.context(authentication(tenantId, userId));
        assertThat(context.tenantId()).isEqualTo(tenantId);
        assertThat(context.userId()).isEqualTo(userId);
    }

    @Test
    void missingTrustedContextIsRejected() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator", "n/a", List.of());
        assertThatThrownBy(() -> support.context(authentication))
                .isInstanceOf(CrmContractException.class);
    }

    @Test
    void postResultIsReplayedAndPayloadMismatchConflicts() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var authentication = authentication(tenantId, userId);
        MockHttpServletRequest firstRequest = request();
        String key = UUID.randomUUID().toString();
        Payload payload = new Payload("same");

        var first = support.begin(
                authentication, "POST:/api/v2/crm/test", key, payload, firstRequest);
        assertThat(first.isReplay()).isFalse();
        var completed = support.complete(
                first, payload, Payload.class, HttpStatus.CREATED,
                null, null, 0L);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var replay = support.begin(
                authentication, "POST:/api/v2/crm/test", key, payload, request());
        assertThat(replay.isReplay()).isTrue();
        assertThat(support.replay(replay, Payload.class).getBody().data())
                .isEqualTo(payload);

        assertThatThrownBy(() -> support.begin(
                authentication, "POST:/api/v2/crm/test", key,
                new Payload("different"), request()))
                .isInstanceOf(CrmContractException.class);
    }

    @Test
    void idempotencyKeyAndIfMatchAreMandatory() {
        var authentication = authentication(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> support.begin(
                authentication, "POST:/api/v2/crm/test", null,
                new Payload("value"), request()))
                .isInstanceOf(CrmContractException.class);

        assertThatThrownBy(() -> support.validateIfMatch(
                null, "sales-team", UUID.randomUUID(), 1L))
                .isInstanceOf(CrmContractException.class);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID tenantId, UUID userId) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return authentication;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/crm/test");
        request.addHeader("X-Request-ID", UUID.randomUUID().toString());
        request.addHeader("X-Correlation-ID", UUID.randomUUID().toString());
        return request;
    }

    record Payload(String value) { }
}
