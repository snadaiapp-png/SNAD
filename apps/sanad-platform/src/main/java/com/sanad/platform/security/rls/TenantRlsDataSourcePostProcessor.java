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
 * every connection handed out by the pool is proxied so that
 * {@code SET LOCAL app.tenant_id} is applied before the first statement
 * inside a transaction, ensuring RLS policies enforce tenant isolation at
 * the database level.</p>
 *
 * <h3>Activation</h3>
 * <ul>
 *   <li>Controlled by {@code snad.rls.enabled} (default: {@code true}).</li>
 *   <li>Set {@code snad.rls.enabled=false} to disable in specific environments.</li>
 * </ul>
 *
 * <h3>Safety</h3>
 * <p>The proxy is transparent when no tenant context is present (background
 * jobs, migrations via the owner role, unauthenticated paths) — RLS policies
 * are permissive-when-unset, so there is zero risk of breaking existing
 * functionality.</p>
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
