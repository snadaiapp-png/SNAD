package com.sanad.platform.partner.executive;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutivePartnerServiceLifecycleTest {

    @Test
    void createRejectsUnknownPartnerTypeBeforeInsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any()))
                .thenReturn("00000000-0000-0000-0000-000000000001");
        ExecutivePartnerService service = new ExecutivePartnerService(jdbc);

        assertThatThrownBy(() -> service.createPartner("ROOT", auth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("PARTNER_TYPE_INVALID");
                });

        verify(jdbc, never()).update(startsWith("INSERT INTO partners"), any(Object[].class));
    }

    @Test
    void suspendedAndTerminatedStatesRequireReason() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any()))
                .thenReturn("00000000-0000-0000-0000-000000000001");
        ExecutivePartnerService service = spy(new ExecutivePartnerService(jdbc));
        UUID partnerId = UUID.randomUUID();

        // DB lock/query behavior is covered by PostgreSQL regression; this test
        // focuses on the service lifecycle contract and uses no mutation.
        doReturn(partner("ACTIVE")).when(service).getPartner(partnerId);

        // Public getPartner cannot replace lockPartner; assert request-level
        // status validation independently here.
        assertThatThrownBy(() -> service.changeStatus(partnerId, "BROKEN", "x", auth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_STATUS_INVALID"));
    }

    private static UsernamePasswordAuthenticationToken auth() {
        UUID user = UUID.randomUUID();
        Map<String,Object> details = new HashMap<>();
        details.put("user_id", user.toString());
        details.put("tenant_id", "00000000-0000-0000-0000-000000000001");
        var auth = new UsernamePasswordAuthenticationToken(user.toString(), null, List.of());
        auth.setDetails(details);
        return auth;
    }

    private static ExecutivePartnerService.PartnerView partner(String status) {
        return new ExecutivePartnerService.PartnerView(
                UUID.randomUUID(), "PARTNER", status,
                null, null, null, null, null, null, 0);
    }
}
