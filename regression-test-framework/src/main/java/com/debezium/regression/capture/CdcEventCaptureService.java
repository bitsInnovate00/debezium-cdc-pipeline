package com.debezium.regression.capture;

import com.debezium.regression.model.CdcEvent;
import com.debezium.regression.model.TestSession;
import com.debezium.regression.session.TestSessionManager;
import com.debezium.regression.session.TestSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures CDC events from Kafka for a completed test session.
 *
 * <p>After a session ends the capture service reads all Kafka records between
 * the session's start and end offsets, parses the Debezium envelope, and
 * persists each event to the {@link CdcEventRepository}.
 *
 * <p>Tables listed in {@link CaptureConfig#getExcludedTables()} are skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdcEventCaptureService {

    private final KafkaConsumer<String, String> captureConsumer;
    private final CdcEventRepository eventRepository;
    private final TestSessionRepository sessionRepository;
    private final TestSessionManager sessionManager;
    private final CaptureConfig captureConfig;
    private final ObjectMapper objectMapper;

    /**
     * Asynchronously captures all CDC events within the session's Kafka offset window.
     *
     * @param session the session whose window to capture (must be in CAPTURING status)
     */
    @Async
    public void captureEventsForSession(TestSession session) {
        log.info("Starting CDC event capture for session {}", session.getId());
        try {
            List<CdcEvent> events = readEventsFromKafka(session);
            eventRepository.saveAll(events);
            sessionManager.completeSession(session.getId());
            log.info("Captured {} events for session {}", events.size(), session.getId());
        } catch (Exception e) {
            log.error("CDC capture failed for session {}", session.getId(), e);
            sessionManager.failSession(session.getId(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private List<CdcEvent> readEventsFromKafka(TestSession session) {
        Map<String, Map<Integer, Long>> startOffsets = sessionManager.getStartOffsets(session);
        Map<String, Map<Integer, Long>> endOffsets   = sessionManager.getEndOffsets(session);

        List<CdcEvent> result = new ArrayList<>();
        AtomicLong seq = new AtomicLong(0);

        for (Map.Entry<String, Map<Integer, Long>> topicEntry : startOffsets.entrySet()) {
            String topic = topicEntry.getKey();
            Map<Integer, Long> partitionStartOffsets = topicEntry.getValue();
            Map<Integer, Long> partitionEndOffsets   = endOffsets.getOrDefault(topic, Collections.emptyMap());

            for (Map.Entry<Integer, Long> partEntry : partitionStartOffsets.entrySet()) {
                int  partition   = partEntry.getKey();
                long startOffset = partEntry.getValue();
                long endOffset   = partitionEndOffsets.getOrDefault(partition, startOffset);

                if (endOffset <= startOffset) continue;

                TopicPartition tp = new TopicPartition(topic, partition);
                captureConsumer.assign(Collections.singletonList(tp));
                captureConsumer.seek(tp, startOffset);

                while (true) {
                    ConsumerRecords<String, String> records =
                            captureConsumer.poll(Duration.ofSeconds(5));
                    if (records.isEmpty()) break;

                    for (ConsumerRecord<String, String> record : records) {
                        if (record.offset() >= endOffset) break;
                        CdcEvent event = parseRecord(record, session.getId(), seq.incrementAndGet());
                        if (event != null) result.add(event);
                    }

                    long position = captureConsumer.position(tp);
                    if (position >= endOffset) break;
                }
            }
        }
        return result;
    }

    private CdcEvent parseRecord(ConsumerRecord<String, String> record, UUID sessionId, long seq) {
        try {
            if (record.value() == null) return null;

            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload  = envelope.has("payload") ? envelope.get("payload") : envelope;

            String operation = payload.has("op") ? payload.get("op").asText() : "r";
            String table     = extractTableName(record.topic(), payload);

            if (captureConfig.getExcludedTables().contains(table)) {
                log.debug("Skipping excluded table {}", table);
                return null;
            }

            JsonNode before = payload.has("before") ? payload.get("before") : null;
            JsonNode after  = payload.has("after")  ? payload.get("after")  : null;

            long eventTsMs = payload.has("ts_ms") ? payload.get("ts_ms").asLong(0) : 0;

            String primaryKey = extractPrimaryKey(after != null ? after : before,
                                                   captureConfig.getPrimaryKeyColumns(table));

            return CdcEvent.builder()
                    .sessionId(sessionId)
                    .kafkaTopic(record.topic())
                    .kafkaPartition(record.partition())
                    .kafkaOffset(record.offset())
                    .eventTimestamp(eventTsMs > 0 ? Instant.ofEpochMilli(eventTsMs) : null)
                    .operation(operation)
                    .tableName(table)
                    .primaryKey(primaryKey)
                    .beforeImage(before != null ? objectMapper.writeValueAsString(before) : null)
                    .afterImage(after  != null ? objectMapper.writeValueAsString(after)  : null)
                    .sequenceNumber(seq)
                    .schemaVersion(extractSchemaVersion(envelope))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse CDC record at offset {}: {}", record.offset(), e.getMessage());
            return null;
        }
    }

    private String extractTableName(String topic, JsonNode payload) {
        if (payload.has("source") && payload.get("source").has("table")) {
            String schema = payload.get("source").has("schema")
                    ? payload.get("source").get("schema").asText("public") : "public";
            return schema + "." + payload.get("source").get("table").asText();
        }
        // Fallback: derive from topic name (debezium.schema.table)
        String[] parts = topic.split("\\.");
        return parts.length >= 3 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : topic;
    }

    private String extractPrimaryKey(JsonNode row, List<String> pkColumns) {
        if (row == null || pkColumns == null || pkColumns.isEmpty()) return null;
        Map<String, Object> pk = new LinkedHashMap<>();
        for (String col : pkColumns) {
            if (row.has(col)) pk.put(col, row.get(col));
        }
        try {
            return objectMapper.writeValueAsString(pk);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSchemaVersion(JsonNode envelope) {
        if (envelope.has("schema") && envelope.get("schema").has("version")) {
            return envelope.get("schema").get("version").asText();
        }
        return null;
    }
}
