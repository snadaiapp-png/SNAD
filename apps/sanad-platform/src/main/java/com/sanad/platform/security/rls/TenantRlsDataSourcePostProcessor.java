package com.sanad.platform.security.rls;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.stereotype.Component;

/**
 * {@link BeanPostProcessor} that transparently wraps the auto-configured
 * {@link DataSource} (typically a HikariCP pool) with a
 * {@link TenantRlsDataSource}.
 *
 * <p>This activates PostgreSQL Row-Level Security as a defense-in-depth layer:
 * every connection handed out by the pool is proxied so that the HMAC-SIGNED
 * tenant context is bound before the first statement inside a transaction
 * ({@code app_tenant_ctx_slot} → HMAC mint → {@code app_bind_tenant_ctx} —
 * V20261124_1 contract), ensuring RLS policies enforce tenant isolation at
 * the database level against the HMAC-validated value.</p>
 *
 * <h3>Activation</h3>
 * <ul>
 *   <li>Controlled by {@code snad.rls.enabled} (default: {@code true}).</li>
 *   <li>Set {@code snad.rls.enabled=false} to disable in specific environments.</li>
 * </ul>
 *
 * <h3>Safety</h3>
 * <p>Since V20261124_2 the RLS policies fail closed on sessions without a
 * signed tenant context, so this proxy no longer relies on
 * permissive-when-unset policies. Inside an active transaction, statements
 * without an authenticated tenant are rejected loudly by the handler unless
 * the path is explicitly classified via {@link TenantRlsPreAuthScope}
 * (login, token issuance, session bootstrap); non-transactional and
 * background paths remain transparent pass-throughs. The strict behavior is
 * governed by {@link TenantRlsSecurityConfig} (F-INT-06): on the production
 * path it can only be disabled with the full governance triple; the raw
 * system-property override was removed.</p>
 */
@Component
@ConditionalOnProperty(name = "snad.rls.enabled", havingValue = "true", matchIfMissing = true)
public class TenantRlsDataSourcePostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsDataSourcePostProcessor.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Only wrap the primary DataSource — avoid wrapping test/embedded
        // datasources or other unrelated beans that happen to implement the
        // interface.
        if (bean instanceof DataSource ds && !(bean instanceof TenantRlsDataSource)) {
            log.info("CRM-018: Wrapping DataSource '{}' with TenantRlsDataSource (RLS defense-in-depth)", beanName);
            return new TenantRlsDataSource(asAbstractDataSource(ds));
        }
        return bean;
    }

    /**
     * Coerce any {@link DataSource} into an {@link AbstractDataSource} so it
     * can be composed by {@link TenantRlsDataSource}.
     *
     * <p>HikariCP's {@code HikariDataSource} extends {@code AbstractDataSource},
     * so this cast succeeds in the standard auto-configured setup. For the
     * unlikely case of a non-{@code AbstractDataSource} implementation, we
     * fall back to an adapter.</p>
     */
    private static AbstractDataSource asAbstractDataSource(DataSource ds) {
        if (ds instanceof AbstractDataSource ads) {
            return ads;
        }
        // Defensive adapter for non-standard DataSource implementations.
        return new AbstractDataSource() {
            @Override
            public java.sql.Connection getConnection() throws java.sql.SQLException {
                return ds.getConnection();
            }

            @Override
            public java.sql.Connection getConnection(String username, String password) throws java.sql.SQLException {
                return ds.getConnection(username, password);
            }
        };
    }
}
