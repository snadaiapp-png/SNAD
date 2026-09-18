package com.sanad.platform.hr.api.v2.recruitment;

import com.sanad.platform.hr.api.v2.HrApiErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1 T10 RED — exhaustive §15 error-code/status mapping.
 *
 * <p>The design introduces exactly these G1 codes on top of the G0 envelope.
 * This test is string-driven so the RED commit compiles before the production
 * enum is extended; missing codes fail as contract assertions, not compilation.</p>
 */
class HrRecruitmentApiErrorMappingTest {

    private static final Map<String, Integer> EXPECTED_STATUS = Map.ofEntries(
            Map.entry("OPENING_APPROVAL_REQUIRED", 422),
            Map.entry("OPENING_STATE_CONFLICT", 409),
            Map.entry("APPLICATION_STAGE_CONFLICT", 409),
            Map.entry("APPLICATION_DUP_ACTIVE", 409),
            Map.entry("OFFER_VERSION_CONFLICT", 409),
            Map.entry("OFFER_STATE_CONFLICT", 409),
            Map.entry("OFFER_EXPIRED", 409),
            Map.entry("OFFER_APPROVAL_REQUIRED", 422),
            Map.entry("CONVERSION_IDENTITY_AMBIGUOUS", 422),
            Map.entry("CONVERSION_ONBOARDING_TEMPLATE_INVALID", 422),
            Map.entry("CONVERSION_POSITION_OVER_OCCUPANCY", 409),
            Map.entry("TENANT_CONTEXT_MISMATCH", 404),
            Map.entry("IDEMPOTENCY_REPLAY", 200),
            Map.entry("WAIVER_REASON_REQUIRED", 400)
    );

    @Test
    void section15Codes_areExhaustivelyRegisteredWithStableHttpStatuses() {
        Map<String, Integer> actual = Arrays.stream(HrApiErrorCode.values())
                .collect(Collectors.toMap(Enum::name, HrApiErrorCode::httpStatus));

        assertThat(actual)
                .as("HRM G1 T10 §15 codes must extend the canonical G0 error enum")
                .containsAllEntriesOf(EXPECTED_STATUS);
    }

    @Test
    void replay_isSuccessSemantics_notAConflict() {
        Map<String, Integer> actual = Arrays.stream(HrApiErrorCode.values())
                .collect(Collectors.toMap(Enum::name, HrApiErrorCode::httpStatus));

        assertThat(actual.get("IDEMPOTENCY_REPLAY"))
                .as("§7 replay returns the original successful result with HTTP 200")
                .isEqualTo(200);
    }
}
