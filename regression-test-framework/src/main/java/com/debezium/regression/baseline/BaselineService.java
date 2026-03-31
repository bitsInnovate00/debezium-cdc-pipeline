package com.debezium.regression.baseline;

import com.debezium.regression.capture.CdcEventRepository;
import com.debezium.regression.model.Baseline;
import com.debezium.regression.model.Baseline.BaselineStatus;
import com.debezium.regression.model.CdcEvent;
import com.debezium.regression.model.TestSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages creation, versioning and approval of CDC baselines.
 *
 * <p>A baseline is created from a completed monolithic test session. It stores a
 * snapshot of all captured CDC events linked to a test case name.  Baselines must
 * be explicitly approved before the {@link com.debezium.regression.comparator.DataComparator}
 * will use them for regression validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineService {

    private final BaselineRepository baselineRepository;
    private final BaselineEventRepository baselineEventRepository;
    private final CdcEventRepository cdcEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new baseline from a completed session.
     *
     * <p>The new baseline starts in {@code PENDING} status and must be explicitly
     * approved.  Any previous APPROVED baseline for the same test case is marked
     * SUPERSEDED once the new one is approved.
     *
     * @param session    completed monolithic session
     * @param commitSha  optional commit SHA for traceability
     * @return the newly created (PENDING) Baseline
     */
    @Transactional
    public Baseline createBaseline(TestSession session, String commitSha) {
        if (session.getStatus() != TestSession.SessionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot create baseline from session in status: " + session.getStatus());
        }

        List<CdcEvent> events = cdcEventRepository
                .findBySessionIdOrderBySequenceNumber(session.getId());

        int nextVersion = baselineRepository
                .findLatestVersionByTestCaseName(session.getTestCaseName())
                .map(v -> v + 1)
                .orElse(1);

        String summary = buildSummary(events);

        Baseline baseline = Baseline.builder()
                .id(UUID.randomUUID())
                .testCaseName(session.getTestCaseName())
                .version(nextVersion)
                .sourceSessionId(session.getId())
                .status(BaselineStatus.PENDING)
                .createdAt(Instant.now())
                .commitSha(commitSha)
                .eventCount(events.size())
                .summary(summary)
                .build();

        baseline = baselineRepository.save(baseline);

        // Copy captured events into the baseline event store
        List<BaselineEvent> baselineEvents = events.stream()
                .map(e -> BaselineEvent.from(e, baseline.getId()))
                .collect(Collectors.toList());
        baselineEventRepository.saveAll(baselineEvents);

        log.info("Created baseline {} v{} for test case '{}' with {} events",
                 baseline.getId(), nextVersion, session.getTestCaseName(), events.size());
        return baseline;
    }

    /**
     * Approves a pending baseline, making it the active golden reference.
     * Any previously approved baseline for the same test case is superseded.
     */
    @Transactional
    public Baseline approveBaseline(UUID baselineId, String approvedBy, String notes) {
        Baseline baseline = findById(baselineId);
        if (baseline.getStatus() != BaselineStatus.PENDING) {
            throw new IllegalStateException("Baseline is not in PENDING status: " + baseline.getStatus());
        }

        // Supersede any currently approved baseline for this test case
        baselineRepository.findApprovedByTestCaseName(baseline.getTestCaseName())
                .forEach(existing -> {
                    existing.setStatus(BaselineStatus.SUPERSEDED);
                    baselineRepository.save(existing);
                    log.info("Superseded baseline {} for test case '{}'",
                             existing.getId(), existing.getTestCaseName());
                });

        baseline.setStatus(BaselineStatus.APPROVED);
        baseline.setApprovedAt(Instant.now());
        baseline.setApprovedBy(approvedBy);
        baseline.setNotes(notes);
        baseline = baselineRepository.save(baseline);
        log.info("Approved baseline {} for test case '{}'", baselineId, baseline.getTestCaseName());
        return baseline;
    }

    /** Rejects a pending baseline. */
    @Transactional
    public Baseline rejectBaseline(UUID baselineId, String rejectedBy, String reason) {
        Baseline baseline = findById(baselineId);
        baseline.setStatus(BaselineStatus.REJECTED);
        baseline.setNotes("Rejected by " + rejectedBy + ": " + reason);
        baseline = baselineRepository.save(baseline);
        log.info("Rejected baseline {} for test case '{}'", baselineId, baseline.getTestCaseName());
        return baseline;
    }

    /** Returns the latest approved baseline for a test case, if any. */
    public Optional<Baseline> findApprovedBaseline(String testCaseName) {
        return baselineRepository.findApprovedByTestCaseName(testCaseName)
                .stream().findFirst();
    }

    /** Returns all baseline events for a given baseline, ordered by sequence. */
    public List<BaselineEvent> getBaselineEvents(UUID baselineId) {
        return baselineEventRepository.findByBaselineIdOrderBySequenceNumber(baselineId);
    }

    /** Returns all baselines across all test cases. */
    public List<Baseline> getAllBaselines() {
        return baselineRepository.findAll();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Baseline findById(UUID id) {
        return baselineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Baseline not found: " + id));
    }

    private String buildSummary(List<CdcEvent> events) {
        Map<String, Map<String, Long>> summary = new LinkedHashMap<>();
        for (CdcEvent event : events) {
            summary.computeIfAbsent(event.getTableName(), k -> new LinkedHashMap<>())
                   .merge(event.getOperation(), 1L, Long::sum);
        }
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{}";
        }
    }
}
