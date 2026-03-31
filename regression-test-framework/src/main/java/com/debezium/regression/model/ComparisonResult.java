package com.debezium.regression.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The result of comparing a test session's CDC events against an approved baseline.
 *
 * <p>A ComparisonResult is produced by the {@code DataComparator} and evaluated
 * by the {@code AssertionEngine} to produce a PASS or FAIL verdict for the test case.
 */
@Entity
@Table(name = "comparison_results",
       indexes = {
           @Index(name = "idx_comp_session_id", columnList = "session_id"),
           @Index(name = "idx_comp_baseline_id", columnList = "baseline_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "baseline_id", nullable = false)
    private UUID baselineId;

    @Column(name = "test_case_name", nullable = false)
    private String testCaseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false)
    private Verdict verdict;

    @Column(name = "compared_at", nullable = false)
    private Instant comparedAt;

    /** Number of events in the baseline */
    @Column(name = "baseline_event_count")
    private int baselineEventCount;

    /** Number of events in the new session */
    @Column(name = "session_event_count")
    private int sessionEventCount;

    /** Number of events present in baseline but missing from new session */
    @Column(name = "missing_event_count")
    private int missingEventCount;

    /** Number of events in new session not present in baseline */
    @Column(name = "extra_event_count")
    private int extraEventCount;

    /** Number of events present in both but with differing column values */
    @Column(name = "value_mismatch_count")
    private int valueMismatchCount;

    /** Number of events present in both but in different order */
    @Column(name = "order_mismatch_count")
    private int orderMismatchCount;

    /** Full JSON diff detail (list of DiffEntry objects) */
    @Column(name = "diff_detail", columnDefinition = "TEXT")
    private String diffDetail;

    /** Human-readable summary of the comparison */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    public enum Verdict {
        PASS, FAIL, ERROR
    }
}
