package com.antoniosifov.popcornpalace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts disposable PostgreSQL and Redis containers for tests and points the
 * application at them, so tests run against the same engines as production.
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

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer()
    {
        return new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    }
}
