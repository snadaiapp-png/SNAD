package com.sanad.platform.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing for the entire application context.
 *
 * <p>This activates the {@link org.springframework.data.jpa.domain.support.AuditingEntityListener}
 * which is referenced by entities via {@code @EntityListeners}. Without this
 * configuration, the {@code @CreatedDate} and {@code @LastModifiedDate}
 * annotations on entity fields would not be populated automatically.</p>
 *
 * <p>Placed in {@code com.sanad.platform.shared.config} because auditing is a
 * cross-cutting concern that affects every persistent entity in every
 * bounded context, not just the Tenant context.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
