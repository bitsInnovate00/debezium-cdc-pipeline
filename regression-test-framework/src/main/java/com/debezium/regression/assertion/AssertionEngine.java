package com.debezium.regression.assertion;

import com.debezium.regression.comparator.DiffEntry;
import com.debezium.regression.model.ComparisonResult;
import com.debezium.regression.model.ComparisonResult.Verdict;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Evaluates comparison results against configurable assertion rules to produce
 * a final PASS / FAIL verdict.
 *
 * <p>Default policy: any difference → FAIL.  Rules can be relaxed via
 * {@link AssertionConfig} (e.g., allow ORDER_MISMATCH for non-transactional tables).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssertionEngine {

    private final AssertionConfig config;
    private final ObjectMapper objectMapper;

    /**
     * Evaluates the assertion rules against a {@link ComparisonResult}.
     *
     * @param result the comparison result to evaluate
     * @return {@code true} if all assertions pass, {@code false} otherwise
     */
    public boolean evaluate(ComparisonResult result) {
        if (result.getVerdict() == Verdict.ERROR) {
            log.warn("Skipping assertion evaluation for ERROR result: {}", result.getId());
            return false;
        }

        List<DiffEntry> diffs = parseDiffs(result.getDiffDetail());
        boolean pass = true;

        for (DiffEntry diff : diffs) {
            if (!isDiffPermitted(diff, result.getTestCaseName())) {
                log.debug("Unpermitted diff: {} on table {} pk {}",
                          diff.getType(), diff.getTableName(), diff.getPrimaryKey());
                pass = false;
            }
        }

        log.info("Assertion for test case '{}': {}", result.getTestCaseName(), pass ? "PASS" : "FAIL");
        return pass;
    }

    /**
     * Returns a structured assertion report for a result.
     */
    public AssertionReport buildReport(ComparisonResult result) {
        List<DiffEntry> diffs = parseDiffs(result.getDiffDetail());
        long permitted   = diffs.stream().filter(d -> isDiffPermitted(d, result.getTestCaseName())).count();
        long unpermitted = diffs.size() - permitted;

        return AssertionReport.builder()
                .testCaseName(result.getTestCaseName())
                .sessionId(result.getSessionId())
                .baselineId(result.getBaselineId())
                .verdict(unpermitted == 0 ? Verdict.PASS : Verdict.FAIL)
                .totalDiffs(diffs.size())
                .permittedDiffs((int) permitted)
                .unpermittedDiffs((int) unpermitted)
                .baselineEventCount(result.getBaselineEventCount())
                .sessionEventCount(result.getSessionEventCount())
                .diffs(diffs)
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isDiffPermitted(DiffEntry diff, String testCaseName) {
        return switch (diff.getType()) {
            case ORDER_MISMATCH -> config.isOrderMismatchPermitted(diff.getTableName(), testCaseName);
            case VALUE_MISMATCH -> config.isColumnPermitted(diff.getTableName(), diff.getColumn(), testCaseName);
            case MISSING_EVENT, EXTRA_EVENT -> false;
        };
    }

    private List<DiffEntry> parseDiffs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse diff detail JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
