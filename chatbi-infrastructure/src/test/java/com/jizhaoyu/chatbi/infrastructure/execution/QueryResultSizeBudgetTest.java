package com.jizhaoyu.chatbi.infrastructure.execution;

import com.jizhaoyu.chatbi.application.execution.QueryResultColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryResultSizeBudgetTest {
    @Test
    void stopsAtCompleteRowWhenResultBudgetIsExhausted() {
        QueryResultSizeBudget budget = new QueryResultSizeBudget(1100, 100);
        budget.addColumns(List.of(new QueryResultColumn("value", "VARCHAR")));

        assertThat(budget.tryAddRow(List.of("first-row"))).isTrue();
        assertThat(budget.tryAddRow(List.of("x".repeat(80)))).isFalse();
    }

    @Test
    void rejectsSingleCellBeyondEscapedUtf8Budget() {
        QueryResultSizeBudget budget = new QueryResultSizeBudget(4096, 12);
        budget.addColumns(List.of(new QueryResultColumn("value", "VARCHAR")));

        assertThatThrownBy(() -> budget.tryAddRow(List.of("\n".repeat(6))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("QUERY_RESULT_TOO_LARGE");
    }

    @Test
    void countsMultibyteAndEmojiContentAsUtf8() {
        QueryResultSizeBudget acceptingBudget = new QueryResultSizeBudget(4096, 12);
        acceptingBudget.addColumns(List.of(new QueryResultColumn("value", "VARCHAR")));
        QueryResultSizeBudget rejectingBudget = new QueryResultSizeBudget(4096, 11);
        rejectingBudget.addColumns(List.of(new QueryResultColumn("value", "VARCHAR")));

        assertThat(acceptingBudget.tryAddRow(List.of("中文😀"))).isTrue();
        assertThatThrownBy(() -> rejectingBudget.tryAddRow(List.of("中文😀")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("QUERY_RESULT_TOO_LARGE");
    }
}
