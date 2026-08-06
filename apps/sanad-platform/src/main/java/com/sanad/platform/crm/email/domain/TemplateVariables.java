package com.sanad.platform.crm.email.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed value object for email template variables.
 * <p>
 * Enforces architecture purity by encapsulating dynamic template data
 * behind a typed interface while preserving the flexible nature of
 * template variable maps.
 */
public record TemplateVariables(Map<String, Object> values) {

    /** Empty instance. */
    public static final TemplateVariables EMPTY = new TemplateVariables(Map.of());

    /** Compact constructor — normalises null to empty map, defensive copy. */
    public TemplateVariables {
        Objects.requireNonNull(values, "values must not be null");
        values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    /**
     * Create from a map.
     */
    public static TemplateVariables of(Map<String, Object> values) {
        return new TemplateVariables(values != null ? values : Map.of());
    }

    /**
     * Create from a single key-value pair.
     */
    public static TemplateVariables of(String key, Object value) {
        return new TemplateVariables(Map.of(key, value));
    }

    /**
     * Merge with another set of variables (other wins on conflict).
     */
    public TemplateVariables merge(TemplateVariables other) {
        if (other == null || other.values.isEmpty()) return this;
        Map<String, Object> merged = new HashMap<>(this.values);
        merged.putAll(other.values);
        return new TemplateVariables(merged);
    }

    /** Get a value by key, or null. */
    public Object get(String key) {
        return values.get(key);
    }

    /** Check if a key exists. */
    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    /** Check if empty. */
    public boolean isEmpty() {
        return values.isEmpty();
    }
}
