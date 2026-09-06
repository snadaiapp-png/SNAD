package com.sanad.platform.hr.integration;

import java.util.UUID;

/**
 * HR-side port into IAM user-account lifecycle (WS4 Task 7).
 *
 * <p>Hard boundary: HR NEVER writes IAM/user-account tables directly. All
 * employment-derived IAM effects go through this port and its single
 * adapter ({@code UserServiceIamEmploymentAccessAdapter}). Whether an
 * employment event may affect IAM at all is decided exclusively by
 * {@link HrmIamAccessPolicy} ({@code hr_iam_access_bindings}).</p>
 */
public interface IamEmploymentAccessPort {

    /** Disables (suspends) the IAM user account. */
    void disableUserAccount(UUID tenantId, UUID userId, String reason);

    /** Re-enables (activates) the IAM user account. */
    void enableUserAccount(UUID tenantId, UUID userId, String reason);

    /** Reports whether the IAM user account is currently active. */
    boolean isUserAccountActive(UUID tenantId, UUID userId);
}
