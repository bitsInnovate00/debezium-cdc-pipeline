package com.debezium.regression.baseline;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable copy of a {@link com.debezium.regression.model.CdcEvent} belonging to
 * an approved (or pending) {@link com.debezium.regression.model.Baseline}.
 *
 * <p>Baseline events are separate from live session events so that baselines remain
 * stable even after session data is pruned.
 */
@Entity
@Table(name = "baseline_events",
       indexes = {
           @Index(name = "idx_be_baseline_id", columnList = "baseline_id"),
           @Index(name = "idx_be_table_name", columnList = "table_name"),
           @Index(name = "idx_be_primary_key", columnList = "primary_key")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baseline_id", nullable = false)
    private UUID baselineId;

    @Column(name = "kafka_topic", nullable = false)
    private String kafkaTopic;

    @Column(name = "kafka_partition")
    private int kafkaPartition;

    @Column(name = "kafka_offset")
    private long kafkaOffset;

    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    @Column(name = "operation", nullable = false, length = 1)
    private String operation;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "primary_key", columnDefinition = "TEXT")
    private String primaryKey;

    @Column(name = "before_image", columnDefinition = "TEXT")
    private String beforeImage;

    @Column(name = "after_image", columnDefinition = "TEXT")
    private String afterImage;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "schema_version")
    private String schemaVersion;

    /** Copies a live CdcEvent into a BaselineEvent for the given baseline. */
    public static BaselineEvent from(com.debezium.regression.model.CdcEvent event, UUID baselineId) {
        return BaselineEvent.builder()
                .baselineId(baselineId)
                .kafkaTopic(event.getKafkaTopic())
                .kafkaPartition(event.getKafkaPartition())
                .kafkaOffset(event.getKafkaOffset())
                .eventTimestamp(event.getEventTimestamp())
                .operation(event.getOperation())
                .tableName(event.getTableName())
                .primaryKey(event.getPrimaryKey())
                .beforeImage(event.getBeforeImage())
                .afterImage(event.getAfterImage())
                .sequenceNumber(event.getSequenceNumber())
                .schemaVersion(event.getSchemaVersion())
                .build();
    }
}
