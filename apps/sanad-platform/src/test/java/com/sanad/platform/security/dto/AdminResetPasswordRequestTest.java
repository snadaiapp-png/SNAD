package com.sanad.platform.security.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminResetPasswordRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsLegacyDirectPasswordPayload() {
        assertThrows(Exception.class, () -> objectMapper.readValue(
                "{\"newPassword\":\"NotAccepted123!\",\"forceChange\":true}",
                AdminResetPasswordRequest.class));
    }
}
