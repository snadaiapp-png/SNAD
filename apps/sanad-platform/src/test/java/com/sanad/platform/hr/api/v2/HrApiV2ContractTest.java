package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.CreateEmploymentRequest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 2 RED contract — structured HRM v2 error model.
 *
 * <p>Locks the canonical envelope for every canonical HRM v2 operation:
 *
 * <ul>
 *   <li>stable machine-readable {@code code} independent of localized message text</li>
 *   <li>{@code violations} array for field-level details</li>
 *   <li>status mapping: 400 validation, 403 scope denial, 404 missing resource,
 *       409 state/occupancy/idempotency/concurrency/migration conflicts,
 *       422 compliance/business blocking</li>
 *   <li>legacy text-prefixed {@code HRM_*} domain exceptions raised by WS2..WS6
 *       services surface through the same envelope without modifying service code</li>
 * </ul>
 *
 * <p>The probe controller is test-scope only and lives in the canonical
 * {@code com.sanad.platform.hr.api.v2} package so the production
 * {@code HrApiExceptionHandler} advice binds to it exactly as it will bind to
 * the real Task 3+ controllers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({SecurityPermitAllTestConfig.class, HrApiV2ContractTest.ProbeConfig.class})
class HrApiV2ContractTest {

    private static final String PROBE_BASE = "/api/v2/hr/probe";

    @Autowired private MockMvc mockMvc;

    /**
     * Every canonical error code maps to its fixed HTTP status and exposes the
     * stable envelope keys (code / message / violations).
     */
    @ParameterizedTest(name = "{0} maps to HTTP {1}")
    @CsvSource({
            "HRM_EMPLOYMENT_NOT_FOUND,          404",
            "HRM_INVALID_STATE_TRANSITION,      409",
            "HRM_ACTIVATION_BLOCKED,            409",
            "HRM_POSITION_OCCUPIED,             409",
            "HRM_ASSIGNMENT_OVERLAP,            409",
            "HRM_EMPLOYMENT_CONFLICT,           409",
            "HRM_SCOPE_DENIED,                  403",
            "HRM_COUNTRY_PACK_NOT_CERTIFIED,    422",
            "HRM_COMPLIANCE_BLOCKED,            422",
            "HRM_OVERRIDE_APPROVAL_REQUIRED,    422",
            "HRM_LEGAL_REVIEW_REQUIRED,         422",
            "HRM_IDEMPOTENCY_CONFLICT,          409",
            "HRM_CONCURRENCY_CONFLICT,          409",
            "HRM_MIGRATION_REQUIRED,            409"
    })
    void typedDomainException_mapsToStableStatusAndEnvelope(String code, int expectedStatus) throws Exception {
        mockMvc.perform(get(PROBE_BASE + "/typed/{code}", code))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.violations").isArray());
    }

    /**
     * WS2..WS6 services raise text-prefixed {@code IllegalStateException}.
     * The v2 envelope must surface them with identical status + code without
     * any modification to the service code.
     */
    @ParameterizedTest(name = "legacy IllegalStateException {0} maps to HTTP {1}")
    @CsvSource({
            "HRM_EMPLOYMENT_NOT_FOUND,          404",
            "HRM_INVALID_STATE_TRANSITION,      409",
            "HRM_ACTIVATION_BLOCKED,            409",
            "HRM_POSITION_OCCUPIED,             409",
            "HRM_ASSIGNMENT_OVERLAP,            409",
            "HRM_EMPLOYMENT_CONFLICT,           409",
            "HRM_SCOPE_DENIED,                  403",
            "HRM_COUNTRY_PACK_NOT_CERTIFIED,    422",
            "HRM_COMPLIANCE_BLOCKED,            422",
            "HRM_OVERRIDE_APPROVAL_REQUIRED,    422",
            "HRM_LEGAL_REVIEW_REQUIRED,         422",
            "HRM_IDEMPOTENCY_CONFLICT,          409",
            "HRM_CONCURRENCY_CONFLICT,          409",
            "HRM_MIGRATION_REQUIRED,            409"
    })
    void legacyIllegalState_textPrefix_mapsToSameStableStatus(String code, int expectedStatus) throws Exception {
        mockMvc.perform(get(PROBE_BASE + "/legacy-state/{code}", code))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.violations").isArray());
    }

