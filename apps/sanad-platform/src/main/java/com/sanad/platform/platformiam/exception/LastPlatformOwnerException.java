package com.sanad.platform.platformiam.exception;

/**
 * Stable conflict raised when a mutation would remove the final effective
 * ACTIVE Platform Owner recovery path.
 */
public final class LastPlatformOwnerException extends RuntimeException {

    public static final String REASON_CODE = "LAST_PLATFORM_OWNER";

    public LastPlatformOwnerException() {
        super(REASON_CODE + ": mutation would remove the final effective active platform owner");
    }

    public String reasonCode() {
        return REASON_CODE;
    }
}
