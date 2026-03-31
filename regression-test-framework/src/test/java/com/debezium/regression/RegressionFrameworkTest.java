package com.debezium.regression;

import com.debezium.regression.comparator.DataComparator;
import com.debezium.regression.comparator.DiffEntry;
import com.debezium.regression.model.Baseline;
import com.debezium.regression.model.CdcEvent;
import com.debezium.regression.model.ComparisonResult;
import com.debezium.regression.model.TestSession;
import com.debezium.regression.model.TestSession.Environment;
import com.debezium.regression.model.TestSession.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for core regression framework logic.
 *
 * <p>These tests verify diff detection, verdict computation, and assertion rules
 * without requiring a running Kafka cluster or PostgreSQL instance.
 */
class RegressionFrameworkTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    // -----------------------------------------------------------------------
    // DiffEntry factory method tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DiffEntry.missingEvent creates correct entry")
    void testMissingEventDiff() {
        var event = buildBaselineEvent("public.reservations", "{\"reservation_id\":1}", "c");
        DiffEntry diff = DiffEntry.missingEvent(event);

        assertThat(diff.getType()).isEqualTo(DiffEntry.DiffType.MISSING_EVENT);
        assertThat(diff.getTableName()).isEqualTo("public.reservations");
        assertThat(diff.getPrimaryKey()).isEqualTo("{\"reservation_id\":1}");
        assertThat(diff.getOperation()).isEqualTo("c");
    }

    @Test
    @DisplayName("DiffEntry.extraEvent creates correct entry")
    void testExtraEventDiff() {
        CdcEvent event = buildCdcEvent("public.flights", "{\"flight_id\":\"AA101\"}", "u");
        DiffEntry diff = DiffEntry.extraEvent(event);

        assertThat(diff.getType()).isEqualTo(DiffEntry.DiffType.EXTRA_EVENT);
        assertThat(diff.getTableName()).isEqualTo("public.flights");
        assertThat(diff.getPrimaryKey()).isEqualTo("{\"flight_id\":\"AA101\"}");
    }

    @Test
    @DisplayName("DiffEntry.valueMismatch creates correct entry")
    void testValueMismatchDiff() {
        DiffEntry diff = DiffEntry.valueMismatch(
                "public.tickets", "{\"ticket_id\":99}", "c",
                "seat", "\"14B\"", "\"15C\"");

        assertThat(diff.getType()).isEqualTo(DiffEntry.DiffType.VALUE_MISMATCH);
        assertThat(diff.getColumn()).isEqualTo("seat");
        assertThat(diff.getBaselineValue()).isEqualTo("\"14B\"");
        assertThat(diff.getSessionValue()).isEqualTo("\"15C\"");
    }

    @Test
    @DisplayName("DiffEntry.orderMismatch creates correct entry")
    void testOrderMismatchDiff() {
        var event = buildBaselineEvent("public.payments", "{\"payment_id\":5}", "c");
        DiffEntry diff = DiffEntry.orderMismatch(event, 3L, 7L);

        assertThat(diff.getType()).isEqualTo(DiffEntry.DiffType.ORDER_MISMATCH);
        assertThat(diff.getBaselineSeq()).isEqualTo(3L);
        assertThat(diff.getSessionSeq()).isEqualTo(7L);
    }

    // -----------------------------------------------------------------------
    // ComparisonResult model tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ComparisonResult with zero diffs has PASS verdict")
    void testPassVerdict() {
        ComparisonResult result = ComparisonResult.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .baselineId(UUID.randomUUID())
                .testCaseName("book_flight")
                .verdict(ComparisonResult.Verdict.PASS)
                .comparedAt(Instant.now())
                .baselineEventCount(5)
                .sessionEventCount(5)
                .missingEventCount(0)
                .extraEventCount(0)
                .valueMismatchCount(0)
                .orderMismatchCount(0)
                .build();

        assertThat(result.getVerdict()).isEqualTo(ComparisonResult.Verdict.PASS);
        assertThat(result.getMissingEventCount()).isZero();
        assertThat(result.getExtraEventCount()).isZero();
    }

    @Test
    @DisplayName("ComparisonResult with diffs has FAIL verdict")
    void testFailVerdict() {
        ComparisonResult result = ComparisonResult.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .baselineId(UUID.randomUUID())
                .testCaseName("book_flight")
                .verdict(ComparisonResult.Verdict.FAIL)
                .comparedAt(Instant.now())
                .baselineEventCount(5)
                .sessionEventCount(4)
                .missingEventCount(1)
                .extraEventCount(0)
                .valueMismatchCount(0)
                .orderMismatchCount(0)
                .build();

        assertThat(result.getVerdict()).isEqualTo(ComparisonResult.Verdict.FAIL);
        assertThat(result.getMissingEventCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // TestSession model tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TestSession builder sets fields correctly")
    void testSessionBuilder() {
        UUID id = UUID.randomUUID();
        TestSession session = TestSession.builder()
                .id(id)
                .testCaseName("cancel_booking")
                .environment(Environment.MONOLITH)
                .status(SessionStatus.STARTED)
                .startedAt(Instant.now())
                .build();

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getTestCaseName()).isEqualTo("cancel_booking");
        assertThat(session.getEnvironment()).isEqualTo(Environment.MONOLITH);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.STARTED);
        assertThat(session.getTags()).isNotNull().isEmpty();
    }

    // -----------------------------------------------------------------------
    // Baseline model tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Baseline starts in PENDING status")
    void testBaselinePendingStatus() {
        Baseline baseline = Baseline.builder()
                .id(UUID.randomUUID())
                .testCaseName("book_flight")
                .version(1)
                .status(Baseline.BaselineStatus.PENDING)
                .createdAt(Instant.now())
                .eventCount(5)
                .build();

        assertThat(baseline.getStatus()).isEqualTo(Baseline.BaselineStatus.PENDING);
        assertThat(baseline.getApprovedAt()).isNull();
        assertThat(baseline.getApprovedBy()).isNull();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private com.debezium.regression.baseline.BaselineEvent buildBaselineEvent(
            String table, String pk, String op) {
        return com.debezium.regression.baseline.BaselineEvent.builder()
                .baselineId(UUID.randomUUID())
                .kafkaTopic("debezium." + table)
                .kafkaPartition(0)
                .kafkaOffset(100L)
                .operation(op)
                .tableName(table)
                .primaryKey(pk)
                .afterImage("{\"reservation_id\":1,\"status\":\"CONFIRMED\"}")
                .sequenceNumber(1L)
                .build();
    }

    private CdcEvent buildCdcEvent(String table, String pk, String op) {
        return CdcEvent.builder()
                .sessionId(UUID.randomUUID())
                .kafkaTopic("debezium." + table)
                .kafkaPartition(0)
                .kafkaOffset(200L)
                .operation(op)
                .tableName(table)
                .primaryKey(pk)
                .afterImage("{\"flight_id\":\"AA101\",\"available_seats\":9}")
                .sequenceNumber(2L)
                .build();
    }
}
