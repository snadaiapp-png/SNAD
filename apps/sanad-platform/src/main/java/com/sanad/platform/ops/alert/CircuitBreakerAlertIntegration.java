package com.sanad.platform.ops.alert;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;

/**
 * Integration point that dispatches operational alerts on circuit breaker state transitions.
 * <p>
 * Subscribes to Resilience4j circuit breaker event publishers and emits alerts
 * when a breaker transitions to OPEN or HALF_OPEN state.
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
@Component
public class CircuitBreakerAlertIntegration {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerAlertIntegration.class);

    private final OperationalAlertPort alertPort;
    private final Map<String, CircuitBreaker> circuitBreakers;

    public CircuitBreakerAlertIntegration(
            OperationalAlertPort alertPort,
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry registry) {
        this.alertPort = alertPort;
        this.circuitBreakers = registry.getAllCircuitBreakers().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CircuitBreaker::getName, cb -> cb));
    }

    @PostConstruct
    public void registerListeners() {
        circuitBreakers.forEach((name, cb) -> {
            cb.getEventPublisher()
                    .onStateTransition(event -> handleStateTransition(name, event));
            log.info("Registered circuit breaker alert listener for: {}", name);
        });
    }

    private void handleStateTransition(String name, CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreakerEvent.Type type = event.getEventType();
        String fromState = event.getStateTransition().getFromState().toString();
        String toState = event.getStateTransition().getToState().toString();

        String severity = "OPEN".equals(toState)
                ? OperationalAlertCategories.SEVERITY_CRITICAL
                : OperationalAlertCategories.SEVERITY_WARN;

        OperationalAlertPort.OperationalAlert alert = OperationalAlertPort.OperationalAlert.builder()
                .severity(severity)
                .category(OperationalAlertCategories.CATEGORY_CIRCUIT_BREAKER)
                .summary("Circuit breaker '" + name + "' transitioned: " + fromState + " → " + toState)
                .details("Event: " + type + ", Breaker: " + name)
                .service(name)
                .environment(alertPort.isEnabled() ? "production" : "development")
                .occurredAt(Instant.now())
                .build();

        alertPort.dispatch(alert);
    }
}
