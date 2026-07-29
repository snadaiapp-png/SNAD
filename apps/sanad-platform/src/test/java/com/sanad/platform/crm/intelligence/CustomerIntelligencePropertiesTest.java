package com.sanad.platform.crm.intelligence;

import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CustomerIntelligenceProperties configuration.
 */
class CustomerIntelligencePropertiesTest {

    @Test
    void defaultProperties_allProvidersAreMock() {
        CustomerIntelligenceProperties props = new CustomerIntelligenceProperties();

        assertThat(props.getErp().isMock()).isTrue();
        assertThat(props.getHrm().isMock()).isTrue();
        assertThat(props.getPos().isMock()).isTrue();
        assertThat(props.getAccounting().isMock()).isTrue();
        assertThat(props.getCommerce().isMock()).isTrue();
    }

    @Test
    void defaultScoring_hasExpectedDefaults() {
        CustomerIntelligenceProperties props = new CustomerIntelligenceProperties();

        assertThat(props.getScoring().getBatchSize()).isEqualTo(100);
        assertThat(props.getScoring().getRescoreIntervalMinutes()).isEqualTo(360);
        assertThat(props.getScoring().getMinConfidence()).isEqualTo(0.6);
    }

    @Test
    void providerTypeDetection_worksCorrectly() {
        CustomerIntelligenceProperties.ExternalProvider mockProvider = new CustomerIntelligenceProperties.ExternalProvider("mock");
        CustomerIntelligenceProperties.ExternalProvider httpProvider = new CustomerIntelligenceProperties.ExternalProvider("http");
        CustomerIntelligenceProperties.ExternalProvider disabledProvider = new CustomerIntelligenceProperties.ExternalProvider("disabled");

        assertThat(mockProvider.isMock()).isTrue();
        assertThat(httpProvider.isHttp()).isTrue();
        assertThat(disabledProvider.isDisabled()).isTrue();
    }
}
