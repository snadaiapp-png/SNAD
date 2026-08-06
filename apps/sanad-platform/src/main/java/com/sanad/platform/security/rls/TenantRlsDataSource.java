package com.sanad.platform.security.rls;

import java.lang.reflect.Proxy;
import java.sql.Connection;

import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * {@link javax.sql.DataSource} decorator that wraps every borrowed connection
 * in a dynamic proxy backed by {@link TenantRlsConnectionHandler}.
 *
 * <p>The proxy transparently applies {@code SET LOCAL app.tenant_id} before
 * the first statement execution inside a transaction, activating PostgreSQL
 * Row-Level Security policies as a defense-in-depth layer on top of the
 * existing application-level tenant filtering.</p>
 *
 * <p>This class only wraps — it does not pool, close, or alter connection
 * lifecycle. HikariCP retains full ownership of pooling semantics.</p>
 */
public class TenantRlsDataSource extends AbstractDataSource {

    private final AbstractDataSource delegate;

    public TenantRlsDataSource(AbstractDataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws java.sql.SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws java.sql.SQLException {
        return wrap(delegate.getConnection(username, password));
    }

    private static Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new TenantRlsConnectionHandler(real));
    }
}
