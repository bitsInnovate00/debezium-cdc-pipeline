package com.debezium.ignite;

import org.apache.ignite.client.IgniteClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

/**
 * Kafka consumer that reads from Debezium CDC topics and writes to Apache Ignite 3 cache
 */
public class IgniteKafkaConsumer {
    
    private final KafkaConsumer<String, String> consumer;
    private final IgniteClient igniteClient;
    private final ObjectMapper objectMapper;
    private final String tableName;
    
    public IgniteKafkaConsumer(String bootstrapServers, String groupId, 
                               String topic, IgniteClient igniteClient) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        this.igniteClient = igniteClient;
        this.objectMapper = new ObjectMapper();
        this.tableName = extractTableName(topic);
    }
    
    private String extractTableName(String topic) {
        // Topic format: dbserver1.public.customers
        String[] parts = topic.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : "unknown";
    }
    
    public void start() {
        System.out.println("Starting Kafka consumer for Ignite 3...");
        
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                
                for (ConsumerRecord<String, String> record : records) {
                    processRecord(record);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in consumer: " + e.getMessage());
            e.printStackTrace();
        } finally {
            consumer.close();
            igniteClient.close();
        }
    }
    
    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            System.out.println("Processing record: " + record.key());
            
            JsonNode jsonNode = objectMapper.readTree(record.value());
            
            // Extract operation type and payload
            String operation = jsonNode.has("op") ? jsonNode.get("op").asText() : null;
            JsonNode after = jsonNode.has("after") ? jsonNode.get("after") : null;
            JsonNode before = jsonNode.has("before") ? jsonNode.get("before") : null;
            
            if (operation == null) {
                // For unwrapped records, treat as insert/update
                writeToIgnite(jsonNode);
            } else {
                switch (operation) {
                    case "c": // Create
                    case "u": // Update
                        if (after != null) {
                            writeToIgnite(after);
                        }
                        break;
                    case "d": // Delete
                        if (before != null) {
                            deleteFromIgnite(before);
                        }
                        break;
                    case "r": // Read (snapshot)
                        if (after != null) {
                            writeToIgnite(after);
                        }
                        break;
                    default:
                        System.out.println("Unknown operation: " + operation);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing record: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void writeToIgnite(JsonNode data) {
        try {
            // Extract ID and create SQL insert/update
            if (data.has("id")) {
                int id = data.get("id").asInt();
                String name = data.has("name") ? data.get("name").asText() : "";
                String email = data.has("email") ? data.get("email").asText() : "";
                
                // Use Ignite 3 SQL API to insert/update
                String sql = String.format(
                    "MERGE INTO %s (id, name, email) VALUES (?, ?, ?)",
                    tableName
                );
                
                igniteClient.sql().execute(null, sql, id, name, email);
                System.out.println("Written to Ignite: " + id);
            }
        } catch (Exception e) {
            System.err.println("Error writing to Ignite: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void deleteFromIgnite(JsonNode data) {
        try {
            if (data.has("id")) {
                int id = data.get("id").asInt();
                
                String sql = String.format("DELETE FROM %s WHERE id = ?", tableName);
                igniteClient.sql().execute(null, sql, id);
                System.out.println("Deleted from Ignite: " + id);
            }
        } catch (Exception e) {
            System.err.println("Error deleting from Ignite: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        String kafkaBootstrapServers = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka-service:9092"
        );
        String kafkaTopic = System.getenv().getOrDefault(
            "KAFKA_TOPIC", "dbserver1.public.customers"
        );
        String kafkaGroupId = System.getenv().getOrDefault(
            "KAFKA_GROUP_ID", "ignite-consumer-group"
        );
        String igniteAddress = System.getenv().getOrDefault(
            "IGNITE_ADDRESS", "ignite-service:10800"
        );
        
        try {
            // Create Ignite client
            IgniteClient igniteClient = IgniteClient.builder()
                .addresses(igniteAddress)
                .build();
            
            // Initialize table if needed
            initializeIgniteTable(igniteClient);
            
            // Start consumer
            IgniteKafkaConsumer consumer = new IgniteKafkaConsumer(
                kafkaBootstrapServers, kafkaGroupId, kafkaTopic, igniteClient
            );
            consumer.start();
            
        } catch (Exception e) {
            System.err.println("Failed to start consumer: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void initializeIgniteTable(IgniteClient client) {
        try {
            String createTableSql = 
                "CREATE TABLE IF NOT EXISTS customers (" +
                "  id INT PRIMARY KEY," +
                "  name VARCHAR," +
                "  email VARCHAR" +
                ")";
            
            client.sql().execute(null, createTableSql);
            System.out.println("Ignite table initialized");
        } catch (Exception e) {
            System.err.println("Error initializing table: " + e.getMessage());
        }
    }
}
