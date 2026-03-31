package com.debezium.regression.api;

import com.debezium.regression.baseline.BaselineService;
import com.debezium.regression.model.Baseline;
import com.debezium.regression.model.TestSession;
import com.debezium.regression.session.TestSessionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for managing CDC baselines.
 *
 * <pre>
 * GET    /api/baselines                     List all baselines
 * GET    /api/baselines/{testCaseName}       Get latest approved baseline for test case
 * POST   /api/baselines/from-session/{id}   Create a baseline from a completed session
 * POST   /api/baselines/{id}/approve        Approve a pending baseline
 * POST   /api/baselines/{id}/reject         Reject a pending baseline
 * </pre>
 */
@RestController
@RequestMapping("/api/baselines")
@RequiredArgsConstructor
public class BaselineController {

    private final BaselineService       baselineService;
    private final TestSessionRepository sessionRepository;

    @GetMapping
    public List<Baseline> listBaselines(
            @RequestParam(required = false) String testCaseName) {
        if (testCaseName != null) {
            return baselineService.findApprovedBaseline(testCaseName)
                    .map(List::of)
                    .orElse(List.of());
        }
        return baselineService.getAllBaselines();
    }

    @PostMapping("/from-session/{sessionId}")
    public ResponseEntity<Baseline> createFromSession(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String commitSha) {
        TestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        Baseline baseline = baselineService.createBaseline(session, commitSha);
        return ResponseEntity.status(HttpStatus.CREATED).body(baseline);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Baseline> approveBaseline(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalRequest request) {
        Baseline baseline = baselineService.approveBaseline(id, request.getApprovedBy(), request.getNotes());
        return ResponseEntity.ok(baseline);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Baseline> rejectBaseline(
            @PathVariable UUID id,
            @Valid @RequestBody RejectionRequest request) {
        Baseline baseline = baselineService.rejectBaseline(id, request.getRejectedBy(), request.getReason());
        return ResponseEntity.ok(baseline);
    }

    @Data
    public static class ApprovalRequest {
        @NotBlank private String approvedBy;
        private String notes;
    }

    @Data
    public static class RejectionRequest {
        @NotBlank private String rejectedBy;
        @NotBlank private String reason;
    }
}
