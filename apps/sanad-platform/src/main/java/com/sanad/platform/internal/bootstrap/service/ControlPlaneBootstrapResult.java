package com.sanad.platform.internal.bootstrap.service;

import java.util.List;
import java.util.UUID;

/**
 * Immutable result of a Control Plane admin bootstrap operation.
 *
 * <p>This record is intentionally free of any secret material: the password is
 * never returned, and the email is masked at the controller layer.</p>
 *
 * @param tenantId              UUID of the Control Plane tenant the admin was bootstrapped into.
 * @param userId                UUID of the bootstrapped admin user.
 * @param adminEmail            The full admin email (controller masks it before returning to caller).
 * @param created               {@code true} if the user record was newly created or re-enrolled.
 * @param membershipActivated   {@code true} if the admin's organization membership was activated.
 * @param roleGrantsActivated   List of role grant codes activated for the admin.
 */
public record ControlPlaneBootstrapResult(
        UUID tenantId,
        UUID userId,
        String adminEmail,
        boolean created,
        boolean membershipActivated,
        List<String> roleGrantsActivated
) {}
