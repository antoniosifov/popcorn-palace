package com.antoniosifov.popcornpalace.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Sends ticket and cancellation emails. It knows nothing about the booking
 * API except the events it publishes to Kafka: the two services share no
 * database and make no calls to each other, so either can be down, restarted
 * or redeployed without the other noticing.
 */
@SpringBootApplication
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationsApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(NotificationsApplication.class, args);
    }
}
