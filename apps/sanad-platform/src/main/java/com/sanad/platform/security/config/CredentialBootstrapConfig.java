package com.sanad.platform.security.config;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Credential Bootstrap configuration.
 *
 * <p>On first startup (when no users with passwords exist), this runner
 * creates a bootstrap admin user with a password from environment variables.</p>
 *
 * <p><strong>Security:</strong> The bootstrap password is read from
 * {@code BOOTSTRAP_ADMIN_PASSWORD} env var. If not set, no bootstrap
 * user is created. The password is NEVER logged.</p>
 *
 * <p>This runner only executes in the {@code prod} and {@code local} profiles.
 * It is NOT active in test profiles (to avoid interfering with test data).</p>
 *
 * <p>The runner is idempotent: if a user with the bootstrap email already
 * exists and has a password, it does nothing.</p>
 */
@Configuration
@Profile({"prod", "local"})
public class CredentialBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(CredentialBootstrapConfig.class);

    @Value("${sanad.security.bootstrap.admin-email:admin@sanad.local}")
    private String bootstrapEmail;

    @Value("${sanad.security.bootstrap.admin-password:}")
    private String bootstrapPassword;

    @Value("${sanad.security.bootstrap.admin-display-name:SANAD Admin}")
    private String bootstrapDisplayName;

    @Value("${sanad.security.bootstrap.tenant-name:SANAD Default Tenant}")
    private String bootstrapTenantName;

    @Value("${sanad.security.bootstrap.tenant-subdomain:sanad-default}")
    private String bootstrapTenantSubdomain;

    @Bean
    public CommandLineRunner credentialBootstrap(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
                log.info("Bootstrap: BOOTSTRAP_ADMIN_PASSWORD not set — skipping admin user creation.");
                return;
            }

            // Check if any user already has a password (skip bootstrap if so)
            long usersWithPassword = userRepository.findAll().stream()
                    .filter(u -> u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                    .count();
            if (usersWithPassword > 0) {
                log.debug("Bootstrap: {} user(s) already have passwords — skipping.", usersWithPassword);
                return;
            }

            // Create or find the default tenant
            Tenant tenant = tenantRepository.findBySubdomain(bootstrapTenantSubdomain)
                    .orElseGet(() -> {
                        Tenant newTenant = new Tenant(bootstrapTenantName, bootstrapTenantSubdomain, TenantStatus.ACTIVE);
                        return tenantRepository.save(newTenant);
                    });

            // Check if the admin user already exists
            Optional<User> existingUser = userRepository.findByTenantIdAndEmail(tenant.getId(), bootstrapEmail);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                    // Set the password on the existing user
                    user.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                    user.setStatus(UserStatus.ACTIVE);
                    user.setLastLoginAt(null);
                    userRepository.save(user);
                    log.info("Bootstrap: set password for existing admin user (email={}, tenantId={})",
                            bootstrapEmail, tenant.getId());
                } else {
                    log.debug("Bootstrap: admin user already has a password — skipping.");
                }
                return;
            }

            // Create the admin user
            User adminUser = new User(tenant.getId(), bootstrapEmail, bootstrapDisplayName, UserStatus.ACTIVE);
            adminUser.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
            userRepository.save(adminUser);

            // Create or find the ADMIN role for this tenant
            String adminRoleCode = "ADMIN";
            Role adminRole = roleRepository.findByTenantIdAndCode(tenant.getId(), adminRoleCode)
                    .orElseGet(() -> {
                        Role newRole = new Role(tenant.getId(), adminRoleCode, "Administrator",
                                "Full administrative access (bootstrap-created)");
                        return roleRepository.save(newRole);
                    });

            // Grant the ADMIN role to the bootstrap user (tenant-wide, no org scope)
            boolean alreadyGranted = !userRoleGrantRepository
                    .findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                            tenant.getId(), adminUser.getId(), adminRole.getId()
                    ).isEmpty();
            if (!alreadyGranted) {
                UserRoleGrant grant = new UserRoleGrant(
                        tenant.getId(), adminUser.getId(), adminRole.getId(), null
                );
                userRoleGrantRepository.save(grant);
                log.info("Bootstrap: granted ADMIN role to user (userId={}, tenantId={})",
                        adminUser.getId(), tenant.getId());
            }

            log.info("Bootstrap: created admin user (email={}, tenantId={}) — change the password after first login.",
                    bootstrapEmail, tenant.getId());
        };
    }
}
