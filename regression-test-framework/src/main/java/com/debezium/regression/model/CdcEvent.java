package com.debezium.regression.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A single CDC event captured within a test session window.
 *
 * <p>Each event corresponds to one Debezium record from Kafka, representing
 * an INSERT, UPDATE, or DELETE on a monitored PostgreSQL table.
 */
@Entity
@Table(name = "cdc_events",
       indexes = {
           @Index(name = "idx_cdc_session_id", columnList = "session_id"),
           @Index(name = "idx_cdc_table_name", columnList = "table_name"),
           @Index(name = "idx_cdc_operation", columnList = "operation")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdcEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** Kafka topic name, e.g. "debezium.public.reservations" */
    @Column(name = "kafka_topic", nullable = false)
    private String kafkaTopic;

    @Column(name = "kafka_partition", nullable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false)
    private long kafkaOffset;

    /** Wall-clock timestamp from the Debezium event source block */
    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    /** Debezium operation: c=CREATE, u=UPDATE, d=DELETE, r=READ (snapshot) */
    @Column(name = "operation", nullable = false, length = 1)
    private String operation;

    /** Schema-qualified table name, e.g. "public.reservations" */
    @Column(name = "table_name", nullable = false)
    private String tableName;

    /** The primary key value(s) serialised as JSON, e.g. {"reservation_id": 42} */
    @Column(name = "primary_key", columnDefinition = "TEXT")
    private String primaryKey;

    /** Before-image JSON (null for INSERT/READ events) */
    @Column(name = "before_image", columnDefinition = "TEXT")
    private String beforeImage;

    /** After-image JSON (null for DELETE events) */
    @Column(name = "after_image", columnDefinition = "TEXT")
    private String afterImage;

    /** Sequence number within the session to preserve ordering */
    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    /** Schema version string from Debezium envelope, for schema evolution tracking */
    @Column(name = "schema_version")
    private String schemaVersion;
}
