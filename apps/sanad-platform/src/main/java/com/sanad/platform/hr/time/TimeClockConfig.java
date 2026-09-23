package com.sanad.platform.hr.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides an injectable {@link Clock} bean for time-dependent business logic.
 *
 * <p>All G2 services that need "now" should inject this Clock rather than
 * calling {@code Instant.now()} or {@code LocalDate.now()} directly.
 * This prevents time-bomb tests and allows deterministic testing.
 *
 * <p>In tests, override this bean with a fixed Clock.
 */
@Configuration
public class TimeClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.system(ZoneId.of("UTC"));
    }
}
