package com.debezium.regression.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a bounded CDC capture session associated with a single test case execution.
 *
 * <p>A session records the Kafka topic offsets at start and end time, forming a precise
 * boundary for which CDC events belong to this test run.
 */
@Entity
@Table(name = "test_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Human-readable name, e.g. "book_domestic_flight_one_way" */
    @Column(name = "test_case_name", nullable = false)
    private String testCaseName;

    /** Phase this session belongs to: MONOLITH or MICROSERVICES */
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /**
     * JSON-serialised map of topic → partition → start offset recorded at session start.
     * Example: {"debezium.public.reservations": {"0": 142}}
     */
    @Column(name = "start_offsets", columnDefinition = "TEXT")
    private String startOffsets;

    /**
     * JSON-serialised map of topic → partition → end offset recorded at session end.
     */
    @Column(name = "end_offsets", columnDefinition = "TEXT")
    private String endOffsets;

    /** Arbitrary key=value tags, e.g. sprint=42, jira=AIRL-101 */
    @ElementCollection
    @CollectionTable(name = "session_tags", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /** Optional code commit SHA that triggered this session */
    @Column(name = "commit_sha")
    private String commitSha;

    public enum SessionStatus {
        STARTED, CAPTURING, COMPLETED, FAILED
    }

    public enum Environment {
        MONOLITH, MICROSERVICES
    }
}
