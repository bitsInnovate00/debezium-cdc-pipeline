package com.debezium.regression.api;

import com.debezium.regression.comparator.ComparisonResultRepository;
import com.debezium.regression.comparator.DataComparator;
import com.debezium.regression.model.ComparisonResult;
import com.debezium.regression.model.TestSession;
import com.debezium.regression.report.ReportGenerator;
import com.debezium.regression.session.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for triggering comparisons and fetching reports.
 *
 * <pre>
 * POST   /api/compare/{sessionId}            Compare session against approved baseline
 * GET    /api/reports/{sessionId}            Get JSON comparison result
 * GET    /api/reports/{sessionId}/junit      Get JUnit-XML report
 * GET    /api/reports/{sessionId}/html       Get HTML report
 * GET    /api/reports/suite/junit            Get suite-level JUnit-XML for all results
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class CompareController {

    private final DataComparator              comparator;
    private final ReportGenerator             reportGenerator;
    private final ComparisonResultRepository  resultRepository;
    private final TestSessionRepository       sessionRepository;

    @PostMapping("/api/compare/{sessionId}")
    public ResponseEntity<ComparisonResult> compare(@PathVariable UUID sessionId) {
        TestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        ComparisonResult result = comparator.compare(session);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/reports/{sessionId}")
    public ResponseEntity<ComparisonResult> getReport(@PathVariable UUID sessionId) {
        return resultRepository.findBySessionId(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/api/reports/{sessionId}/junit",
                produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getJUnitReport(@PathVariable UUID sessionId) {
        String xml = reportGenerator.generateJUnitXml(sessionId);
        return ResponseEntity.ok(xml);
    }

    @GetMapping(value = "/api/reports/{sessionId}/html",
                produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getHtmlReport(@PathVariable UUID sessionId) {
        String html = reportGenerator.generateHtmlReport(sessionId);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/api/reports/suite/junit",
                produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSuiteJUnitReport() {
        List<ComparisonResult> all = resultRepository.findAll();
        String xml = reportGenerator.generateJUnitXmlSuite(all);
        return ResponseEntity.ok(xml);
    }
}
