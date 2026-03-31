package com.debezium.regression.assertion;

import com.debezium.regression.comparator.DiffEntry;
import com.debezium.regression.model.ComparisonResult.Verdict;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Structured report produced by the {@link AssertionEngine} for a single test case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssertionReport {

    private String   testCaseName;
    private UUID     sessionId;
    private UUID     baselineId;
    private Verdict  verdict;

    private int      totalDiffs;
    private int      permittedDiffs;
    private int      unpermittedDiffs;

    private int      baselineEventCount;
    private int      sessionEventCount;

    private List<DiffEntry> diffs;

    /** Convenience: true if the verdict is PASS */
    public boolean isPassed() {
        return verdict == Verdict.PASS;
    }
}
