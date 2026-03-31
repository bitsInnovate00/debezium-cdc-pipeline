package com.debezium.regression.comparator;

import com.debezium.regression.baseline.BaselineEvent;
import com.debezium.regression.baseline.BaselineService;
import com.debezium.regression.capture.CaptureConfig;
import com.debezium.regression.capture.CdcEventRepository;
import com.debezium.regression.model.Baseline;
import com.debezium.regression.model.CdcEvent;
import com.debezium.regression.model.ComparisonResult;
import com.debezium.regression.model.ComparisonResult.Verdict;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares a test session's CDC events against an approved baseline.
 *
 * <p>The comparison is performed at the row level: events are matched by
 * (tableName, primaryKey, operation) and column values are diffed field by field,
 * excluding non-deterministic columns configured in {@link CaptureConfig}.
 *
 * <p>The four difference categories are:
 * <ul>
 *   <li><b>MISSING_EVENT</b> — event in baseline but absent from new session</li>
 *   <li><b>EXTRA_EVENT</b>   — event in new session but absent from baseline</li>
 *   <li><b>VALUE_MISMATCH</b> — same key/operation but different column value</li>
 *   <li><b>ORDER_MISMATCH</b> — present in both but sequence numbers differ</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataComparator {

    /**
     * Lightweight wrapper pairing an event with its sequence number for indexed lookup.
     * Used to match baseline and session events by (table, primaryKey, operation) key.
     */
    private static final class IndexedEvent<T> {
        final T    event;
        final long seq;

        IndexedEvent(T event, long seq) {
            this.event = event;
            this.seq   = seq;
        }
    }

    private final BaselineService baselineService;
    private final CdcEventRepository eventRepository;
    private final ComparisonResultRepository resultRepository;
    private final CaptureConfig captureConfig;
    private final ObjectMapper objectMapper;

    /**
     * Compares all CDC events for a completed session against the approved baseline
     * for the same test case name.
     *
     * @param session the completed microservices session to validate
     * @return the persisted {@link ComparisonResult}
     */
    @Transactional
    public ComparisonResult compare(com.debezium.regression.model.TestSession session) {
        Optional<Baseline> optBaseline =
                baselineService.findApprovedBaseline(session.getTestCaseName());

        if (optBaseline.isEmpty()) {
            throw new IllegalStateException(
                    "No approved baseline found for test case: " + session.getTestCaseName());
        }
        Baseline baseline = optBaseline.get();

        List<BaselineEvent> baselineEvents =
                baselineService.getBaselineEvents(baseline.getId());
        List<CdcEvent> sessionEvents =
                eventRepository.findBySessionIdOrderBySequenceNumber(session.getId());

        log.info("Comparing session {} ({} events) against baseline {} ({} events)",
                 session.getId(), sessionEvents.size(), baseline.getId(), baselineEvents.size());

        List<DiffEntry> diffs = computeDiffs(baselineEvents, sessionEvents, session.getTestCaseName());

        ComparisonResult result = buildResult(session, baseline, baselineEvents, sessionEvents, diffs);
        result = resultRepository.save(result);
        log.info("Comparison complete for session {}: {} — {} diffs",
                 session.getId(), result.getVerdict(), diffs.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Core diff algorithm
    // -------------------------------------------------------------------------

    private List<DiffEntry> computeDiffs(List<BaselineEvent> baseline,
                                          List<CdcEvent> session,
                                          String testCaseName) {
        List<DiffEntry> diffs = new ArrayList<>();

        // Index session events by (table, primaryKey, operation)
        Map<String, List<IndexedEvent<CdcEvent>>> sessionIndex = indexEvents(
                session,
                e -> eventKey(e.getTableName(), e.getPrimaryKey(), e.getOperation()),
                e -> new IndexedEvent<>(e, e.getSequenceNumber())
        );

        // Index baseline events by the same key
        Map<String, List<IndexedEvent<BaselineEvent>>> baselineIndex = indexEvents(
                baseline,
                e -> eventKey(e.getTableName(), e.getPrimaryKey(), e.getOperation()),
                e -> new IndexedEvent<>(e, e.getSequenceNumber())
        );

        // Check baseline events against session events
        for (Map.Entry<String, List<IndexedEvent<BaselineEvent>>> entry : baselineIndex.entrySet()) {
            String key = entry.getKey();
            List<IndexedEvent<BaselineEvent>> bEvents = entry.getValue();
            List<IndexedEvent<CdcEvent>>      sEvents = sessionIndex.getOrDefault(key, List.of());

            for (int i = 0; i < bEvents.size(); i++) {
                BaselineEvent be = bEvents.get(i).event;
                if (i >= sEvents.size()) {
                    diffs.add(DiffEntry.missingEvent(be));
                } else {
                    CdcEvent se = sEvents.get(i).event;
                    diffs.addAll(compareFields(be, se, testCaseName));
                    if (bEvents.get(i).seq != sEvents.get(i).seq) {
                        diffs.add(DiffEntry.orderMismatch(be, bEvents.get(i).seq, sEvents.get(i).seq));
                    }
                }
            }
        }

        // Check session events not present in baseline
        for (Map.Entry<String, List<IndexedEvent<CdcEvent>>> entry : sessionIndex.entrySet()) {
            String key = entry.getKey();
            List<IndexedEvent<CdcEvent>>      sEvents = entry.getValue();
            List<IndexedEvent<BaselineEvent>> bEvents = baselineIndex.getOrDefault(key, List.of());

            for (int i = bEvents.size(); i < sEvents.size(); i++) {
                diffs.add(DiffEntry.extraEvent(sEvents.get(i).event));
            }
        }

        return diffs;
    }

    private List<DiffEntry> compareFields(BaselineEvent baseline,
                                           CdcEvent session,
                                           String testCaseName) {
        List<DiffEntry> diffs = new ArrayList<>();
        List<String> excluded = captureConfig.getNonDeterministicColumns(baseline.getTableName());

        String baselineImage = baseline.getAfterImage() != null
                ? baseline.getAfterImage() : baseline.getBeforeImage();
        String sessionImage  = session.getAfterImage() != null
                ? session.getAfterImage() : session.getBeforeImage();

        if (baselineImage == null && sessionImage == null) return diffs;
        if (baselineImage == null || sessionImage == null) {
            diffs.add(DiffEntry.valueMismatch(baseline.getTableName(), baseline.getPrimaryKey(),
                                              baseline.getOperation(), "row", baselineImage, sessionImage));
            return diffs;
        }

        try {
            JsonNode baseNode = objectMapper.readTree(baselineImage);
            JsonNode sessNode = objectMapper.readTree(sessionImage);

            // Remove non-deterministic columns before comparison
            JsonNode cleanBase = stripColumns((ObjectNode) baseNode.deepCopy(), excluded);
            JsonNode cleanSess = stripColumns((ObjectNode) sessNode.deepCopy(), excluded);

            cleanBase.fields().forEachRemaining(entry -> {
                String col = entry.getKey();
                JsonNode bVal = entry.getValue();
                JsonNode sVal = cleanSess.get(col);
                if (sVal == null || !bVal.equals(sVal)) {
                    diffs.add(DiffEntry.valueMismatch(
                            baseline.getTableName(), baseline.getPrimaryKey(),
                            baseline.getOperation(), col,
                            bVal.toString(),
                            sVal != null ? sVal.toString() : "null"));
                }
            });

            // Check columns in session not in baseline
            cleanSess.fields().forEachRemaining(entry -> {
                String col = entry.getKey();
                if (!cleanBase.has(col)) {
                    diffs.add(DiffEntry.valueMismatch(
                            baseline.getTableName(), baseline.getPrimaryKey(),
                            baseline.getOperation(), col,
                            "null", entry.getValue().toString()));
                }
            });

        } catch (Exception e) {
            log.warn("Field comparison failed for table {}: {}", baseline.getTableName(), e.getMessage());
        }
        return diffs;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private <T, R> Map<String, List<IndexedEvent<R>>> indexEvents(
            List<T> events,
            java.util.function.Function<T, String> keyFn,
            java.util.function.Function<T, IndexedEvent<R>> mapFn) {
        Map<String, List<IndexedEvent<R>>> index = new LinkedHashMap<>();
        for (T event : events) {
            index.computeIfAbsent(keyFn.apply(event), k -> new ArrayList<>())
                 .add(mapFn.apply(event));
        }
        return index;
    }

    private String eventKey(String table, String primaryKey, String operation) {
        return table + "::" + (primaryKey != null ? primaryKey : "") + "::" + operation;
    }

    private JsonNode stripColumns(ObjectNode node, List<String> columns) {
        columns.forEach(node::remove);
        return node;
    }

    private ComparisonResult buildResult(com.debezium.regression.model.TestSession session,
                                          Baseline baseline,
                                          List<BaselineEvent> bEvents,
                                          List<CdcEvent> sEvents,
                                          List<DiffEntry> diffs) {
        long missing = diffs.stream().filter(d -> d.getType() == DiffEntry.DiffType.MISSING_EVENT).count();
        long extra   = diffs.stream().filter(d -> d.getType() == DiffEntry.DiffType.EXTRA_EVENT).count();
        long value   = diffs.stream().filter(d -> d.getType() == DiffEntry.DiffType.VALUE_MISMATCH).count();
        long order   = diffs.stream().filter(d -> d.getType() == DiffEntry.DiffType.ORDER_MISMATCH).count();

        Verdict verdict = diffs.isEmpty() ? Verdict.PASS : Verdict.FAIL;
        String diffJson;
        try {
            diffJson = objectMapper.writeValueAsString(diffs);
        } catch (Exception e) {
            diffJson = "[]";
        }

        return ComparisonResult.builder()
                .id(UUID.randomUUID())
                .sessionId(session.getId())
                .baselineId(baseline.getId())
                .testCaseName(session.getTestCaseName())
                .verdict(verdict)
                .comparedAt(Instant.now())
                .baselineEventCount(bEvents.size())
                .sessionEventCount(sEvents.size())
                .missingEventCount((int) missing)
                .extraEventCount((int) extra)
                .valueMismatchCount((int) value)
                .orderMismatchCount((int) order)
                .diffDetail(diffJson)
                .summary(String.format("Verdict=%s missing=%d extra=%d valueMismatch=%d orderMismatch=%d",
                                       verdict, missing, extra, value, order))
                .build();
    }
}
