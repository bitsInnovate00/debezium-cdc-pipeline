package com.debezium.regression.capture;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configuration for CDC event capture behaviour.
 *
 * <p>Loaded from {@code application.yml} under the {@code cdc.capture} prefix.
 *
 * <pre>
 * cdc:
 *   capture:
 *     excluded-tables:
 *       - public.flyway_schema_history
 *       - public.spring_session
 *     primary-key-columns:
 *       public.reservations: [reservation_id]
 *       public.flights:      [flight_id]
 *     non-deterministic-columns:
 *       public.reservations: [created_at, updated_at]
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "cdc.capture")
public class CaptureConfig {

    /** Tables to exclude from CDC capture (schema-qualified) */
    private Set<String> excludedTables = new HashSet<>(Arrays.asList(
            "public.flyway_schema_history",
            "public.spring_session"
    ));

    /**
     * Mapping of table name → list of primary key column names.
     * Used to construct the primary-key JSON for each captured event.
     */
    private Map<String, List<String>> primaryKeyColumns = new LinkedHashMap<>();

    /**
     * Mapping of table name → list of column names to exclude from value comparison.
     * These columns contain non-deterministic values (timestamps, sequences, etc.)
     */
    private Map<String, List<String>> nonDeterministicColumns = new LinkedHashMap<>();

    /**
     * Returns the primary-key column list for a given table, or an empty list if not configured.
     */
    public List<String> getPrimaryKeyColumns(String tableName) {
        return primaryKeyColumns.getOrDefault(tableName, Collections.emptyList());
    }

    /**
     * Returns the non-deterministic column list for a given table.
     */
    public List<String> getNonDeterministicColumns(String tableName) {
        return nonDeterministicColumns.getOrDefault(tableName, Collections.emptyList());
    }
}
