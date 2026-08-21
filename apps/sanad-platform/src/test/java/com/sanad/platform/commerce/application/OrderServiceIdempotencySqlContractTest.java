package com.sanad.platform.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceIdempotencySqlContractTest {

    @Test
    void keyedClaim_usesBareOnConflictSoCartAndKeyInvariantsAreBothAtomicNoOps() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderService service = new OrderService(jdbc, mock(PlatformAuditService.class), new ObjectMapper());
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        service.tryClaimIdempotencyKey(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ORD-1", UUID.randomUUID(),
                "customer", Map.of("name", "Customer"), "SAR",
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("15.00"),
                BigDecimal.ZERO, new BigDecimal("115.00"), "idem-1", null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertThat(sql.getValue()).contains("ON CONFLICT DO NOTHING");
        assertThat(sql.getValue()).doesNotContain("ON CONFLICT (tenant_id, idempotency_key)");
        assertThat(sql.getValue()).doesNotContain("ON CONFLICT (tenant_id, cart_id)");
    }
}
