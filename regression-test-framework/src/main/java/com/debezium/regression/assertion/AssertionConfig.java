package com.debezium.regression.assertion;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configures which diffs are permitted (do not cause test failure).
 *
 * <pre>
 * cdc:
 *   assertion:
 *     order-mismatch-permitted-tables:
 *       - public.seat_assignments
 *     permitted-columns:
 *       public.reservations: [updated_at]
 *       public.payments:     [gateway_txn_id]
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "cdc.assertion")
public class AssertionConfig {

    /**
     * Tables for which ORDER_MISMATCH diffs are not considered failures.
     * Useful for tables where row ordering is non-deterministic.
     */
    private Set<String> orderMismatchPermittedTables = new HashSet<>();

    /**
     * Per-table list of columns whose VALUE_MISMATCH is permitted
     * (in addition to those in {@link com.debezium.regression.capture.CaptureConfig#getNonDeterministicColumns}).
     */
    private Map<String, List<String>> permittedColumns = new LinkedHashMap<>();

    /**
     * Returns true if an ORDER_MISMATCH on this table is permitted for the given test case.
     */
    public boolean isOrderMismatchPermitted(String tableName, String testCaseName) {
        return orderMismatchPermittedTables.contains(tableName);
    }

    /**
     * Returns true if a VALUE_MISMATCH on this column is permitted.
     */
    public boolean isColumnPermitted(String tableName, String column, String testCaseName) {
        List<String> cols = permittedColumns.getOrDefault(tableName, Collections.emptyList());
        return column != null && cols.contains(column);
    }
}
