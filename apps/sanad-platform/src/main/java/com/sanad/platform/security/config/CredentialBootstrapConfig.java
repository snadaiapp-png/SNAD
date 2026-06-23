package com.sanad.platform.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

/**
 * Wires explicit one-time administrative enrollment into startup.
 *
 * <p>Supports two tenant-resolution modes:</p>
 * <ol>
 *   <li><b>Explicit tenant-id</b> — provide {@code sanad.security.bootstrap.tenant-id}
 *       (env: {@code BOOTSTRAP_TENANT_ID} or {@code SANAD_SECURITY_BOOTSTRAP_TENANT_ID})
 *       as a UUID of an existing ACTIVE tenant.</li>
 *   <li><b>Auto-create tenant</b> — provide {@code tenant-name} + {@code tenant-subdomain}
 *       (env: {@code BOOTSTRAP_TENANT_NAME}/{@code BOOTSTRAP_TENANT_SUBDOMAIN} or the
 *       {@code SANAD_SECURITY_BOOTSTRAP_*} relaxed-binding equivalents). The bootstrap
 *       service will look up the tenant by subdomain and create it if absent.</li>
 * </ol>
 *
 * <p>Admin credential is read from {@code sanad.security.bootstrap.admin-password}
 * (env: {@code BOOTSTRAP_ADMIN_PASSWORD} or {@code SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD}).</p>
 */
@Configuration
@Profile({"prod", "local"})
public class CredentialBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(CredentialBootstrapConfig.class);

    @Value("${sanad.security.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${sanad.security.bootstrap.force-reset:false}")
    private boolean forceReset;

    @Value("${sanad.security.bootstrap.tenant-id:}")
    private String tenantIdValue;

    @Value("${sanad.security.bootstrap.tenant-name:}")
    private String tenantName;

    @Value("${sanad.security.bootstrap.tenant-subdomain:}")
    private String tenantSubdomain;

    @Value("${sanad.security.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${sanad.security.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${sanad.security.bootstrap.admin-display-name:SANAD Admin}")
    private String adminDisplayName;

    @Value("${sanad.security.bootstrap.audit-actor:credential-bootstrap}")
    private String auditActorValue;

    @Bean
    public CommandLineRunner credentialBootstrap(CredentialBootstrapService service) {
        return args -> {
            UUID tenantId = CredentialBootstrapPolicy.resolveTenantId(
                    enabled, tenantIdValue, tenantName, tenantSubdomain);
            CredentialBootstrapPolicy.requireAdminInput(enabled, adminEmail, adminPassword);
            String auditActor = CredentialBootstrapPolicy.requireAuditActor(enabled, auditActorValue);

            if (!enabled) {
                log.info("Credential bootstrap disabled by configuration.");
                return;
            }

            service.bootstrap(
                    true,
                    forceReset,
                    tenantId,
                    tenantName,
                    tenantSubdomain,
                    adminEmail,
                    adminPassword,
                    adminDisplayName,
                    auditActor);
        };
    }
}
