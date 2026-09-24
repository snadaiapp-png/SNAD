package com.sanad.platform.module.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CapabilityType}.
 */
@DisplayName("CapabilityType — unit tests")
class CapabilityTypeTest {

    @Test
    @DisplayName("fromString: parses valid type names")
    void fromString_parsesValidTypes() {
        assertThat(CapabilityType.fromString("MODULE_ENABLED")).isEqualTo(CapabilityType.MODULE_ENABLED);
        assertThat(CapabilityType.fromString("FEATURE_ENABLED")).isEqualTo(CapabilityType.FEATURE_ENABLED);
        assertThat(CapabilityType.fromString("NUMERIC_LIMIT")).isEqualTo(CapabilityType.NUMERIC_LIMIT);
        assertThat(CapabilityType.fromString("QUOTA")).isEqualTo(CapabilityType.QUOTA);
        assertThat(CapabilityType.fromString("BOOLEAN_CAPABILITY")).isEqualTo(CapabilityType.BOOLEAN_CAPABILITY);
    }

    @Test
    @DisplayName("fromString: uppercases input before parsing")
    void fromString_uppercasesInput() {
        assertThat(CapabilityType.fromString("module_enabled")).isEqualTo(CapabilityType.MODULE_ENABLED);
        assertThat(CapabilityType.fromString("  Quota  ")).isEqualTo(CapabilityType.QUOTA);
    }

    @Test
    @DisplayName("fromString: throws for null/blank input")
    void fromString_throwsForNull() {
        assertThatThrownBy(() -> CapabilityType.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> CapabilityType.fromString(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CapabilityType.fromString("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromString: throws for invalid type name")
    void fromString_throwsForInvalid() {
        assertThatThrownBy(() -> CapabilityType.fromString("INVALID_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isBoolean: returns true for boolean types")
    void isBoolean_returnsTrueForBooleanTypes() {
        assertThat(CapabilityType.MODULE_ENABLED.isBoolean()).isTrue();
        assertThat(CapabilityType.FEATURE_ENABLED.isBoolean()).isTrue();
        assertThat(CapabilityType.BOOLEAN_CAPABILITY.isBoolean()).isTrue();
    }

    @Test
    @DisplayName("isBoolean: returns false for numeric types")
    void isBoolean_returnsFalseForNumericTypes() {
        assertThat(CapabilityType.NUMERIC_LIMIT.isBoolean()).isFalse();
        assertThat(CapabilityType.QUOTA.isBoolean()).isFalse();
    }

    @Test
    @DisplayName("isNumeric: returns true for numeric types")
    void isNumeric_returnsTrueForNumericTypes() {
        assertThat(CapabilityType.NUMERIC_LIMIT.isNumeric()).isTrue();
        assertThat(CapabilityType.QUOTA.isNumeric()).isTrue();
    }

    @Test
    @DisplayName("isNumeric: returns false for boolean types")
    void isNumeric_returnsFalseForBooleanTypes() {
        assertThat(CapabilityType.MODULE_ENABLED.isNumeric()).isFalse();
        assertThat(CapabilityType.FEATURE_ENABLED.isNumeric()).isFalse();
        assertThat(CapabilityType.BOOLEAN_CAPABILITY.isNumeric()).isFalse();
    }
}
