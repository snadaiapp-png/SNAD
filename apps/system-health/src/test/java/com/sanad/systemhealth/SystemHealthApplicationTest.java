package com.sanad.systemhealth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemHealthApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exposesVersionedSystemHealthContract() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/system-health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("application", "sanad-system-health");
        assertThat(response.getBody()).containsEntry("maturity", "FOUNDATION");
        assertThat(response.getBody()).containsEntry("status", "HEALTHY");
    }

    @Test
    void exposesIndependentLivenessProbe() {
        ResponseEntity<Void> response = restTemplate.getForEntity("/api/v1/system-health/live", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void servesStandaloneOperationalInterface() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("صحة النظام والتصحيح الذاتي");
        assertThat(response.getBody()).contains("غير تابع لتطبيق الإدارة العليا");
    }
}
