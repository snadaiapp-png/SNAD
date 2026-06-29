package com.sanad.platform.security.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public account-registration request. Password setup is completed by email. */
public class SelfRegistrationRequest {

    @NotBlank(message = "displayName must not be blank")
    @Size(max = 200, message = "displayName must be at most 200 characters")
    private String displayName;

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    @NotBlank(message = "organizationName must not be blank")
    @Size(max = 200, message = "organizationName must be at most 200 characters")
    private String organizationName;

    @NotBlank(message = "regionCode must not be blank")
    @Pattern(regexp = "^[A-Z]{2}$", message = "regionCode must be an ISO 3166-1 alpha-2 code")
    private String regionCode;

    @NotBlank(message = "countryCode must not be blank")
    @Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "countryCode must be a valid international calling code")
    private String countryCode;

    @NotBlank(message = "mobileNumber must not be blank")
    @Pattern(regexp = "^[0-9]{7,15}$", message = "mobileNumber must contain 7 to 15 digits")
    private String mobileNumber;

    @AssertTrue(message = "terms must be accepted")
    private boolean acceptTerms;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.trim();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName == null ? null : organizationName.trim();
    }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode == null ? null : regionCode.trim().toUpperCase();
    }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode == null ? null : countryCode.trim();
    }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber == null ? null : mobileNumber.trim();
    }

    public boolean isAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(boolean acceptTerms) { this.acceptTerms = acceptTerms; }
}
