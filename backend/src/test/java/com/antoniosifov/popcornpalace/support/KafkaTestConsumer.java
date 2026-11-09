package com.antoniosifov.popcornpalace.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Reads what was actually published to Kafka, the way another service would.
 * Every call uses a new consumer group and reads the topic from the start, so
 * tests filter for their own messages instead of depending on each other's offsets.
 */
public final class KafkaTestConsumer
{
    private KafkaTestConsumer()
    {
    }

    /**
     * Collects the messages that match, in the order Kafka delivered them, until
     * {@code expected} of them have arrived or the timeout passes.
     */
    public static List<ConsumerRecord<String, String>> read(String bootstrapServers, String topic,
                                                           Predicate<ConsumerRecord<String, String>> matching,
                                                           int expected, Duration timeout)
    {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            while (found.size() < expected && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (matching.test(record)) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }

    public static List<ConsumerRecord<String, String>> readByKey(String bootstrapServers, String topic, String key,
                                                                int expected, Duration timeout)
    {
        return read(bootstrapServers, topic, record -> key.equals(record.key()), expected, timeout);
    }

    /** Value of a header as text, or null if the message does not have it. */
    public static String header(ConsumerRecord<String, String> record, String name)
    {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
