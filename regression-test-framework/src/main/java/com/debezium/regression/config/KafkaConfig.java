package com.debezium.regression.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Spring configuration for Kafka consumers used by the regression test framework.
 *
 * <p>Two distinct {@link KafkaConsumer} beans are created:
 * <ul>
 *   <li><b>offsetProbeConsumer</b> — used by {@link com.debezium.regression.session.TestSessionManager}
 *       to snapshot current end-offsets at session start/end.</li>
 *   <li><b>captureConsumer</b>    — used by {@link com.debezium.regression.capture.CdcEventCaptureService}
 *       to replay events within a session window.</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${cdc.kafka.topics:debezium.public.reservations,debezium.public.flights,debezium.public.tickets,debezium.public.payments,debezium.public.passengers,debezium.public.boarding_passes,debezium.public.seat_assignments}")
    private String topics;

    /**
     * Kafka consumer used solely for offset probing (seek to end, read position).
     * Assigned manually — no automatic partition assignment.
     */
    @Bean
    public KafkaConsumer<String, String> offsetProbeConsumer() {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(baseProperties("regression-offset-probe"));
        List<org.apache.kafka.common.TopicPartition> partitions = resolvePartitions(consumer);
        consumer.assign(partitions);
        return consumer;
    }

    /**
     * Kafka consumer used for replaying events within a session window.
     * Partitions are assigned dynamically per capture run.
     */
    @Bean
    public KafkaConsumer<String, String> captureConsumer() {
        return new KafkaConsumer<>(baseProperties("regression-capture"));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Properties baseProperties(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        return props;
    }

    private List<org.apache.kafka.common.TopicPartition> resolvePartitions(KafkaConsumer<String, String> consumer) {
        List<org.apache.kafka.common.TopicPartition> result = new ArrayList<>();
        for (String topic : topics.split(",")) {
            topic = topic.trim();
            var partitionInfos = consumer.partitionsFor(topic);
            if (partitionInfos != null) {
                for (var pi : partitionInfos) {
                    result.add(new org.apache.kafka.common.TopicPartition(pi.topic(), pi.partition()));
                }
            }
        }
        return result;
    }
}
