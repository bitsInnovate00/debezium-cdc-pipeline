package com.debezium.regression.comparator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single difference found by the {@link DataComparator}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffEntry {

    public enum DiffType {
        MISSING_EVENT, EXTRA_EVENT, VALUE_MISMATCH, ORDER_MISMATCH
    }

    private DiffType type;
    private String   tableName;
    private String   primaryKey;
    private String   operation;

    /** Column name (for VALUE_MISMATCH), null otherwise */
    private String column;

    /** Value from the baseline (or null for EXTRA_EVENT) */
    private String baselineValue;

    /** Value from the new session (or null for MISSING_EVENT) */
    private String sessionValue;

    /** Sequence numbers for ORDER_MISMATCH */
    private Long baselineSeq;
    private Long sessionSeq;

    public static DiffEntry missingEvent(BaselineEventRef event) {
        return DiffEntry.builder()
                .type(DiffType.MISSING_EVENT)
                .tableName(event.getTableName())
                .primaryKey(event.getPrimaryKey())
                .operation(event.getOperation())
                .build();
    }

    public static DiffEntry missingEvent(com.debezium.regression.baseline.BaselineEvent event) {
        return missingEvent(new BaselineEventRef(event.getTableName(), event.getPrimaryKey(), event.getOperation()));
    }

    public static DiffEntry extraEvent(com.debezium.regression.model.CdcEvent event) {
        return DiffEntry.builder()
                .type(DiffType.EXTRA_EVENT)
                .tableName(event.getTableName())
                .primaryKey(event.getPrimaryKey())
                .operation(event.getOperation())
                .build();
    }

    public static DiffEntry valueMismatch(String table, String pk, String op,
                                           String column, String baselineVal, String sessionVal) {
        return DiffEntry.builder()
                .type(DiffType.VALUE_MISMATCH)
                .tableName(table)
                .primaryKey(pk)
                .operation(op)
                .column(column)
                .baselineValue(baselineVal)
                .sessionValue(sessionVal)
                .build();
    }

    public static DiffEntry orderMismatch(com.debezium.regression.baseline.BaselineEvent event,
                                           long baselineSeq, long sessionSeq) {
        return DiffEntry.builder()
                .type(DiffType.ORDER_MISMATCH)
                .tableName(event.getTableName())
                .primaryKey(event.getPrimaryKey())
                .operation(event.getOperation())
                .baselineSeq(baselineSeq)
                .sessionSeq(sessionSeq)
                .build();
    }

    /** Lightweight reference for factory methods that need only key fields. */
    @Data
    @AllArgsConstructor
    public static class BaselineEventRef {
        private String tableName;
        private String primaryKey;
        private String operation;
    }
}
