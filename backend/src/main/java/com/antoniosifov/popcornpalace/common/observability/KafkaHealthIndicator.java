package com.antoniosifov.popcornpalace.common.observability;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Whether the Kafka cluster answers. Spring Boot ships health indicators for
 * the database and Redis but none for Kafka, so this one asks the cluster to
 * describe itself.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator
{
    private static final int TIMEOUT_MS = 2000;

    private final Admin admin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin)
    {
        // Connects lazily and reconnects on its own, so one client serves every check.
        this.admin = Admin.create(kafkaAdmin.getConfigurationProperties());
    }

    @Override
    public Health health()
    {
        try {
            DescribeClusterResult cluster = admin.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS));
            int nodes = cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            String clusterId = cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return Health.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes).build();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Health.down(exception).build();
        }
        catch (ExecutionException | TimeoutException | RuntimeException exception) {
            return Health.down(exception).build();
        }
    }

    @PreDestroy
    void close()
    {
        admin.close(Duration.ofSeconds(2));
    }
}
