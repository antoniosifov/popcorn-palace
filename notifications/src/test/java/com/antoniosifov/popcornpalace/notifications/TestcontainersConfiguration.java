package com.antoniosifov.popcornpalace.notifications;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Starts disposable Kafka and Redis containers for tests and points the service at them. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration
{
    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer()
    {
        return new KafkaContainer("apache/kafka:4.2.2");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer()
    {
        return new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    }
}
