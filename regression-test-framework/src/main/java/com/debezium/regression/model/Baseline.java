package com.debezium.regression.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable approved snapshot of CDC events for a named test case.
 *
 * <p>A Baseline is created from a {@link TestSession} once QA has reviewed and
 * approved the captured CDC events.  Subsequent runs of the same test case are
 * compared against the latest approved Baseline.
 */
@Entity
@Table(name = "baselines",
       indexes = {
           @Index(name = "idx_baseline_test_case", columnList = "test_case_name"),
           @Index(name = "idx_baseline_status", columnList = "status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Baseline {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The test case this baseline represents */
    @Column(name = "test_case_name", nullable = false)
    private String testCaseName;

    /** Monotonically increasing version within the same test case */
    @Column(name = "version", nullable = false)
    private int version;

    /** Source session that generated this baseline */
    @Column(name = "source_session_id")
    private UUID sourceSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BaselineStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    /** Code commit SHA that produced this baseline */
    @Column(name = "commit_sha")
    private String commitSha;

    /** Total number of CDC events stored in this baseline */
    @Column(name = "event_count", nullable = false)
    private int eventCount;

    /** JSON summary of tables touched and operation counts */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Optional human-readable notes from the approver */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public enum BaselineStatus {
        PENDING, APPROVED, REJECTED, SUPERSEDED
    }
}
