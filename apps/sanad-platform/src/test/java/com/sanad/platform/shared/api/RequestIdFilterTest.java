package com.sanad.platform.shared.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    @Test
    void requestIdHeaderIsConstant() {
        assertEquals("X-Request-Id", RequestIdFilter.REQUEST_ID_HEADER);
    }

    @Test
    void mdcKeyIsConstant() {
        assertEquals("requestId", RequestIdFilter.MDC_KEY);
    }
}
