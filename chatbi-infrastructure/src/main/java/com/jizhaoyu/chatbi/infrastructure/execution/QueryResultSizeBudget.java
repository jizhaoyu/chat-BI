package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionFailure;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionStatus;
import com.jizhaoyu.chatbi.application.execution.QueryResultColumn;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.List;

final class QueryResultSizeBudget {
    static final int MAX_RESULT_BYTES = 2 * 1024 * 1024;
    static final int MAX_CELL_BYTES = 256 * 1024;
    private static final int ENVELOPE_RESERVE_BYTES = 1024;

    private final int maximumResultBytes;
    private final int maximumCellBytes;
    private long usedBytes = ENVELOPE_RESERVE_BYTES;

    QueryResultSizeBudget() {
        this(MAX_RESULT_BYTES, MAX_CELL_BYTES);
    }

    QueryResultSizeBudget(int maximumResultBytes, int maximumCellBytes) {
        if (maximumResultBytes <= ENVELOPE_RESERVE_BYTES || maximumCellBytes < 1) {
            throw new IllegalArgumentException("QUERY_RESULT_BUDGET_INVALID");
        }
        this.maximumResultBytes = maximumResultBytes;
        this.maximumCellBytes = maximumCellBytes;
    }

    void addColumns(List<QueryResultColumn> columns) {
        long bytes = 2;
        for (QueryResultColumn column : columns) {
            bytes += 20L + jsonStringBytes(column.name()) + jsonStringBytes(column.type());
        }
        requireAvailable(bytes);
        usedBytes += bytes;
    }

    boolean tryAddRow(List<Object> row) {
        long rowBytes = 3 + Math.max(0, row.size() - 1);
        for (Object value : row) {
            long cellBytes = jsonValueBytes(value);
            if (cellBytes > maximumCellBytes) {
                throw tooLarge();
            }
            rowBytes += cellBytes;
        }
        if (usedBytes + rowBytes > maximumResultBytes) {
            return false;
        }
        usedBytes += rowBytes;
        return true;
    }

    private void requireAvailable(long bytes) {
        if (usedBytes + bytes > maximumResultBytes) {
            throw tooLarge();
        }
    }

    private static long jsonValueBytes(Object value) {
        if (value == null) {
            return 4;
        }
        if (value instanceof String text) {
            return jsonStringBytes(text);
        }
        if (value instanceof TemporalAccessor temporal) {
            return jsonStringBytes(temporal.toString());
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString().getBytes(StandardCharsets.UTF_8).length;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        throw new QueryExecutionFailure(QueryExecutionStatus.FAILED, "QUERY_RESULT_TYPE_FORBIDDEN");
    }

    private static long jsonStringBytes(String value) {
        long bytes = 2;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '"' || current == '\\' || current == '\b' || current == '\f'
                    || current == '\n' || current == '\r' || current == '\t') {
                bytes += 2;
            } else if (current < 0x20) {
                bytes += 6;
            } else {
                int codePoint = value.codePointAt(index);
                bytes += new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    index++;
                }
            }
        }
        return bytes;
    }

    private static QueryExecutionFailure tooLarge() {
        return new QueryExecutionFailure(QueryExecutionStatus.FAILED, "QUERY_RESULT_TOO_LARGE");
    }
}
