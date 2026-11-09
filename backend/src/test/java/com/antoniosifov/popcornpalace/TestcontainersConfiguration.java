package com.antoniosifov.popcornpalace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts a disposable PostgreSQL container for tests and points the application's
 * datasource at it, so tests run against the same database engine as production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration
{
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer()
    {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