    /** IllegalArgumentException variants (WS2 domain guards) map identically. */
    @ParameterizedTest(name = "legacy IllegalArgumentException {0} maps to HTTP {1}")
    @CsvSource({
            "HRM_EMPLOYMENT_NOT_FOUND, 404",
            "HRM_SCOPE_DENIED,         403",
            "HRM_COMPLIANCE_BLOCKED,   422"
    })
    void legacyIllegalArgument_textPrefix_mapsToSameStableStatus(String code, int expectedStatus) throws Exception {
        mockMvc.perform(get(PROBE_BASE + "/legacy-argument/{code}", code))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** Violation payload carries field + message for typed domain exceptions. */
    @Test
    void typedException_violationDetails_exposed() throws Exception {
        mockMvc.perform(get(PROBE_BASE + "/typed-with-violations"))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.code").value("HRM_ASSIGNMENT_OVERLAP"))
                .andExpect(jsonPath("$.violations.length()").value(2))
                .andExpect(jsonPath("$.violations[0].field").value("assignmentId"))
                .andExpect(jsonPath("$.violations[0].message").value("overlaps active assignment"))
                .andExpect(jsonPath("$.violations[1].field").value("effectiveDate"));
    }

    /** Code stability: message text may vary (localization), code must not. */
    @Test
    void codeStable_regardlessOfMessageText() throws Exception {
        String first = mockMvc.perform(get(PROBE_BASE + "/typed/{code}", "HRM_ACTIVATION_BLOCKED")
                        .locale(Locale.FRENCH))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get(PROBE_BASE + "/typed/{code}", "HRM_ACTIVATION_BLOCKED")
                        .locale(Locale.forLanguageTag("ar")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(first).contains("\"HRM_ACTIVATION_BLOCKED\"");
        org.assertj.core.api.Assertions.assertThat(second).contains("\"HRM_ACTIVATION_BLOCKED\"");
    }

    /** Bean validation failures produce 400 + typed violations envelope. */
    @Test
    void beanValidation_returns400_withViolationArray() throws Exception {
        mockMvc.perform(post(PROBE_BASE + "/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNumber\":\"\",\"laborJurisdictionCode\":\"usa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[?(@.field == 'personId')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'legalEntityId')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'laborJurisdictionCode')]").exists());
    }

    /** Valid typed DTO body binds (no 400) — proves DTOs are Jakarta-annotated, not Map-based. */
    @Test
    void typedDto_validBody_binds() throws Exception {
        mockMvc.perform(post(PROBE_BASE + "/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":"11111111-1111-1111-1111-111111111111",
                                 "legalEntityId":"22222222-2222-2222-2222-222222222222",
                                 "employeeNumber":"EMP-0001",
                                 "employmentStartDate":"2026-09-04",
                                 "laborJurisdictionCode":"SA",
                                 "workerClassificationCode":"FULL_TIME"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BOUND"));
    }

    /** Test-scope probe controller registered inside the canonical v2 package. */
    @TestConfiguration
    static class ProbeConfig {

        @RestController
        @RequestMapping(PROBE_BASE)
        static class ProbeController {

            @GetMapping("/typed/{code}")
            void typed(@PathVariable String code) {
                throw new HrDomainException(HrApiErrorCode.valueOf(code),
                        "probe typed failure for " + code);
            }

            @GetMapping("/typed-with-violations")
            void typedWithViolations() {
                throw HrDomainException.of(HrApiErrorCode.HRM_ASSIGNMENT_OVERLAP,
                        "assignment overlap probe",
                        List.of(HrApiErrorResponse.Violation.of("assignmentId", "overlaps active assignment"),
                                HrApiErrorResponse.Violation.of("effectiveDate", "must fall within assignment window")));
            }

            @GetMapping("/legacy-state/{code}")
            void legacyState(@PathVariable String code) {
                throw new IllegalStateException("HRM_" + code.replaceFirst("^HRM_", "")
                        + ": legacy service refusal with details for " + code);
            }

            @GetMapping("/legacy-argument/{code}")
            void legacyArgument(@PathVariable String code) {
                throw new IllegalArgumentException("HRM_" + code.replaceFirst("^HRM_", "")
                        + ": legacy domain guard refusal for " + code);
            }

            @PostMapping("/validation")
            java.util.Map<String, String> validation(@Valid @RequestBody CreateEmploymentRequest request) {
                return java.util.Map.of("code", "BOUND");
            }
        }
    }
}
