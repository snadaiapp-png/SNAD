package com.sanad.platform.shared.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void allCodesAreUnique() {
        var codes = java.util.Arrays.stream(ErrorCode.values())
            .map(ErrorCode::code)
            .toList();
        assertEquals(codes.size(), codes.stream().distinct().count());
    }

    @Test
    void allCodesFollowPattern() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(ec.code().startsWith("SANAD-"), "Code should start with SANAD-: " + ec.code());
        }
    }

    @Test
    void keyErrorCodesExist() {
        assertNotNull(ErrorCode.SANAD_GEN_001);
        assertNotNull(ErrorCode.SANAD_VAL_001);
        assertNotNull(ErrorCode.SANAD_AUTH_001);
        assertNotNull(ErrorCode.SANAD_SEC_001);
        assertNotNull(ErrorCode.SANAD_RES_001);
        assertNotNull(ErrorCode.SANAD_CON_001);
        assertNotNull(ErrorCode.SANAD_PAG_001);
        assertNotNull(ErrorCode.SANAD_RATE_001);
    }

    @Test
    void statusCodesAreValid() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(ec.status() >= 400 && ec.status() < 600);
        }
    }
}
