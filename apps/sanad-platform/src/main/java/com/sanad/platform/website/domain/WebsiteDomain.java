package com.sanad.platform.website.domain;

import java.util.UUID;

/**
 * Website platform domain types (v20260816.3).
 */
public final class WebsiteDomain {

    private WebsiteDomain() {}

    public enum WebsiteStatus { DRAFT, ACTIVE, SUSPENDED, ARCHIVED }

    public enum PageStatus { DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED }

    public enum PageType { STANDARD, HOME, ABOUT, CONTACT, BLOG, LANDING, CUSTOM }

    public enum DomainType { CUSTOM, DEFAULT_GENERATED }

    public enum VerificationStatus { PENDING, VERIFYING, VERIFIED, FAILED }

    public enum ActivationStatus { INACTIVE, ACTIVE, DISABLED }

    public enum VerificationMethod { DNS_TXT, DNS_CNAME, HTTP }

    public enum NavType { MAIN, FOOTER, MOBILE, CUSTOM }

    public enum NavTargetType { PAGE, URL, EXTERNAL }

    public enum PublicationType { PAGE, SITE, PARTIAL }

    public enum PublicationStatus { PUBLISHED, UNPUBLISHED }

    /** Reserved hostnames that cannot be claimed as custom domains. */
    public static final java.util.Set<String> RESERVED_HOSTNAME_PREFIXES = java.util.Set.of(
            "www", "admin", "api", "app", "mail", "smtp", "ftp", "localhost",
            "snad", "sanad", "platform", "manage", "management", "system",
            "health", "actuator", "v1", "v2"
    );

    /** Check if a hostname is reserved. */
    public static boolean isReservedHostname(String hostname) {
        if (hostname == null) return true;
        String lower = hostname.toLowerCase().trim();
        for (String prefix : RESERVED_HOSTNAME_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix + ".")
                    || lower.endsWith("." + prefix)) {
                return true;
            }
        }
        // Also protect the platform's own deployment domains
        if (lower.contains("vercel.app") || lower.contains("onrender.com")
                || lower.contains("snad.ai") || lower.contains("sanad.ai")) {
            return true;
        }
        return false;
    }
}
