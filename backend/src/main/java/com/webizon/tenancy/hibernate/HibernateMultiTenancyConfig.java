package com.webizon.tenancy.hibernate;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires our multi-tenancy SPI implementations into Hibernate.
 *
 * <p>We deliberately do NOT set {@code hibernate.multiTenancy=DISCRIMINATOR}
 * via {@code application.yml} — the connection-provider + resolver beans must
 * be available before Spring Boot auto-configuration builds the EntityManager,
 * so we register them through {@link HibernatePropertiesCustomizer} which is
 * invoked at the right moment in the startup sequence.
 */
@Configuration
public class HibernateMultiTenancyConfig implements HibernatePropertiesCustomizer {

    private final WebizonMultiTenantConnectionProvider connectionProvider;
    private final WebizonCurrentTenantIdentifierResolver tenantResolver;

    public HibernateMultiTenancyConfig(
            WebizonMultiTenantConnectionProvider connectionProvider,
            WebizonCurrentTenantIdentifierResolver tenantResolver) {
        this.connectionProvider = connectionProvider;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void customize(Map<String, Object> properties) {
        properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    }
}
