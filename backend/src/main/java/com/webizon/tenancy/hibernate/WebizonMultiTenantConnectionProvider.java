package com.webizon.tenancy.hibernate;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Hibernate multi-tenancy SPI implementation for Webizon.
 *
 * <p>We run <em>one physical database with discriminator columns</em> (see
 * ADR-0002), so this provider:
 * <ul>
 *   <li>always returns a connection from the single {@link DataSource}</li>
 *   <li>on checkout, runs {@code SET app.current_tenant = '<uuid>'} so the
 *       row-level security policies in {@code V001__core_tenancy.sql} are
 *       effective for the lifetime of that connection</li>
 *   <li>on release, runs {@code RESET app.current_tenant} so the next consumer
 *       of the pooled connection cannot accidentally inherit a stale tenant</li>
 * </ul>
 *
 * <p>The {@link #DEFAULT_TENANT} constant is a special sentinel used by
 * operations that intentionally span tenants (bootstrap, billing reconciliation).
 * When the resolver returns it we skip the SET entirely and the RLS policies
 * deny all tenant-scoped reads — a deliberate fail-closed posture.
 */
@Component
@Slf4j
public class WebizonMultiTenantConnectionProvider extends AbstractMultiTenantConnectionProvider<Object> {

    /** Sentinel that tells Hibernate "no tenant in scope; global tables only". */
    public static final String DEFAULT_TENANT = "__webizon_default__";

    private final DataSource dataSource;
    private final SingleConnectionProviderAdapter adapter;

    public WebizonMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
        this.adapter = new SingleConnectionProviderAdapter(dataSource);
    }

    @Override
    protected ConnectionProvider getAnyConnectionProvider() {
        return adapter;
    }

    @Override
    protected ConnectionProvider selectConnectionProvider(Object tenantIdentifier) {
        return adapter;
    }

    @Override
    public Connection getConnection(Object tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (tenantIdentifier == null || DEFAULT_TENANT.equals(tenantIdentifier)) {
            // Global-scope work (bootstrap, billing reconciliation, health checks).
            // RLS policies will refuse tenant-scoped SELECTs, which is what we want.
            return connection;
        }

        UUID tenantUuid = coerceToUuid(tenantIdentifier);
        try (Statement st = connection.createStatement()) {
            // Intentionally NOT using SET LOCAL — this is a pooled connection, not
            // a transaction scope. We rely on releaseConnection() to RESET the
            // variable when the connection goes back to the pool.
            st.execute("SET app.current_tenant = '" + tenantUuid + "'");
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
        return connection;
    }

    @Override
    public void releaseConnection(Object tenantIdentifier, Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("RESET app.current_tenant");
        } catch (SQLException ex) {
            log.warn("Failed to RESET app.current_tenant before returning to pool", ex);
        } finally {
            connection.close();
        }
    }

    private static UUID coerceToUuid(Object tenantIdentifier) {
        if (tenantIdentifier instanceof UUID uuid) return uuid;
        return UUID.fromString(tenantIdentifier.toString());
    }

    /**
     * Tiny adapter that presents the shared {@link DataSource} as a
     * {@link ConnectionProvider} — required by the SPI contract even though
     * we never route to more than one physical pool.
     */
    private static final class SingleConnectionProviderAdapter implements ConnectionProvider {
        private final DataSource dataSource;

        SingleConnectionProviderAdapter(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override public Connection getConnection() throws SQLException { return dataSource.getConnection(); }
        @Override public void closeConnection(Connection conn) throws SQLException { conn.close(); }
        @Override public boolean supportsAggressiveRelease() { return false; }
        @Override public boolean isUnwrappableAs(Class<?> unwrapType) { return false; }
        @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
    }
}
