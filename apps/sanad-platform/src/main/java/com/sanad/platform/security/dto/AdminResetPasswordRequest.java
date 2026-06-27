package com.sanad.platform.security.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Pattern;

/** Request for an administrator-issued, single-use set-password link. */
public class AdminResetPasswordRequest {

    @Pattern(regexp = "(?i)ar|en", message = "locale must be ar or en")
    private String locale = "ar";

    public AdminResetPasswordRequest() {
    }

    public AdminResetPasswordRequest(String locale) {
        this.locale = locale;
    }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported administrative recovery field: " + field);
    }

    /** Legacy service compatibility guard. Direct password payloads are disabled. */
    @Deprecated
    public String getNewPassword() {
        throw new UnsupportedOperationException("Direct administrative password reset is disabled");
    }

    @Deprecated
    public boolean isForceChange() { return true; }
}
