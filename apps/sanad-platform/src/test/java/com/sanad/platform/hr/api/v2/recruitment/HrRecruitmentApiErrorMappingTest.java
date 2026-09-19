package com.sanad.platform.hr.api.v2.recruitment;

import com.sanad.platform.hr.api.v2.HrApiErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1 T10 RED — §15 adds stable client-facing recruitment/onboarding
 * errors. G0's canonical contract prefixes enum identifiers with HRM_, which
 * also matches the legacy service exception prefixes already emitted by T3-T9.
 */
class HrRecruitmentApiErrorMappingTest {

    private static final List<String> G1_ERROR_CODES = List.of(
            "HRM_OPENING_APPROVAL_REQUIRED",
            "HRM_OPENING_STATE_CONFLICT",
            "HRM_APPLICATION_STAGE_CONFLICT",
            "HRM_APPLICATION_DUP_ACTIVE",
            "HRM_OFFER_VERSION_CONFLICT",
            "HRM_OFFER_STATE_CONFLICT",
            "HRM_OFFER_EXPIRED",
            "HRM_OFFER_APPROVAL_REQUIRED",
            "HRM_CONVERSION_IDENTITY_AMBIGUOUS",
            "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID",
            "HRM_CONVERSION_POSITION_OVER_OCCUPANCY",
            "HRM_TENANT_CONTEXT_MISMATCH",
            "HRM_WAIVER_REASON_REQUIRED"
    );

    @Test
    void section15ErrorsAreRepresentedByCanonicalHrApiErrorCode() {
        Set<String> actual = Arrays.stream(HrApiErrorCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(actual)
                .as("HRM-G1 T10 §15 errors must be mapped by the canonical G0 error contract")
                .containsAll(G1_ERROR_CODES);

        for (String code : G1_ERROR_CODES) {
            assertThat(HrApiErrorCode.valueOf(code).httpStatus())
                    .as("%s must have a concrete stable HTTP mapping", code)
                    .isBetween(400, 599);
        }
    }
}
