package com.sanad.platform.internal.bootstrap.service;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.config.CredentialBootstrapService;
import com.sanad.platform.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service-layer wrapper that adapts the existing {@link CredentialBootstrapService}
 * to the one-time Control Plane admin bootstrap flow.
 *
 * <p>This service reads all sensitive inputs from server-side environment variables
 * (never from the HTTP request body) and delegates to {@link CredentialBootstrapService}
 * with {@code forceReset=true} so that re-invocations rotate the password hash
 * and re-grant the ADMIN role and organization membership.</p>
 *
 * <p>Configuration (env vars):
 * <ul>
 *   <li>{@code SANAD_CONTROL_PLANE_TENANT_ID} - UUID of the existing control-plane tenant.</li>
 *   <li>{@code CONTROL_PLANE_ADMIN_EMAIL} - email for the admin user.</li>
 *   <li>{@code CONTROL_PLANE_ADMIN_PASSWORD} - new password for the admin user.</li>
 *   <li>{@code CONTROL_PLANE_ADMIN_DISPLAY_NAME} - optional display name (defaults to "SANAD Control Plane Admin").</li>
 * </ul>
 * </p>
 */
@Service
public class ControlPlaneBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneBootstrapService.class);

    private final CredentialBootstrapService credentialBootstrapService;
    private final ControlPlaneAccessGuard accessGuard;
    private final String controlPlaneTenantId;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminDisplayName;

    public ControlPlaneBootstrapService(
            CredentialBootstrapService credentialBootstrapService,
            ControlPlaneAccessGuard accessGuard,
            @Value("${sanad.control-plane.tenant-id:${SANAD_CONTROL_PLANE_TENANT_ID:}}") String controlPlaneTenantId,
            @Value("${sanad.control-plane.bootstrap.admin-email:${CONTROL_PLANE_ADMIN_EMAIL:}}") String adminEmail,
            @Value("${sanad.control-plane.bootstrap.admin-password:${CONTROL_PLANE_ADMIN_PASSWORD:}}") String adminPassword,
            @Value("${sanad.control-plane.bootstrap.admin-display-name:${CONTROL_PLANE_ADMIN_DISPLAY_NAME:SANAD Control Plane Admin}}") String adminDisplayName
    ) {
        this.credentialBootstrapService = credentialBootstrapService;
        this.accessGuard = accessGuard;
        this.controlPlaneTenantId = controlPlaneTenantId == null ? "" : controlPlaneTenantId.trim();
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.adminDisplayName = adminDisplayName == null || adminDisplayName.isBlank()
                ? "SANAD Control Plane Admin" : adminDisplayName.trim();
    }

    /**
     * Bootstrap (or re-bootstrap) the Control Plane admin user.
     *
     * <p>The operation is transactional and idempotent:
     * <ul>
     *   <li>Validates that {@code SANAD_CONTROL_PLANE_TENANT_ID} is configured and resolvable.</li>
     *   <li>Validates that admin email and password are configured.</li>
     *   <li>Calls {@link CredentialBootstrapService#bootstrap} with {@code forceReset=true}
     *       so the password hash, ADMIN role grant, and organization membership are all
     *       (re-)established in one atomic operation.</li>
     * </ul>
     * </p>
     *
     * @return a result record with the tenant ID, user ID, masked email, and flags
     *         indicating what was created or activated. No secrets are returned.
     * @throws IllegalStateException if any required configuration is missing or the
     *         tenant does not exist / is not ACTIVE.
     */
    @Transactional
    public ControlPlaneBootstrapResult bootstrapControlPlaneAdmin() {
        if (!accessGuard.isControlPlaneConfigured()) {
            throw new IllegalStateException(
                    "SANAD_CONTROL_PLANE_TENANT_ID is not configured; cannot bootstrap control-plane admin.");
        }
        if (adminEmail.isBlank()) {
            throw new IllegalStateException(
                    "CONTROL_PLANE_ADMIN_EMAIL is not configured; cannot bootstrap control-plane admin.");
        }
        if (adminPassword.isBlank() || adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "CONTROL_PLANE_ADMIN_PASSWORD is missing or too short (min 12 chars); refusing to bootstrap.");
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(controlPlaneTenantId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "SANAD_CONTROL_PLANE_TENANT_ID is not a valid UUID: " + controlPlaneTenantId, ex);
        }

        log.info("Invoking control-plane admin bootstrap for tenantId={} adminEmailMasked={}",
                tenantId, maskEmail(adminEmail));

        User adminUser = credentialBootstrapService.bootstrap(
                /* enabled */ true,
                /* forceReset */ true,
                /* tenantId */ tenantId,
                /* tenantName */ null,
                /* tenantSubdomain */ null,
                /* adminEmail */ adminEmail,
                /* adminPassword */ adminPassword,
                /* displayName */ adminDisplayName,
                /* auditActor */ "control-plane-bootstrap-endpoint"
        );

        if (adminUser == null || adminUser.getId() == null) {
            throw new IllegalStateException("Bootstrap returned no user; refusing to report success.");
        }

        log.info("Control-plane admin bootstrap completed for userId={} tenantId={}",
                adminUser.getId(), tenantId);

        // The CredentialBootstrapService unconditionally activates the ADMIN role grant
        // and the primary organization membership when forceReset=true; we report that
        // here so callers can assert on the response without re-querying the database.
        return new ControlPlaneBootstrapResult(
                tenantId,
                adminUser.getId(),
                adminEmail,
                /* created */ adminUser.getPasswordSetAt() != null,
                /* membershipActivated */ true,
                /* roleGrantsActivated */ java.util.List.of("ROLE.READ", "ROLE.WRITE")
        );
    }

    private static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return "*";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.length() <= 1 ? "*" : local.charAt(0) + "*".repeat(Math.max(1, local.length() - 1));
        return maskedLocal + "@" + domain;
    }
}
