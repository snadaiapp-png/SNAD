package com.sanad.platform.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

/** Wires explicit one-time administrative enrollment into startup. */
@Configuration
@Profile({"prod", "local"})
public class CredentialBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(CredentialBootstrapConfig.class);

    @Value("${sanad.security.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${sanad.security.bootstrap.tenant-id:}")
    private String tenantIdValue;

    @Value("${sanad.security.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${sanad.security.bootstrap.admin-credential:}")
    private String adminCredential;

    @Value("${sanad.security.bootstrap.admin-display-name:SANAD Admin}")
    private String adminDisplayName;

    @Value("${sanad.security.bootstrap.audit-actor:credential-bootstrap}")
    private String auditActorValue;

    @Bean
    public CommandLineRunner credentialBootstrap(CredentialBootstrapService service) {
        return args -> {
            UUID tenantId = CredentialBootstrapPolicy.requireTenantId(enabled, tenantIdValue);
            CredentialBootstrapPolicy.requireAdminInput(enabled, adminEmail, adminCredential);
            String auditActor = CredentialBootstrapPolicy.requireAuditActor(enabled, auditActorValue);

            if (!enabled) {
                log.info("Credential bootstrap disabled by configuration.");
                return;
            }

            service.bootstrap(
                    true,
                    tenantId,
                    adminEmail,
                    adminCredential,
                    adminDisplayName,
                    auditActor);
        };
    }
}
