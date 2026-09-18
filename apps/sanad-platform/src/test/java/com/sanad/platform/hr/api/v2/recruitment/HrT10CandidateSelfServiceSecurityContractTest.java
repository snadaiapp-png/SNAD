package com.sanad.platform.hr.api.v2.recruitment;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.RecruitmentCapabilities;
import com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.hr.security.HrResourceContextResolver;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1 T10 RED — candidate self-service must use IAM-bound candidate ownership
 * through the existing G0 scoped-authorization machinery. It must never reuse
 * operator MANAGE capabilities for candidate submission/withdrawal/offer response.
 */
class HrT10CandidateSelfServiceSecurityContractTest {

    private static final String APPLICATION_SUBMIT = "HRM.RECRUITMENT.APPLICATION.SUBMIT";
    private static final String APPLICATION_WITHDRAW = "HRM.RECRUITMENT.APPLICATION.WITHDRAW";
    private static final String OFFER_ACCEPT = "HRM.RECRUITMENT.OFFER.ACCEPT";
    private static final String OFFER_DECLINE = "HRM.RECRUITMENT.OFFER.DECLINE";

    @Test
    void capabilityFamily_containsActionSpecificCandidateScopedCommands() {
        assertThat(RecruitmentCapabilities.all())
                .contains(APPLICATION_SUBMIT, APPLICATION_WITHDRAW, OFFER_ACCEPT, OFFER_DECLINE);
    }

    @Test
    void controller_candidateSelfServiceRoutes_doNotReuseOperatorManageCapabilities() throws Exception {
        assertCapability("createApplication", APPLICATION_SUBMIT);
        assertCapability("withdrawApplication", APPLICATION_WITHDRAW);
        assertCapability("acceptOffer", OFFER_ACCEPT);
        assertCapability("declineOffer", OFFER_DECLINE);
    }

    @Test
    void candidateCreateRequest_exposesExplicitExistingIamUserBinding() {
        Set<String> names = Arrays.stream(HrRecruitmentV2Controller.CreateCandidateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(names).contains("iamUserId");
        RecordComponent iamUserId = Arrays.stream(
                        HrRecruitmentV2Controller.CreateCandidateRequest.class.getRecordComponents())
                .filter(component -> component.getName().equals("iamUserId"))
                .findFirst()
                .orElseThrow();
        assertThat(iamUserId.getType()).isEqualTo(UUID.class);
    }

    @Test
    void scopedAuthorizationResource_canCarryCandidateOwnershipWithoutPersonConversion() throws Exception {
        assertThat(HrAuthorizationResourceContext.class.getMethod("candidateId").getReturnType())
                .isEqualTo(UUID.class);
        assertThat(HrResourceContextResolver.class.getMethod(
                "isCandidateSelf", UUID.class, UUID.class, UUID.class).getReturnType())
                .isEqualTo(boolean.class);
    }

    @Test
    void recruitmentAuthorizationPort_exposesActionSpecificScopedChecks() throws Exception {
        assertThat(RecruitmentAuthorizationPort.class.getMethod(
                "requireApplicationSubmit", HrCommandContext.class, UUID.class)).isNotNull();
        assertThat(RecruitmentAuthorizationPort.class.getMethod(
                "requireApplicationWithdraw", HrCommandContext.class, UUID.class)).isNotNull();
        assertThat(RecruitmentAuthorizationPort.class.getMethod(
                "requireOfferAccept", HrCommandContext.class, UUID.class)).isNotNull();
        assertThat(RecruitmentAuthorizationPort.class.getMethod(
                "requireOfferDecline", HrCommandContext.class, UUID.class)).isNotNull();
    }

    private static void assertCapability(String methodName, String expected) {
        var method = Arrays.stream(HrRecruitmentV2Controller.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing controller method " + methodName));
        RequireCapability capability = method.getAnnotation(RequireCapability.class);
        assertThat(capability)
                .as("%s must declare @RequireCapability", methodName)
                .isNotNull();
        assertThat(capability.value()).isEqualTo(expected);
    }
}
