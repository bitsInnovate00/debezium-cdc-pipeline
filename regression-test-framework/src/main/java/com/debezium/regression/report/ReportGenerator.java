package com.debezium.regression.report;

import com.debezium.regression.assertion.AssertionEngine;
import com.debezium.regression.assertion.AssertionReport;
import com.debezium.regression.comparator.ComparisonResultRepository;
import com.debezium.regression.model.ComparisonResult;
import com.debezium.regression.model.ComparisonResult.Verdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates regression test reports in JUnit-XML and HTML formats.
 *
 * <p>JUnit-XML output is compatible with Jenkins, GitHub Actions, GitLab CI,
 * and any CI system that accepts the Surefire report format.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerator {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneOffset.UTC);

    private final AssertionEngine assertionEngine;
    private final ComparisonResultRepository resultRepository;
    private final TemplateEngine templateEngine;

    /**
     * Generates a JUnit-XML report for a single session comparison result.
     *
     * @param sessionId the session whose comparison result to report
     * @return JUnit-XML string
     */
    public String generateJUnitXml(UUID sessionId) {
        ComparisonResult result = findResult(sessionId);
        AssertionReport  report = assertionEngine.buildReport(result);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"CDC Regression - ")
           .append(escape(result.getTestCaseName())).append("\" ")
           .append("tests=\"1\" ")
           .append("failures=\"").append(report.isPassed() ? 0 : 1).append("\" ")
           .append("errors=\"0\" ")
           .append("time=\"0\" ")
           .append("timestamp=\"").append(ISO.format(result.getComparedAt())).append("\">\n");

        xml.append("  <testcase name=\"").append(escape(result.getTestCaseName())).append("\" ")
           .append("classname=\"com.debezium.regression\" ")
           .append("time=\"0\">\n");

        if (!report.isPassed()) {
            xml.append("    <failure message=\"CDC regression failure: ")
               .append(report.getUnpermittedDiffs()).append(" diff(s) found\" ")
               .append("type=\"CDCRegressionFailure\">\n");
            xml.append("      ").append(escape(result.getSummary())).append("\n");
            xml.append("      Baseline events: ").append(result.getBaselineEventCount()).append("\n");
            xml.append("      Session events:  ").append(result.getSessionEventCount()).append("\n");
            xml.append("      Missing: ").append(result.getMissingEventCount()).append("\n");
            xml.append("      Extra:   ").append(result.getExtraEventCount()).append("\n");
            xml.append("      Value mismatches: ").append(result.getValueMismatchCount()).append("\n");
            xml.append("    </failure>\n");
        }

        xml.append("  </testcase>\n");
        xml.append("</testsuite>\n");
        return xml.toString();
    }

    /**
     * Generates a JUnit-XML suite report covering multiple comparison results.
     *
     * @param results list of comparison results to include
     * @return JUnit-XML string
     */
    public String generateJUnitXmlSuite(List<ComparisonResult> results) {
        long passed  = results.stream().filter(r -> r.getVerdict() == Verdict.PASS).count();
        long failed  = results.stream().filter(r -> r.getVerdict() == Verdict.FAIL).count();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuites name=\"CDC Regression Suite\" ")
           .append("tests=\"").append(results.size()).append("\" ")
           .append("failures=\"").append(failed).append("\" ")
           .append("errors=\"0\" ")
           .append("timestamp=\"").append(ISO.format(Instant.now())).append("\">\n");

        for (ComparisonResult result : results) {
            AssertionReport report = assertionEngine.buildReport(result);
            xml.append("  <testsuite name=\"").append(escape(result.getTestCaseName())).append("\" ")
               .append("tests=\"1\" ")
               .append("failures=\"").append(report.isPassed() ? 0 : 1).append("\">\n");
            xml.append("    <testcase name=\"").append(escape(result.getTestCaseName())).append("\">\n");
            if (!report.isPassed()) {
                xml.append("      <failure message=\"")
                   .append(report.getUnpermittedDiffs()).append(" diff(s)\">");
                xml.append(escape(result.getSummary()));
                xml.append("</failure>\n");
            }
            xml.append("    </testcase>\n");
            xml.append("  </testsuite>\n");
        }

        xml.append("</testsuites>\n");
        return xml.toString();
    }

    /**
     * Generates an HTML report for a single session result using a Thymeleaf template.
     *
     * @param sessionId the session to report
     * @return HTML string
     */
    public String generateHtmlReport(UUID sessionId) {
        ComparisonResult result = findResult(sessionId);
        AssertionReport  report = assertionEngine.buildReport(result);

        Context ctx = new Context();
        ctx.setVariable("result", result);
        ctx.setVariable("report", report);
        ctx.setVariable("generatedAt", ISO.format(Instant.now()));

        return templateEngine.process("regression-report", ctx);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ComparisonResult findResult(UUID sessionId) {
        return resultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No comparison result found for session: " + sessionId));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
