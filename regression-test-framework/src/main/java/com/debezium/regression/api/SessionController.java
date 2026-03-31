package com.debezium.regression.api;

import com.debezium.regression.capture.CdcEventCaptureService;
import com.debezium.regression.model.TestSession;
import com.debezium.regression.model.TestSession.Environment;
import com.debezium.regression.session.TestSessionManager;
import com.debezium.regression.session.TestSessionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for managing CDC capture sessions.
 *
 * <pre>
 * POST   /api/sessions               Create and start a new session
 * GET    /api/sessions               List sessions (optional filter by testCaseName, environment)
 * GET    /api/sessions/{id}          Get a specific session
 * POST   /api/sessions/{id}/end      End a session and trigger CDC capture
 * DELETE /api/sessions/{id}          Delete a session
 * </pre>
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final TestSessionManager    sessionManager;
    private final TestSessionRepository sessionRepository;
    private final CdcEventCaptureService captureService;

    @PostMapping
    public ResponseEntity<TestSession> startSession(@Valid @RequestBody StartSessionRequest request) {
        TestSession session = sessionManager.startSession(
                request.getTestCaseName(),
                request.getEnvironment(),
                request.getTags(),
                request.getCommitSha()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping
    public List<TestSession> listSessions(
            @RequestParam(required = false) String testCaseName,
            @RequestParam(required = false) Environment environment) {
        if (testCaseName != null && environment != null) {
            return sessionRepository.findByTestCaseNameAndEnvironment(testCaseName, environment);
        } else if (testCaseName != null) {
            return sessionRepository.findByTestCaseName(testCaseName);
        } else if (environment != null) {
            return sessionRepository.findByEnvironment(environment);
        }
        return sessionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestSession> getSession(@PathVariable UUID id) {
        return sessionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<TestSession> endSession(@PathVariable UUID id) {
        TestSession session = sessionManager.endSession(id);
        captureService.captureEventsForSession(session);
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        if (!sessionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        sessionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class StartSessionRequest {
        @NotBlank private String testCaseName;
        @NotNull  private Environment environment;
        private Map<String, String> tags;
        private String commitSha;
    }
}
