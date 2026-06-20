package com.sanad.platform.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link ProductionDatabaseProperties} validation fails
 * when mandatory database properties are missing.
 *
 * <p>Uses {@link Validator} directly — no Spring context or Docker
 * required. The {@code @Validated} + {@code @ConfigurationProperties}
 * integration in Spring Boot guarantees that these validation
 * violations cause startup failure in a real application.</p>
 */
class ProductionDatabasePropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Validation fails when DATABASE_URL is missing")
    void failsWhenUrlMissing() {
        ProductionDatabaseProperties props = new ProductionDatabaseProperties();
        props.setUsername("user");
        props.setPassword("pass");

        Set<ConstraintViolation<ProductionDatabaseProperties>> violations = validator.validate(props);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("DATABASE_URL"));
    }

    @Test
    @DisplayName("Validation fails when DATABASE_USERNAME is missing")
    void failsWhenUsernameMissing() {
        ProductionDatabaseProperties props = new ProductionDatabaseProperties();
        props.setUrl("jdbc:postgresql://localhost/db");
        props.setPassword("pass");

        Set<ConstraintViolation<ProductionDatabaseProperties>> violations = validator.validate(props);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("DATABASE_USERNAME"));
    }

    @Test
    @DisplayName("Validation fails when DATABASE_PASSWORD is missing")
    void failsWhenPasswordMissing() {
        ProductionDatabaseProperties props = new ProductionDatabaseProperties();
        props.setUrl("jdbc:postgresql://localhost/db");
        props.setUsername("user");

        Set<ConstraintViolation<ProductionDatabaseProperties>> violations = validator.validate(props);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("DATABASE_PASSWORD"));
    }

    @Test
    @DisplayName("Validation passes when all mandatory properties are set")
    void passesWhenAllPropertiesSet() {
        ProductionDatabaseProperties props = new ProductionDatabaseProperties();
        props.setUrl("jdbc:postgresql://localhost/db");
        props.setUsername("user");
        props.setPassword("pass");

        Set<ConstraintViolation<ProductionDatabaseProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }
}
