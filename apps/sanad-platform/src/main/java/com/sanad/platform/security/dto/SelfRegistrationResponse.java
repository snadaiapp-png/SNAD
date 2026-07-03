package com.sanad.platform.security.dto;

/** Public response after a workspace and administrator account are created. */
public class SelfRegistrationResponse {

    private final String message;
    private final String subdomain;
    private final boolean passwordSetupRequired;

    public SelfRegistrationResponse(String message, String subdomain, boolean passwordSetupRequired) {
        this.message = message;
        this.subdomain = subdomain;
        this.passwordSetupRequired = passwordSetupRequired;
    }

    public String getMessage() { return message; }
    public String getSubdomain() { return subdomain; }
    public boolean isPasswordSetupRequired() { return passwordSetupRequired; }
}
