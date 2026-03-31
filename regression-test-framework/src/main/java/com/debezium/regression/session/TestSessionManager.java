package com.debezium.regression.session;

import com.debezium.regression.model.TestSession;
import com.debezium.regression.model.TestSession.Environment;
import com.debezium.regression.model.TestSession.SessionStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Manages the lifecycle of CDC capture sessions.
 *
 * <p>On {@link #startSession} the current end-offsets of all monitored Kafka topics
 * are recorded.  On {@link #endSession} the new end-offsets are recorded, forming a
 * precise window of Kafka records that belong to this session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestSessionManager {

    private final TestSessionRepository sessionRepository;
    private final KafkaConsumer<String, String> offsetProbeConsumer;
    private final ObjectMapper objectMapper;

    /**
     * Creates and starts a new named test session.
     *
     * @param testCaseName  human-readable test case identifier
     * @param environment   MONOLITH or MICROSERVICES
     * @param tags          optional metadata tags
     * @param commitSha     optional VCS commit SHA
     * @return the persisted {@link TestSession}
     */
    @Transactional
    public TestSession startSession(String testCaseName,
                                    Environment environment,
                                    Map<String, String> tags,
                                    String commitSha) {
        Map<String, Map<Integer, Long>> startOffsets = captureCurrentEndOffsets();

        TestSession session = TestSession.builder()
                .id(UUID.randomUUID())
                .testCaseName(testCaseName)
                .environment(environment)
                .status(SessionStatus.STARTED)
                .startedAt(Instant.now())
                .startOffsets(toJson(startOffsets))
                .tags(tags != null ? tags : new HashMap<>())
                .commitSha(commitSha)
                .build();

        session = sessionRepository.save(session);
        log.info("Started session {} for test case '{}' in environment {}",
                 session.getId(), testCaseName, environment);
        return session;
    }

    /**
     * Ends an active session and records the ending Kafka offsets.
     *
     * @param sessionId the UUID of the session to end
     * @return the updated {@link TestSession}
     */
    @Transactional
    public TestSession endSession(UUID sessionId) {
        TestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.STARTED) {
            throw new IllegalStateException("Cannot end session in status: " + session.getStatus());
        }

        Map<String, Map<Integer, Long>> endOffsets = captureCurrentEndOffsets();
        session.setEndOffsets(toJson(endOffsets));
        session.setEndedAt(Instant.now());
        session.setStatus(SessionStatus.CAPTURING);

        session = sessionRepository.save(session);
        log.info("Ended session {} — capture window recorded", sessionId);
        return session;
    }

    /**
     * Marks a session as COMPLETED after all CDC events have been captured.
     */
    @Transactional
    public void completeSession(UUID sessionId) {
        TestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);
        log.info("Session {} marked COMPLETED", sessionId);
    }

    /**
     * Marks a session as FAILED, e.g. when CDC capture encounters an unrecoverable error.
     */
    @Transactional
    public void failSession(UUID sessionId, String reason) {
        TestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setStatus(SessionStatus.FAILED);
        sessionRepository.save(session);
        log.error("Session {} FAILED: {}", sessionId, reason);
    }

    /** Returns the parsed start-offset map for a session. */
    public Map<String, Map<Integer, Long>> getStartOffsets(TestSession session) {
        return fromJson(session.getStartOffsets());
    }

    /** Returns the parsed end-offset map for a session. */
    public Map<String, Map<Integer, Long>> getEndOffsets(TestSession session) {
        return fromJson(session.getEndOffsets());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Map<String, Map<Integer, Long>> captureCurrentEndOffsets() {
        Map<String, Map<Integer, Long>> result = new LinkedHashMap<>();
        Set<TopicPartition> assignments = offsetProbeConsumer.assignment();
        offsetProbeConsumer.seekToEnd(assignments);
        for (TopicPartition tp : assignments) {
            result.computeIfAbsent(tp.topic(), k -> new LinkedHashMap<>())
                  .put(tp.partition(), offsetProbeConsumer.position(tp));
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise offsets", e);
        }
    }

    private Map<String, Map<Integer, Long>> fromJson(String json) {
        if (json == null) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialise offsets", e);
        }
    }
}
