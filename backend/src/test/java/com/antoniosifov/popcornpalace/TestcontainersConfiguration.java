package com.antoniosifov.popcornpalace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts disposable PostgreSQL, Redis and Kafka containers for tests and points
 * the application at them, so tests run against the same engines as production.
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

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer()
    {
        return new KafkaContainer("apache/kafka:4.2.2");
    }
}
