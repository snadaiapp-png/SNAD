package com.sanad.platform.crm.email.domain;

import java.util.Objects;

/**
 * Typed email address value object — enforces RFC 5322 basic syntax.
 * <p>
 * All email operations in the CRM email bounded context use this
 * value object instead of raw strings to prevent injection and
 * ensure consistent normalization.
 */
public record EmailAddress(String value) {

    /** Compact constructor with validation. */
    public EmailAddress {
        Objects.requireNonNull(value, "email address must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("email address must not be blank");
        if (value.length() > 254) throw new IllegalArgumentException("email address must not exceed 254 characters");
        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("invalid email address format: " + value);
        }
    }

    /** Returns the lowercase, trimmed email address. */
    public String normalized() {
        return value.toLowerCase().trim();
    }

    /** Factory method that throws if the address is invalid. */
    public static EmailAddress of(String email) {
        return new EmailAddress(email);
    }

    @Override
    public String toString() {
        return value;
    }
}
